package com.example.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.data.model.ArcepLookupResult
import com.example.data.model.ArcepNumberRange
import com.example.data.model.ArcepOperator
import com.example.data.model.PhoneNumberType
import com.example.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class DatabaseStats(
    val totalRanges: Int,
    val totalOperators: Int,
    val topOperators: List<Pair<String, Int>>,
    val databaseVersionDate: String = "Officiel ARCEP",
    val generatedAt: String? = null,
    val latestAttributionDate: String? = null,
    val majnumChecksum: String? = null,
    val ceChecksum: String? = null
)

data class CallNote(
    val phoneNumber: String,
    val isFavorite: Boolean,
    val isSpam: Boolean,
    val userTag: String?,
    val userNote: String?
)

class ArcepDatabaseManager private constructor(private val context: Context) {

    private val dbName = "arcep_data.db"
    private var db: SQLiteDatabase? = null

    private fun ensureDatabaseCopied() {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists() || dbFile.length() < 100_000) {
            dbFile.parentFile?.mkdirs()
            context.assets.open(dbName).use { inputStream ->
                FileOutputStream(dbFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                    outputStream.flush()
                }
            }
        }
    }

    @Synchronized
    private fun getReadableDb(): SQLiteDatabase {
        if (db == null || !db!!.isOpen) {
            val dbFile = context.getDatabasePath(dbName)
            if (!dbFile.exists() || dbFile.length() < 100_000) {
                ensureDatabaseCopied()
            }
            db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            // Ensure call_notes table exists
            db?.execSQL(
                """
                CREATE TABLE IF NOT EXISTS call_notes (
                    phone_number TEXT PRIMARY KEY,
                    is_favorite INTEGER DEFAULT 0,
                    is_spam INTEGER DEFAULT 0,
                    user_tag TEXT,
                    user_note TEXT,
                    updated_at INTEGER
                )
                """.trimIndent()
            )
        }
        return db!!
    }

    suspend fun lookupNumber(rawNumber: String): ArcepLookupResult = withContext(Dispatchers.IO) {
        val normalized = PhoneNumberFormatter.normalize(rawNumber)
        val formatted = PhoneNumberFormatter.format(rawNumber)
        val phoneType = PhoneNumberType.classify(normalized)

        if (normalized.isBlank()) {
            return@withContext ArcepLookupResult(
                queryNumber = rawNumber,
                normalizedNumber = "",
                formattedNumber = "Numéro masqué",
                operator = null,
                range = null,
                numberType = PhoneNumberType.INCONNU,
                isFound = false
            )
        }

        val database = getReadableDb()

        // 1. Direct range lookup
        var range: ArcepNumberRange? = null
        if (normalized.length >= 4) {
            val cursor = database.rawQuery(
                """
                SELECT r.id, r.ezabpqm, r.tranche_debut, r.tranche_fin, r.operator_code, r.operator_name, r.territory, r.attribution_date 
                FROM number_ranges r
                WHERE r.tranche_debut <= ? AND r.tranche_fin >= ?
                ORDER BY LENGTH(r.ezabpqm) DESC LIMIT 1
                """.trimIndent(),
                arrayOf(normalized, normalized)
            )

            cursor.use {
                if (it.moveToFirst()) {
                    range = ArcepNumberRange(
                        id = it.getLong(0),
                        ezabpqm = it.getString(1),
                        trancheDebut = it.getString(2),
                        trancheFin = it.getString(3),
                        operatorCode = it.getString(4),
                        operatorName = it.getString(5),
                        territory = it.getString(6),
                        attributionDate = it.getString(7)
                    )
                }
            }
        }

        // 2. Prefix fallback if not matched by full 10-digit range
        if (range == null) {
            for (len in listOf(7, 6, 5, 4, 3, 2)) {
                if (normalized.length >= len) {
                    val prefix = normalized.substring(0, len)
                    val cursor = database.rawQuery(
                        """
                        SELECT r.id, r.ezabpqm, r.tranche_debut, r.tranche_fin, r.operator_code, r.operator_name, r.territory, r.attribution_date 
                        FROM number_ranges r
                        WHERE r.ezabpqm = ? LIMIT 1
                        """.trimIndent(),
                        arrayOf(prefix)
                    )
                    cursor.use {
                        if (it.moveToFirst()) {
                            range = ArcepNumberRange(
                                id = it.getLong(0),
                                ezabpqm = it.getString(1),
                                trancheDebut = it.getString(2),
                                trancheFin = it.getString(3),
                                operatorCode = it.getString(4),
                                operatorName = it.getString(5),
                                territory = it.getString(6),
                                attributionDate = it.getString(7)
                            )
                        }
                    }
                    if (range != null) break
                }
            }
        }

        // 3. Fetch operator legal profile if range found
        var operator: ArcepOperator? = null
        val opCode = range?.operatorCode
        if (!opCode.isNullOrBlank()) {
            val cursorOp = database.rawQuery(
                """
                SELECT code, name, siret, rcs, address, declaration_date
                FROM operators
                WHERE code = ? LIMIT 1
                """.trimIndent(),
                arrayOf(opCode)
            )
            cursorOp.use {
                if (it.moveToFirst()) {
                    operator = ArcepOperator(
                        code = it.getString(0),
                        name = it.getString(1),
                        siret = it.getString(2),
                        rcs = it.getString(3),
                        address = it.getString(4),
                        declarationDate = it.getString(5)
                    )
                }
            }
        }

        // Fallback for operator if not in operator table
        if (operator == null && range != null) {
            operator = ArcepOperator(
                code = range!!.operatorCode,
                name = range!!.operatorName
            )
        }

        return@withContext ArcepLookupResult(
            queryNumber = rawNumber,
            normalizedNumber = normalized,
            formattedNumber = formatted,
            operator = operator,
            range = range,
            numberType = phoneType,
            isFound = (range != null)
        )
    }

    suspend fun getCallNote(phoneNumber: String): CallNote? = withContext(Dispatchers.IO) {
        val database = getReadableDb()
        val normalized = PhoneNumberFormatter.normalize(phoneNumber)
        val cursor = database.rawQuery(
            "SELECT is_favorite, is_spam, user_tag, user_note FROM call_notes WHERE phone_number = ? LIMIT 1",
            arrayOf(normalized)
        )
        cursor.use {
            if (it.moveToFirst()) {
                CallNote(
                    phoneNumber = normalized,
                    isFavorite = it.getInt(0) == 1,
                    isSpam = it.getInt(1) == 1,
                    userTag = it.getString(2),
                    userNote = it.getString(3)
                )
            } else null
        }
    }

    suspend fun saveCallNote(
        phoneNumber: String,
        isFavorite: Boolean,
        isSpam: Boolean,
        userTag: String?,
        userNote: String?
    ) = withContext(Dispatchers.IO) {
        val database = getReadableDb()
        val normalized = PhoneNumberFormatter.normalize(phoneNumber)
        val values = ContentValues().apply {
            put("phone_number", normalized)
            put("is_favorite", if (isFavorite) 1 else 0)
            put("is_spam", if (isSpam) 1 else 0)
            put("user_tag", userTag)
            put("user_note", userNote)
            put("updated_at", System.currentTimeMillis())
        }
        database.insertWithOnConflict("call_notes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun searchPrefixesOrOperators(query: String): List<ArcepLookupResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val database = getReadableDb()
        val results = mutableListOf<ArcepLookupResult>()

        val cursor = database.rawQuery(
            """
            SELECT r.id, r.ezabpqm, r.tranche_debut, r.tranche_fin, r.operator_code, r.operator_name, r.territory, r.attribution_date,
                   o.siret, o.rcs, o.address, o.declaration_date
            FROM number_ranges r
            LEFT JOIN operators o ON r.operator_code = o.code
            WHERE r.ezabpqm LIKE ? OR r.operator_name LIKE ? OR r.operator_code LIKE ?
            ORDER BY r.ezabpqm ASC
            LIMIT 40
            """.trimIndent(),
            arrayOf("$cleanQuery%", "%$cleanQuery%", "$cleanQuery%")
        )

        cursor.use {
            while (it.moveToNext()) {
                val range = ArcepNumberRange(
                    id = it.getLong(0),
                    ezabpqm = it.getString(1),
                    trancheDebut = it.getString(2),
                    trancheFin = it.getString(3),
                    operatorCode = it.getString(4),
                    operatorName = it.getString(5),
                    territory = it.getString(6),
                    attributionDate = it.getString(7)
                )
                val operator = ArcepOperator(
                    code = it.getString(4),
                    name = it.getString(5),
                    siret = it.getString(8),
                    rcs = it.getString(9),
                    address = it.getString(10),
                    declarationDate = it.getString(11)
                )
                val normalizedSample = range.trancheDebut
                val numberType = PhoneNumberType.classify(normalizedSample)

                results.add(
                    ArcepLookupResult(
                        queryNumber = range.ezabpqm,
                        normalizedNumber = normalizedSample,
                        formattedNumber = PhoneNumberFormatter.format(normalizedSample),
                        operator = operator,
                        range = range,
                        numberType = numberType,
                        isFound = true
                    )
                )
            }
        }

        return@withContext results
    }

    suspend fun getStats(): DatabaseStats = withContext(Dispatchers.IO) {
        val database = getReadableDb()
        var totalRanges = 0
        var totalOperators = 0
        val topOperators = mutableListOf<Pair<String, Int>>()

        database.rawQuery("SELECT COUNT(*) FROM number_ranges", null).use {
            if (it.moveToFirst()) totalRanges = it.getInt(0)
        }

        database.rawQuery("SELECT COUNT(*) FROM operators", null).use {
            if (it.moveToFirst()) totalOperators = it.getInt(0)
        }

        database.rawQuery(
            """
            SELECT operator_name, COUNT(*) as cnt 
            FROM number_ranges 
            GROUP BY operator_name 
            ORDER BY cnt DESC 
            LIMIT 7
            """.trimIndent(),
            null
        ).use {
            while (it.moveToNext()) {
                topOperators.add(Pair(it.getString(0), it.getInt(1)))
            }
        }

        // Read dynamic metadata from arcep_metadata table if available
        var versionDate = "Officiel ARCEP (Septembre 2026)"
        var generatedAt: String? = null
        var latestAttributionDate: String? = null
        var majnumChecksum: String? = null
        var ceChecksum: String? = null

        try {
            database.rawQuery("SELECT key, value FROM arcep_metadata", null).use { cursor ->
                val keyIdx = cursor.getColumnIndex("key")
                val valIdx = cursor.getColumnIndex("value")
                while (cursor.moveToNext()) {
                    val key = cursor.getString(keyIdx)
                    val value = cursor.getString(valIdx)
                    when (key) {
                        "version_date" -> versionDate = value
                        "generated_at" -> generatedAt = value
                        "latest_attribution_date" -> latestAttributionDate = value
                        "majnum_sha256" -> majnumChecksum = value
                        "ce_sha256" -> ceChecksum = value
                    }
                }
            }
        } catch (e: Exception) {
            // Table arcep_metadata might not exist on older versions
        }

        return@withContext DatabaseStats(
            totalRanges = totalRanges,
            totalOperators = totalOperators,
            topOperators = topOperators,
            databaseVersionDate = versionDate,
            generatedAt = generatedAt,
            latestAttributionDate = latestAttributionDate,
            majnumChecksum = majnumChecksum,
            ceChecksum = ceChecksum
        )
    }

    /**
     * Copie toutes les notes, drapeaux spam et favoris dans la nouvelle base cible
     */
    fun backupUserNotesTo(targetDb: SQLiteDatabase) {
        val currentDb = getReadableDb()
        try {
            currentDb.rawQuery("SELECT phone_number, is_favorite, is_spam, user_tag, user_note, updated_at FROM call_notes", null).use { cursor ->
                val stmt = targetDb.compileStatement(
                    "INSERT OR REPLACE INTO call_notes (phone_number, is_favorite, is_spam, user_tag, user_note, updated_at) VALUES (?, ?, ?, ?, ?, ?);"
                )
                targetDb.beginTransaction()
                while (cursor.moveToNext()) {
                    stmt.bindString(1, cursor.getString(0))
                    stmt.bindLong(2, cursor.getLong(1))
                    stmt.bindLong(3, cursor.getLong(2))
                    if (cursor.isNull(3)) stmt.bindNull(4) else stmt.bindString(4, cursor.getString(3))
                    if (cursor.isNull(4)) stmt.bindNull(5) else stmt.bindString(5, cursor.getString(4))
                    stmt.bindLong(6, cursor.getLong(5))
                    stmt.executeInsert()
                }
                targetDb.setTransactionSuccessful()
                targetDb.endTransaction()
                stmt.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Remplacement atomique de la base active par un nouveau fichier SQLite compilé
     */
    @Synchronized
    fun replaceDatabaseFile(newDbFile: File) {
        try {
            if (db != null && db!!.isOpen) {
                db!!.close()
                db = null
            }
            val activeDbFile = context.getDatabasePath(dbName)
            if (activeDbFile.exists()) {
                activeDbFile.delete()
            }
            newDbFile.copyTo(activeDbFile, overwrite = true)
            newDbFile.delete()
            // Re-open
            db = SQLiteDatabase.openDatabase(activeDbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: ArcepDatabaseManager? = null

        fun getInstance(context: Context): ArcepDatabaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ArcepDatabaseManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
