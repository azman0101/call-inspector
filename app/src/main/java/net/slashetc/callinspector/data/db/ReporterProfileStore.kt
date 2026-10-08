package net.slashetc.callinspector.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The user's own details, as SignalConso asks for them in step 4 ("Vos coordonnées") and J'alerte l'Arcep in
 * its last step. [shareContact] answers SignalConso's "Souhaitez-vous partager vos coordonnées avec
 * l'entreprise ?"; null leaves it unanswered. [postalCode] and [city] locate the user for J'alerte l'Arcep,
 * which requires a commune: the city picks it when several share the postal code.
 */
data class ReporterProfile(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val referenceNumber: String = "",
    val shareContact: Boolean? = null,
    val postalCode: String = "",
    val city: String = "",
) {
    fun isEmpty() = !hasSignalConsoContact() && postalCode.isBlank() && city.isBlank()

    /** Something to prefill in SignalConso's step 4: the postal code and city alone are only J'alerte l'Arcep's. */
    fun hasSignalConsoContact() = firstName.isNotBlank() || lastName.isNotBlank() || email.isNotBlank() ||
        phone.isNotBlank() || referenceNumber.isNotBlank() || shareContact != null
}

/** SignalConso reports (or J'alerte l'Arcep alerts) the user sent for one number. */
data class ReportStats(val count: Int, val lastReportedAt: Long)

/**
 * Keeps [ReporterProfile], the SignalConso reports sent from the app and the numbers of the user's lines, in its own app-private database, separate from arcep_data.db (which is replaced
 * on every ARCEP update), encrypted with SQLCipher under a passphrase wrapped by an Android Keystore key
 * ([DatabaseKeyStore]). It is excluded from backups and device transfers (data_extraction_rules.xml):
 * it only ever leaves the phone when the user submits a SignalConso report or copies a call export.
 */
