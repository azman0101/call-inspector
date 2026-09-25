package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.ArcepLookupResult
import com.example.data.model.ArcepNumberRange
import com.example.data.model.ArcepOperator
import com.example.data.model.PhoneNumberType
import com.example.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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

    private val dbName = "arcep_data_secure.db"
    private val legacyDbName = "arcep_data.db"
    private val secureDatabase: SecureArcepDatabase

    init {
        System.loadLibrary("sqlcipher")
        val passphrase = KeyStoreHelper(context).getDatabasePassphrase()
        secureDatabase = Room.databaseBuilder(context, SecureArcepDatabase::class.java, dbName)
            .openHelperFactory(SupportOpenHelperFactory(passphrase))
            .build()
        try {
            initializeEncryptedDatabase()
        } finally {
            passphrase.fill(0)
        }
    }

    private fun getReadableDb(): SupportSQLiteDatabase = secureDatabase.openHelper.writableDatabase

    private fun initializeEncryptedDatabase() {
        val target = getReadableDb()
        if (target.query("SELECT COUNT(*) FROM number_ranges", emptyArray()).use { it.moveToFirst() && it.getLong(0) == 0L }) {
            val seedFile = File(context.cacheDir, "arcep-public-seed.db")
            try {
                context.assets.open("arcep_data.db").use { input ->
                    FileOutputStream(seedFile).use { output -> input.copyTo(output) }
                }
                val seed = SQLiteDatabase.openDatabase(seedFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                try {
                    target.beginTransaction()
                    try {
                        copyPublicTables(seed, target)
                        target.setTransactionSuccessful()
                    } finally {
                        target.endTransaction()
                    }
                } finally {
                    seed.close()
                }
            } finally {
                seedFile.delete()
            }
        }

        val legacyFile = context.getDatabasePath(legacyDbName)
        if (legacyFile.exists()) {
            val legacy = SQLiteDatabase.openDatabase(legacyFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                if (tableExists(legacy, "call_notes")) {
                    target.beginTransaction()
                    try {
                        legacy.rawQuery(
                            "SELECT phone_number, is_favorite, is_spam, user_tag, user_note, updated_at FROM call_notes",
                            null
                        ).use { cursor ->
                            while (cursor.moveToNext()) {
                                secureDatabase.callNoteDao().save(
                                    CallNoteEntity(
                                        phoneNumber = cursor.getString(0),
                                        isFavorite = cursor.getInt(1) == 1,
                                        isSpam = cursor.getInt(2) == 1,
                                        userTag = cursor.getString(3),
                                        userNote = cursor.getString(4),
                                        updatedAt = if (cursor.isNull(5)) null else cursor.getLong(5)
                                    )
                                )
                            }
                        }
                        target.setTransactionSuccessful()
                        // Scrub legacy plaintext rows before removing the pre-encryption database.
                        legacy.execSQL("PRAGMA secure_delete = ON")
                        legacy.beginTransaction()
                        try {
                            legacy.execSQL("DELETE FROM call_notes")
                            legacy.setTransactionSuccessful()
                        } finally {
                            legacy.endTransaction()
                        }
                        legacy.execSQL("PRAGMA wal_checkpoint(TRUNCATE)")
                        legacy.execSQL("VACUUM")
                    } finally {
                        target.endTransaction()
                    }
                }
            } finally {
                legacy.close()
            }
            deleteDatabaseFiles(legacyFile)
        }
    }

    private fun tableExists(database: SQLiteDatabase, name: String): Boolean = database.rawQuery(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=? LIMIT 1", arrayOf(name)
    ).use { it.moveToFirst() }

    private fun copyPublicTables(source: SQLiteDatabase, target: SupportSQLiteDatabase) {
        listOf("operators", "number_ranges", "arcep_metadata").forEach { table ->
            if (!tableExists(source, table)) return@forEach
            source.rawQuery("SELECT * FROM $table", null).use { cursor ->
                val columns = cursor.columnNames
                val placeholders = columns.joinToString(",") { "?" }
                val insert = "INSERT OR REPLACE INTO $table (${columns.joinToString(",")}) VALUES ($placeholders)"
                while (cursor.moveToNext()) {
                    val values = Array<Any?>(columns.size) { index ->
                        if (cursor.isNull(index)) null else when (cursor.getType(index)) {
                            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
                            android.database.Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
                            android.database.Cursor.FIELD_TYPE_BLOB -> cursor.getBlob(index)
                            else -> cursor.getString(index)
                        }
                    }
                    target.execSQL(insert, values)
                }
            }
        }
    }

    private fun deleteDatabaseFiles(databaseFile: File) {
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
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
            val cursor = database.query(
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
                    val cursor = database.query(
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
            val cursorOp = database.query(
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
        val normalized = PhoneNumberFormatter.normalize(phoneNumber)
        secureDatabase.callNoteDao().find(normalized)?.let {
            CallNote(it.phoneNumber, it.isFavorite, it.isSpam, it.userTag, it.userNote)
        }
    }

    suspend fun saveCallNote(
        phoneNumber: String,
        isFavorite: Boolean,
        isSpam: Boolean,
        userTag: String?,
        userNote: String?
    ) = withContext(Dispatchers.IO) {
        val normalized = PhoneNumberFormatter.normalize(phoneNumber)
        secureDatabase.callNoteDao().save(
            CallNoteEntity(normalized, isFavorite, isSpam, userTag, userNote, System.currentTimeMillis())
        )
    }

    suspend fun searchPrefixesOrOperators(query: String): List<ArcepLookupResult> = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return@withContext emptyList()

        val database = getReadableDb()
        val results = mutableListOf<ArcepLookupResult>()

        val cursor = database.query(
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

        database.query("SELECT COUNT(*) FROM number_ranges", emptyArray()).use {
            if (it.moveToFirst()) totalRanges = it.getInt(0)
        }

        database.query("SELECT COUNT(*) FROM operators", emptyArray()).use {
            if (it.moveToFirst()) totalOperators = it.getInt(0)
        }

        database.query(
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
            database.query("SELECT key, value FROM arcep_metadata", emptyArray()).use { cursor ->
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

    /** Import ARCEP data in one encrypted transaction; user notes stay in place. */
    @Synchronized
    fun replaceDatabaseFile(newDbFile: File) {
        val source = SQLiteDatabase.openDatabase(newDbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        try {
            val target = getReadableDb()
            target.beginTransaction()
            try {
                target.delete("number_ranges", null, null)
                target.delete("operators", null, null)
                target.delete("arcep_metadata", null, null)
                copyPublicTables(source, target)
                target.setTransactionSuccessful()
            } finally {
                target.endTransaction()
            }
        } finally {
            source.close()
            newDbFile.delete()
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
