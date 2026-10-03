package net.slashetc.callinspector.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepNumberRange
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

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

class ArcepDatabaseManager private constructor(private val context: Context) : LegacyCallNotes {

    private val dbName = "arcep_data.db"
    private var db: SQLiteDatabase? = null

    private fun ensureDatabaseCopied() {
        val dbFile = context.getDatabasePath(dbName)
        if (!dbFile.exists() || dbFile.length() < 100_000) {
            dbFile.parentFile?.mkdirs()
            val tempFile = File(dbFile.parentFile, "$dbName.tmp")
            context.assets.open(dbName).use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    val buffer = ByteArray(65536)
                    var length: Int
                    while (inputStream.read(buffer).also { length = it } > 0) {
                        outputStream.write(buffer, 0, length)
                    }
                    outputStream.flush()
                }
            }
            if (tempFile.exists() && tempFile.length() > 100_000) {
                if (dbFile.exists()) dbFile.delete()
                tempFile.renameTo(dbFile)
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
        }
        return db!!
    }

    suspend fun lookupNumber(rawNumber: String): ArcepLookupResult =
        lookupNumbers(listOf(rawNumber))[rawNumber] ?: ArcepLookupResult(
            queryNumber = rawNumber,
            normalizedNumber = PhoneNumberFormatter.normalize(rawNumber),
            formattedNumber = PhoneNumberFormatter.format(rawNumber),
            operator = null,
            range = null,
            numberType = PhoneNumberType.classify(PhoneNumberFormatter.normalize(rawNumber)),
            isFound = false
        )

    suspend fun lookupNumbers(rawNumbers: Collection<String>): Map<String, ArcepLookupResult> = withContext(Dispatchers.IO) {
        if (rawNumbers.isEmpty()) return@withContext emptyMap()

        val uniqueNumbers = rawNumbers.distinct()
        val parsedMap = uniqueNumbers.associateWith { rawNum ->
            val normalized = PhoneNumberFormatter.normalize(rawNum)
            val formatted = PhoneNumberFormatter.format(rawNum)
            val phoneType = PhoneNumberType.classify(normalized)
            Triple(normalized, formatted, phoneType)
        }

        val database = getReadableDb()
        val rangeMap = mutableMapOf<String, ArcepNumberRange>()

        // 1. Batched Direct Range Lookup
        val directCandidates = uniqueNumbers.mapNotNull { rawNum ->
            val (normalized, _, _) = parsedMap[rawNum]!!
            if (normalized.isNotBlank() && normalized.length >= 4) normalized else null
        }.distinct()

        val directRangeByNormalized = mutableMapOf<String, ArcepNumberRange>()
        if (directCandidates.isNotEmpty()) {
            for (chunk in directCandidates.chunked(200)) {
                val placeholders = chunk.joinToString(",") { "(?)" }
                val cursor = database.rawQuery(
                    """
                    WITH inputs(num) AS (
                        VALUES $placeholders
                    )
                    SELECT inputs.num, r.id, r.ezabpqm, r.tranche_debut, r.tranche_fin, r.operator_code, r.operator_name, r.territory, r.attribution_date
                    FROM inputs
                    JOIN number_ranges r ON r.id = (
                        SELECT id FROM number_ranges WHERE tranche_debut <= inputs.num
                        ORDER BY tranche_debut DESC LIMIT 1
                    )
                    WHERE r.tranche_fin >= inputs.num
                    """.trimIndent(),
                    chunk.toTypedArray()
                )
                cursor.use {
                    while (it.moveToNext()) {
                        val num = it.getString(0)
                        val range = ArcepNumberRange(
                            id = it.getLong(1),
                            ezabpqm = it.getString(2),
                            trancheDebut = it.getString(3),
                            trancheFin = it.getString(4),
                            operatorCode = it.getString(5),
                            operatorName = it.getString(6),
                            territory = it.getString(7),
                            attributionDate = it.getString(8)
                        )
                        directRangeByNormalized[num] = range
                    }
                }
            }
        }

        for (rawNum in uniqueNumbers) {
            val (normalized, _, _) = parsedMap[rawNum]!!
            val range = directRangeByNormalized[normalized]
            if (range != null) {
                rangeMap[rawNum] = range
            }
        }

        // 2. Batched Prefix Fallback for unmatched numbers
        val unmatchedRawNumbers = uniqueNumbers.filter { rawNum ->
            val (normalized, _, _) = parsedMap[rawNum]!!
            normalized.isNotBlank() && rangeMap[rawNum] == null
        }

        if (unmatchedRawNumbers.isNotEmpty()) {
            val prefixLens = listOf(7, 6, 5, 4, 3, 2)
            val candidatePrefixes = unmatchedRawNumbers.flatMap { rawNum ->
                val (normalized, _, _) = parsedMap[rawNum]!!
                prefixLens.mapNotNull { len ->
                    if (normalized.length >= len) normalized.substring(0, len) else null
                }
            }.distinct()

            if (candidatePrefixes.isNotEmpty()) {
                val prefixToRangeMap = mutableMapOf<String, ArcepNumberRange>()
                for (chunk in candidatePrefixes.chunked(200)) {
                    val placeholders = chunk.joinToString(",") { "?" }
                    val cursor = database.rawQuery(
                        """
                        SELECT r.id, r.ezabpqm, r.tranche_debut, r.tranche_fin, r.operator_code, r.operator_name, r.territory, r.attribution_date
                        FROM number_ranges r
                        WHERE r.ezabpqm IN ($placeholders)
                        """.trimIndent(),
                        chunk.toTypedArray()
                    )
                    cursor.use {
                        while (it.moveToNext()) {
                            val ezabpqm = it.getString(1)
                            if (!prefixToRangeMap.containsKey(ezabpqm)) {
                                prefixToRangeMap[ezabpqm] = ArcepNumberRange(
                                    id = it.getLong(0),
                                    ezabpqm = ezabpqm,
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
                }

                for (rawNum in unmatchedRawNumbers) {
                    val (normalized, _, _) = parsedMap[rawNum]!!
                    for (len in prefixLens) {
                        if (normalized.length >= len) {
                            val prefix = normalized.substring(0, len)
                            val range = prefixToRangeMap[prefix]
                            if (range != null) {
                                rangeMap[rawNum] = range
                                break
                            }
                        }
                    }
                }
            }
        }

        val operatorCodes = rangeMap.values.map { it.operatorCode }.filter { !it.isNullOrBlank() }.distinct()
        val operatorMap = mutableMapOf<String, ArcepOperator>()

        if (operatorCodes.isNotEmpty()) {
            val placeholders = operatorCodes.joinToString(",") { "?" }
            val cursorOp = database.rawQuery(
                """
                SELECT code, name, siret, rcs, address, declaration_date
                FROM operators
                WHERE code IN ($placeholders)
                """.trimIndent(),
                operatorCodes.toTypedArray()
            )
            cursorOp.use {
                while (it.moveToNext()) {
                    val code = it.getString(0)
                    operatorMap[code] = ArcepOperator(
                        code = code,
                        name = it.getString(1),
                        siret = it.getString(2),
                        rcs = it.getString(3),
                        address = it.getString(4),
                        declarationDate = it.getString(5)
                    )
                }
            }
        }

        val resultMap = mutableMapOf<String, ArcepLookupResult>()
        for (rawNum in uniqueNumbers) {
            val (normalized, formatted, phoneType) = parsedMap[rawNum]!!
            if (normalized.isBlank()) {
                resultMap[rawNum] = ArcepLookupResult(
                    queryNumber = rawNum,
                    normalizedNumber = "",
                    formattedNumber = "Numéro masqué",
                    operator = null,
                    range = null,
                    numberType = PhoneNumberType.INCONNU,
                    isFound = false
                )
                continue
            }

            val range = rangeMap[rawNum]
            var operator: ArcepOperator? = null
            val opCode = range?.operatorCode
            if (!opCode.isNullOrBlank()) {
                operator = operatorMap[opCode]
            }
            if (operator == null && range != null) {
                operator = ArcepOperator(
                    code = range.operatorCode,
                    name = range.operatorName
                )
            }

            resultMap[rawNum] = ArcepLookupResult(
                queryNumber = rawNum,
                normalizedNumber = normalized,
                formattedNumber = formatted,
                operator = operator,
                range = range,
                numberType = phoneType,
                isFound = (range != null)
            )
        }

        resultMap
    }

    suspend fun searchPrefixesOrOperators(query: String): List<ArcepLookupResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        // Sanitize LIKE wildcard characters ('\', '%', '_') to prevent wildcard injection
        val sanitizedQuery = cleanQuery
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")

        val database = getReadableDb()
        val results = mutableListOf<ArcepLookupResult>()

        val cursor = database.rawQuery(
            """
            SELECT r.id, r.ezabpqm, r.tranche_debut, r.tranche_fin, r.operator_code, r.operator_name, r.territory, r.attribution_date,
                   o.siret, o.rcs, o.address, o.declaration_date
            FROM number_ranges r
            LEFT JOIN operators o ON r.operator_code = o.code
            WHERE r.ezabpqm LIKE ? ESCAPE '\' OR r.operator_name LIKE ? ESCAPE '\' OR r.operator_code LIKE ? ESCAPE '\'
            ORDER BY r.ezabpqm ASC
            LIMIT 40
            """.trimIndent(),
            arrayOf("$sanitizedQuery%", "%$sanitizedQuery%", "$sanitizedQuery%")
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

    /** The `arcep_metadata` table (empty for a database built before it existed). */
    @Synchronized
    fun metadata(): Map<String, String> = try {
        getReadableDb().rawQuery("SELECT key, value FROM arcep_metadata", null).use { cursor ->
            buildMap { while (cursor.moveToNext()) put(cursor.getString(0), cursor.getString(1)) }
        }
    } catch (e: Exception) {
        emptyMap()
    }

    /** Notes kept in cleartext here before [CallNotesStore]; they are moved to it on first use. */
    @Synchronized
    override fun read(): List<CallNote> = readLegacyCallNotes(getReadableDb())

    @Synchronized
    override fun erase() = eraseLegacyCallNotes(getReadableDb())

    /**
     * Remplacement atomique de la base active par un nouveau fichier SQLite compilé
     */
    @Synchronized
    fun replaceDatabaseFile(newDbFile: File) {
        try {
            val activeDbFile = context.getDatabasePath(dbName)
            // Stage next to the active file so the swap is a same-directory rename: the active
            // database is never missing, even if the copy fails (e.g. disk full).
            val stagedFile = File(activeDbFile.parentFile, "$dbName.new")
            newDbFile.copyTo(stagedFile, overwrite = true)
            if (db != null && db!!.isOpen) {
                db!!.close()
                db = null
            }
            // Leftover journal/WAL files of the old database must not be replayed onto the new one.
            listOf("-journal", "-wal", "-shm").forEach { File(activeDbFile.path + it).delete() }
            if (!stagedFile.renameTo(activeDbFile)) {
                stagedFile.delete()
                throw IOException("Impossible de remplacer la base ARCEP active")
            }
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

private fun hasCallNotesTable(database: SQLiteDatabase): Boolean =
    database.rawQuery("SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'call_notes'", null)
        .use { it.moveToFirst() }

internal fun readLegacyCallNotes(database: SQLiteDatabase): List<CallNote> {
    if (!hasCallNotesTable(database)) return emptyList()
    return database.rawQuery(
        "SELECT phone_number, is_favorite, is_spam, user_tag, user_note, updated_at FROM call_notes", null
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    CallNote(
                        phoneNumber = cursor.getString(0),
                        isFavorite = cursor.getInt(1) == 1,
                        isSpam = cursor.getInt(2) == 1,
                        userTag = if (cursor.isNull(3)) null else cursor.getString(3),
                        userNote = if (cursor.isNull(4)) null else cursor.getString(4),
                        updatedAt = if (cursor.isNull(5)) null else cursor.getLong(5),
                    )
                )
            }
        }
    }
}

/** Drops the cleartext notes table and rewrites the file (VACUUM) so no free page keeps the old notes. */
internal fun eraseLegacyCallNotes(database: SQLiteDatabase) {
    if (!hasCallNotesTable(database)) return
    database.execSQL("DROP TABLE call_notes")
    database.execSQL("VACUUM")
}