class ReporterProfileStore internal constructor(
    context: Context,
    private val openDatabase: (Context, SupportSQLiteOpenHelper.Callback) -> SupportSQLiteDatabase,
) {
    private val appContext = context.applicationContext

    private val database: SupportSQLiteDatabase by lazy {
        // Unreleased builds kept the profile unencrypted under this name: never leave it behind.
        appContext.deleteDatabase(LEGACY_PLAINTEXT_DB_NAME)
        openDatabase(appContext, Schema)
    }

    internal object Schema : SupportSQLiteOpenHelper.Callback(6) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE reporter_profile (
                    id INTEGER PRIMARY KEY CHECK (id = 1),
                    first_name TEXT NOT NULL,
                    last_name TEXT NOT NULL,
                    email TEXT NOT NULL,
                    phone TEXT NOT NULL
                )
                """.trimIndent()
            )
            onUpgrade(db, 1, version)
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE reporter_profile ADD COLUMN reference_number TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reporter_profile ADD COLUMN share_contact INTEGER")
            }
            if (oldVersion < 3) {
                db.execSQL(
                    """
                    CREATE TABLE signalconso_reports (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        phone_number TEXT NOT NULL,
                        reported_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX signalconso_reports_phone ON signalconso_reports (phone_number)")
            }
            if (oldVersion < 4) {
                // The number of each of the user's lines (SIM...), which the call log only identifies.
                db.execSQL(
                    """
                    CREATE TABLE phone_lines (
                        line_id TEXT PRIMARY KEY,
                        phone_number TEXT NOT NULL,
                        updated_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
            if (oldVersion < 5) {
                // Where the user lives, for J'alerte l'Arcep's commune.
                db.execSQL("ALTER TABLE reporter_profile ADD COLUMN postal_code TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reporter_profile ADD COLUMN city TEXT NOT NULL DEFAULT ''")
            }
            if (oldVersion < 6) {
                // Alerts sent on J'alerte l'Arcep, one row per number an alert covered.
                db.execSQL(
                    """
                    CREATE TABLE arcep_alerts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        phone_number TEXT NOT NULL,
                        sent_at INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX arcep_alerts_phone ON arcep_alerts (phone_number)")
            }
        }
    }

    suspend fun load(): ReporterProfile? = withContext(Dispatchers.IO) {
        database.query(
            "SELECT first_name, last_name, email, phone, reference_number, share_contact, postal_code, city " +
                "FROM reporter_profile WHERE id = 1",
            emptyArray()
        ).use {
            if (!it.moveToFirst()) return@use null
            ReporterProfile(
                firstName = it.getString(0),
                lastName = it.getString(1),
                email = it.getString(2),
                phone = it.getString(3),
                referenceNumber = it.getString(4),
                shareContact = if (it.isNull(5)) null else it.getInt(5) != 0,
                postalCode = it.getString(6),
                city = it.getString(7),
            )
        }
    }

    suspend fun save(profile: ReporterProfile) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", 1)
            put("first_name", profile.firstName.trim())
            put("last_name", profile.lastName.trim())
            put("email", profile.email.trim())
            put("phone", profile.phone.trim())
            put("reference_number", profile.referenceNumber.trim())
            if (profile.shareContact == null) putNull("share_contact") else put("share_contact", if (profile.shareContact) 1 else 0)
            put("postal_code", profile.postalCode.trim())
            put("city", profile.city.trim())
        }
        database.insert("reporter_profile", SQLiteDatabase.CONFLICT_REPLACE, values)
        Unit
    }

    /** Clears the profile only: the reports sent stay counted. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        database.delete("reporter_profile", null, null)
        Unit
    }

    /** Records a report the user sent on SignalConso for [phoneNumber] (normalized). */
    suspend fun recordReport(phoneNumber: String, reportedAt: Long) = withContext(Dispatchers.IO) {
        database.insert(
            "signalconso_reports",
            SQLiteDatabase.CONFLICT_ABORT,
            ContentValues().apply {
                put("phone_number", phoneNumber)
                put("reported_at", reportedAt)
            }
        )
        Unit
    }

    /** Reports sent, per normalized phone number. */
    suspend fun reportStats(): Map<String, ReportStats> = withContext(Dispatchers.IO) {
        database.query(
            "SELECT phone_number, COUNT(*), MAX(reported_at) FROM signalconso_reports GROUP BY phone_number",
            emptyArray()
        ).use {
            buildMap {
                while (it.moveToNext()) put(it.getString(0), ReportStats(it.getInt(1), it.getLong(2)))
            }
        }
    }

    /** Records an alert the user sent on J'alerte l'Arcep, for each (normalized) number it covered. */
    suspend fun recordArcepAlert(phoneNumbers: Collection<String>, sentAt: Long) = withContext(Dispatchers.IO) {
        database.beginTransaction()
        try {
            phoneNumbers.filter { it.isNotBlank() }.distinct().forEach { number ->
                database.insert(
                    "arcep_alerts",
                    SQLiteDatabase.CONFLICT_ABORT,
                    ContentValues().apply {
                        put("phone_number", number)
                        put("sent_at", sentAt)
                    }
                )
            }
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    /** Alerts sent on J'alerte l'Arcep, per normalized phone number. */
    suspend fun arcepAlertStats(): Map<String, ReportStats> = withContext(Dispatchers.IO) {
        database.query(
            "SELECT phone_number, COUNT(*), MAX(sent_at) FROM arcep_alerts GROUP BY phone_number",
            emptyArray()
        ).use {
            buildMap {
                while (it.moveToNext()) put(it.getString(0), ReportStats(it.getInt(1), it.getLong(2)))
            }
        }
    }

    /** The numbers the user gave for their lines, by line id (see CallLogEntry.lineId). */
    suspend fun lineNumbers(): Map<String, String> = withContext(Dispatchers.IO) {
        database.query("SELECT line_id, phone_number FROM phone_lines", emptyArray()).use {
            buildMap { while (it.moveToNext()) put(it.getString(0), it.getString(1)) }
        }
    }

    suspend fun saveLineNumber(lineId: String, phoneNumber: String) = withContext(Dispatchers.IO) {
        database.insert(
            "phone_lines",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("line_id", lineId)
                put("phone_number", phoneNumber.trim())
                put("updated_at", System.currentTimeMillis())
            }
        )
        Unit
    }

    internal suspend fun clearLineNumbers() = withContext(Dispatchers.IO) {
        database.delete("phone_lines", null, null)
        Unit
    }

    internal suspend fun clearReports() = withContext(Dispatchers.IO) {
        database.delete("signalconso_reports", null, null)
        database.delete("arcep_alerts", null, null)
        Unit
    }

    companion object {
        const val DB_NAME = "reporter_profile_secure.db"
        private const val LEGACY_PLAINTEXT_DB_NAME = "reporter_profile.db"
        private const val KEY_ALIAS = "reporter_profile_wrapping_key_v1"
        private const val WRAPPED_PASSPHRASE_FILE = "reporter-profile-db-key.bin"

        @Volatile
        private var INSTANCE: ReporterProfileStore? = null

        fun getInstance(context: Context): ReporterProfileStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReporterProfileStore(context.applicationContext, ::openEncrypted).also { INSTANCE = it }
            }

        internal fun open(
            context: Context,
            factory: SupportSQLiteOpenHelper.Factory,
            callback: SupportSQLiteOpenHelper.Callback,
        ): SupportSQLiteDatabase = EncryptedDatabases.open(context, factory, DB_NAME, callback)

        private fun openEncrypted(context: Context, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteDatabase =
            EncryptedDatabases.openEncrypted(context, DB_NAME, KEY_ALIAS, WRAPPED_PASSPHRASE_FILE, callback)
    }
}
