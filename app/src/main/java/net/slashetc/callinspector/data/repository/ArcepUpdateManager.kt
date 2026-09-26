package net.slashetc.callinspector.data.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import android.util.Log
import net.slashetc.callinspector.data.db.ArcepDatabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import io.sentry.Sentry
import javax.net.ssl.SSLPeerUnverifiedException
import java.security.cert.CertificateException
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

sealed class UpdateStatus {
    object Idle : UpdateStatus()
    data class Checking(val message: String = "Vérification des mises à jour sur l'extranet ARCEP...") : UpdateStatus()
    data class Downloading(val step: String, val progress: Float) : UpdateStatus()
    data class Processing(val step: String, val progress: Float) : UpdateStatus()
    data class Success(val message: String, val rangesCount: Int, val operatorsCount: Int, val date: String) : UpdateStatus()
    data class Error(val errorMessage: String) : UpdateStatus()
}

class ArcepUpdateManager(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val dbManager = ArcepDatabaseManager.getInstance(context)

    companion object {
        private const val TAG = "ArcepUpdateManager"
        const val MAJNUM_URL = "https://extranet.arcep.fr/uploads/MAJNUM.csv"
        const val CE_URL = "https://extranet.arcep.fr/uploads/identifiants_CE.csv"

        const val MAX_LINE_LENGTH = 4096
        const val MAX_CODE_LENGTH = 20
        const val MAX_NAME_LENGTH = 200
        const val MAX_SIRET_LENGTH = 20
        const val MAX_RCS_LENGTH = 100
        const val MAX_ADDRESS_LENGTH = 500
        const val MAX_DATE_LENGTH = 30
        const val MAX_NUMBER_LENGTH = 20
        const val MAX_TERRITORY_LENGTH = 100

        // Both CSV files are a few MB; anything far larger is not a genuine ARCEP export.
        const val MAX_DOWNLOAD_BYTES = 32 * 1024 * 1024

        // Roughly half of the current volume (~20 600 ranges, ~1 600 operators): a renamed CSV column
        // or a truncated download must not replace a working database with an empty one.
        const val MIN_EXPECTED_RANGES = 10_000
        const val MIN_EXPECTED_OPERATORS = 500

        fun isPlausibleArcepData(rangeCount: Int, operatorCount: Int): Boolean =
            rangeCount >= MIN_EXPECTED_RANGES && operatorCount >= MIN_EXPECTED_OPERATORS

        fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (out.size() + read > maxBytes) throw IOException("Réponse ARCEP trop volumineuse (> $maxBytes octets)")
                out.write(buffer, 0, read)
            }
            return out.toByteArray()
        }

        fun sanitizeToken(token: String?, maxLength: Int): String? {
            if (token == null) return null
            val cleaned = token.replace(Regex("[\\x00-\\x1F\\x7F]"), "").trim().trim('"').trim('\'').trim()
            if (cleaned.isEmpty()) return null
            return cleaned.take(maxLength)
        }

        fun sanitizeNonNullableToken(token: String?, maxLength: Int, defaultIfEmpty: String = ""): String {
            return sanitizeToken(token, maxLength) ?: defaultIfEmpty
        }
    }

    suspend fun checkAndDownloadUpdate(
        onProgress: (UpdateStatus) -> Unit
    ): Boolean = withContext(Dispatchers.IO) {
        val tempDbFile = File(context.cacheDir, "arcep_update_temp.db")
        var openedTempDb: SQLiteDatabase? = null
        try {
            onProgress(UpdateStatus.Checking())

            // 1. Check HEAD on MAJNUM
            val headReq = Request.Builder()
                .url(MAJNUM_URL)
                .head()
                .header("User-Agent", "ArcepOperateur/1.0 (Android)")
                .build()

            val lastModifiedHeader = try {
                client.newCall(headReq).execute().use { response ->
                    response.header("Last-Modified") ?: ""
                }
            } catch (e: Exception) {
                Log.w(TAG, "HEAD request failed, proceeding with direct download: ${e.message}")
                ""
            }

            // 2. Download identifiants_CE.csv
            onProgress(UpdateStatus.Downloading("Téléchargement des identifiants opérateurs...", 0.15f))
            val ceRequest = Request.Builder()
                .url(CE_URL)
                .header("User-Agent", "ArcepOperateur/1.0 (Android)")
                .build()

            val ceBytes = client.newCall(ceRequest).execute().use { ceResponse ->
                if (!ceResponse.isSuccessful) {
                    onProgress(UpdateStatus.Error("Échec du téléchargement des opérateurs : Code HTTP ${ceResponse.code}"))
                    return@withContext false
                }
                ceResponse.body?.byteStream()?.let { readLimited(it, MAX_DOWNLOAD_BYTES) }
            } ?: run {
                onProgress(UpdateStatus.Error("Fichier opérateurs vide"))
                return@withContext false
            }

            // 3. Download MAJNUM.csv
            onProgress(UpdateStatus.Downloading("Téléchargement des ressources de numérotation (MAJNUM)...", 0.45f))
            val majRequest = Request.Builder()
                .url(MAJNUM_URL)
                .header("User-Agent", "ArcepOperateur/1.0 (Android)")
                .build()

            val majBytes = client.newCall(majRequest).execute().use { majResponse ->
                if (!majResponse.isSuccessful) {
                    onProgress(UpdateStatus.Error("Échec du téléchargement de MAJNUM : Code HTTP ${majResponse.code}"))
                    return@withContext false
                }
                majResponse.body?.byteStream()?.let { readLimited(it, MAX_DOWNLOAD_BYTES) }
            } ?: run {
                onProgress(UpdateStatus.Error("Fichier MAJNUM vide"))
                return@withContext false
            }

            // 4. Compile into temporary SQLite database
            onProgress(UpdateStatus.Processing("Compilation et indexation locale de la base...", 0.70f))
            SQLiteDatabase.deleteDatabase(tempDbFile)

            val tempDb = SQLiteDatabase.openOrCreateDatabase(tempDbFile, null)
            openedTempDb = tempDb
            tempDb.execSQL("PRAGMA page_size = 4096;")
            tempDb.execSQL("PRAGMA synchronous = OFF;")

            // Create schema
            tempDb.execSQL("""
                CREATE TABLE operators (
                    code TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    siret TEXT,
                    rcs TEXT,
                    address TEXT,
                    declaration_date TEXT
                );
            """.trimIndent())

            tempDb.execSQL("""
                CREATE TABLE number_ranges (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    ezabpqm TEXT NOT NULL,
                    tranche_debut TEXT NOT NULL,
                    tranche_fin TEXT NOT NULL,
                    operator_code TEXT NOT NULL,
                    operator_name TEXT NOT NULL,
                    territory TEXT,
                    attribution_date TEXT
                );
            """.trimIndent())

            tempDb.execSQL("""
                CREATE TABLE call_notes (
                    phone_number TEXT PRIMARY KEY,
                    is_favorite INTEGER DEFAULT 0,
                    is_spam INTEGER DEFAULT 0,
                    user_tag TEXT,
                    user_note TEXT,
                    updated_at INTEGER
                );
            """.trimIndent())

            tempDb.execSQL("""
                CREATE TABLE arcep_metadata (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                );
            """.trimIndent())

            // Insert operators
            val operatorsDict = mutableMapOf<String, String>()
            val ceReader = BufferedReader(InputStreamReader(ceBytes.inputStream(), Charset.forName("ISO-8859-1")))
            val headerLineCe = ceReader.readLine() ?: ""
            val headersCe = headerLineCe.split(";").map { sanitizeNonNullableToken(it, 100) }
            val codeIdx = headersCe.indexOf("CODE_OPERATEUR")
            val nameIdx = headersCe.indexOf("IDENTITE_OPERATEUR")
            val siretIdx = headersCe.indexOf("SIRET_ACTEUR")
            val rcsIdx = headersCe.indexOf("RCS_ACTEUR")
            val addrIdx = headersCe.indexOf("ADRESSE_COMPLETE_ACTEUR")
            val decDateIdx = headersCe.indexOf("DATE_DECLARATION_OPERATEUR")

            tempDb.beginTransaction()
            var opCount = 0
            val insertOpStmt: SQLiteStatement = tempDb.compileStatement(
                "INSERT OR REPLACE INTO operators VALUES (?, ?, ?, ?, ?, ?);"
            )

            ceReader.forEachLine { line ->
                if (line.length > MAX_LINE_LENGTH) return@forEachLine
                val cols = line.split(";")
                if (codeIdx in cols.indices && nameIdx in cols.indices) {
                    val code = sanitizeNonNullableToken(cols[codeIdx], MAX_CODE_LENGTH)
                    val name = sanitizeNonNullableToken(cols[nameIdx], MAX_NAME_LENGTH)
                    if (code.isNotEmpty()) {
                        val siret = if (siretIdx in cols.indices) sanitizeToken(cols[siretIdx], MAX_SIRET_LENGTH) else null
                        val rcs = if (rcsIdx in cols.indices) sanitizeToken(cols[rcsIdx], MAX_RCS_LENGTH) else null
                        val addr = if (addrIdx in cols.indices) sanitizeToken(cols[addrIdx], MAX_ADDRESS_LENGTH) else null
                        val decDate = if (decDateIdx in cols.indices) sanitizeToken(cols[decDateIdx], MAX_DATE_LENGTH) else null

                        operatorsDict[code] = name
                        insertOpStmt.bindString(1, code)
                        insertOpStmt.bindString(2, name)
                        if (siret != null) insertOpStmt.bindString(3, siret) else insertOpStmt.bindNull(3)
                        if (rcs != null) insertOpStmt.bindString(4, rcs) else insertOpStmt.bindNull(4)
                        if (addr != null) insertOpStmt.bindString(5, addr) else insertOpStmt.bindNull(5)
                        if (decDate != null) insertOpStmt.bindString(6, decDate) else insertOpStmt.bindNull(6)
                        insertOpStmt.executeInsert()
                        opCount++
                    }
                }
            }
            tempDb.setTransactionSuccessful()
            tempDb.endTransaction()
            insertOpStmt.close()

            // Insert ranges from MAJNUM
            onProgress(UpdateStatus.Processing("Insertion des 20 000+ tranches de numérotation...", 0.85f))
            val majReader = BufferedReader(InputStreamReader(majBytes.inputStream(), Charset.forName("ISO-8859-1")))
            val headerLineMaj = majReader.readLine() ?: ""
            val headersMaj = headerLineMaj.split(";").map { sanitizeNonNullableToken(it, 100) }
            val ezIdx = headersMaj.indexOf("EZABPQM")
            val debutIdx = headersMaj.indexOf("Tranche_Debut")
            val finIdx = headersMaj.indexOf("Tranche_Fin")
            var mnemoIdx = headersMaj.indexOf("Mnémo")
            if (mnemoIdx < 0) mnemoIdx = headersMaj.indexOfFirst { it.startsWith("Mn") }
            val terIdx = headersMaj.indexOf("Territoire")
            val attrDateIdx = headersMaj.indexOf("Date_Attribution")

            tempDb.beginTransaction()
            var rangeCount = 0
            var latestAttrDate = ""
            val insertRangeStmt: SQLiteStatement = tempDb.compileStatement(
                "INSERT INTO number_ranges (ezabpqm, tranche_debut, tranche_fin, operator_code, operator_name, territory, attribution_date) VALUES (?, ?, ?, ?, ?, ?, ?);"
            )

            majReader.forEachLine { line ->
                if (line.length > MAX_LINE_LENGTH) return@forEachLine
                val cols = line.split(";")
                if (ezIdx in cols.indices && debutIdx in cols.indices && finIdx in cols.indices) {
                    val ez = sanitizeNonNullableToken(cols[ezIdx], MAX_NUMBER_LENGTH)
                    val debut = sanitizeNonNullableToken(cols[debutIdx], MAX_NUMBER_LENGTH)
                    val fin = sanitizeNonNullableToken(cols[finIdx], MAX_NUMBER_LENGTH)
                    val opCode = if (mnemoIdx in cols.indices) sanitizeNonNullableToken(cols[mnemoIdx], MAX_CODE_LENGTH) else ""
                    val opName = operatorsDict[opCode] ?: sanitizeNonNullableToken(opCode, MAX_NAME_LENGTH)
                    val ter = if (terIdx in cols.indices) sanitizeToken(cols[terIdx], MAX_TERRITORY_LENGTH) else null
                    val attrDate = if (attrDateIdx in cols.indices) sanitizeToken(cols[attrDateIdx], MAX_DATE_LENGTH) else null

                    if (attrDate != null && attrDate > latestAttrDate) {
                        latestAttrDate = attrDate
                    }

                    if (ez.isNotEmpty() && debut.isNotEmpty()) {
                        insertRangeStmt.bindString(1, ez)
                        insertRangeStmt.bindString(2, debut)
                        insertRangeStmt.bindString(3, fin)
                        insertRangeStmt.bindString(4, opCode)
                        insertRangeStmt.bindString(5, opName)
                        if (ter != null) insertRangeStmt.bindString(6, ter) else insertRangeStmt.bindNull(6)
                        if (attrDate != null) insertRangeStmt.bindString(7, attrDate) else insertRangeStmt.bindNull(7)
                        insertRangeStmt.executeInsert()
                        rangeCount++
                    }
                }
            }
            tempDb.setTransactionSuccessful()
            tempDb.endTransaction()
            insertRangeStmt.close()

            if (!isPlausibleArcepData(rangeCount, opCount)) {
                Log.w(TAG, "Rejected ARCEP update: $rangeCount ranges, $opCount operators")
                onProgress(UpdateStatus.Error("Données ARCEP incomplètes ($rangeCount tranches, $opCount opérateurs) : la base actuelle est conservée."))
                return@withContext false
            }

            // 5. Restore user call notes from current database
            dbManager.backupUserNotesTo(tempDb)

            // 6. Write metadata
            val nowStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).format(Date())
            val dateLabel = if (lastModifiedHeader.isNotBlank()) lastModifiedHeader else nowStr

            tempDb.beginTransaction()
            val metaStmt = tempDb.compileStatement("INSERT INTO arcep_metadata VALUES (?, ?);")
            val metaList = listOf(
                Pair("version_date", dateLabel),
                Pair("generated_at", nowStr),
                Pair("ranges_count", rangeCount.toString()),
                Pair("operators_count", opCount.toString()),
                Pair("latest_attribution_date", latestAttrDate),
                Pair("source_majnum_url", MAJNUM_URL),
                Pair("source_ce_url", CE_URL)
            )
            for ((k, v) in metaList) {
                metaStmt.bindString(1, k)
                metaStmt.bindString(2, v)
                metaStmt.executeInsert()
            }
            tempDb.setTransactionSuccessful()
            tempDb.endTransaction()
            metaStmt.close()

            // Create indexes
            onProgress(UpdateStatus.Processing("Finalisation et création des index...", 0.95f))
            tempDb.execSQL("CREATE INDEX idx_tranche ON number_ranges (tranche_debut, tranche_fin);")
            tempDb.execSQL("CREATE INDEX idx_ezabpqm ON number_ranges (ezabpqm);")
            tempDb.execSQL("CREATE INDEX idx_op_code ON number_ranges (operator_code);")
            tempDb.execSQL("VACUUM;")
            tempDb.close()

            // 7. Atomic replace in ArcepDatabaseManager
            dbManager.replaceDatabaseFile(tempDbFile)

            onProgress(
                UpdateStatus.Success(
                    message = "Base ARCEP mise à jour avec succès !",
                    rangesCount = rangeCount,
                    operatorsCount = opCount,
                    date = dateLabel
                )
            )
            true
        } catch (e: Exception) {
            if (e.hasTlsPinningFailure()) {
                Sentry.captureMessage("TLS certificate pinning failed for extranet.arcep.fr")
                Sentry.captureException(e)
            }
            Log.e(TAG, "Erreur lors de la mise à jour ARCEP", e)
            onProgress(UpdateStatus.Error("Erreur : ${e.localizedMessage ?: e.message}"))
            false
        } finally {
            openedTempDb?.takeIf { it.isOpen }?.let { db -> runCatching { db.close() } }
            SQLiteDatabase.deleteDatabase(tempDbFile)
        }
    }
}

// Android's network_security_config pin-set fails with CertificateException("Pin verification failed")
// wrapped in an SSLHandshakeException; SSLPeerUnverifiedException is OkHttp CertificatePinner's failure.
internal fun Throwable.hasTlsPinningFailure(): Boolean {
    var current: Throwable? = this
    val seen = mutableSetOf<Throwable>()
    while (current != null && seen.add(current)) {
        if (current is SSLPeerUnverifiedException) return true
        if (current is CertificateException && current.message?.contains("Pin verification failed") == true) return true
        current = current.cause
    }
    return false
}
