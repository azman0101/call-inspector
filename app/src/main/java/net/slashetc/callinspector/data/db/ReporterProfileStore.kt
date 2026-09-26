package net.slashetc.callinspector.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The user's own details, as SignalConso asks for them in step 4 ("Vos coordonnées").
 * [shareContact] answers "Souhaitez-vous partager vos coordonnées avec l'entreprise ?"; null leaves it unanswered.
 */
data class ReporterProfile(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
    val referenceNumber: String = "",
    val shareContact: Boolean? = null,
) {
    fun isEmpty() = firstName.isBlank() && lastName.isBlank() && email.isBlank() && phone.isBlank() &&
        referenceNumber.isBlank() && shareContact == null
}

/** SignalConso reports the user sent for one number. */
data class ReportStats(val count: Int, val lastReportedAt: Long)

/**
 * Keeps [ReporterProfile], and the SignalConso reports sent from the app, in its own app-private database, separate from arcep_data.db (which is replaced
 * on every ARCEP update), encrypted with SQLCipher under a passphrase wrapped by an Android Keystore key
 * ([DatabaseKeyStore]). It is excluded from backups and device transfers (data_extraction_rules.xml):
 * it only ever leaves the phone when the user submits a SignalConso report.
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

    internal object Schema : SupportSQLiteOpenHelper.Callback(3) {
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
        }
    }

    suspend fun load(): ReporterProfile? = withContext(Dispatchers.IO) {
        database.query(
            "SELECT first_name, last_name, email, phone, reference_number, share_contact " +
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

    internal suspend fun clearReports() = withContext(Dispatchers.IO) {
        database.delete("signalconso_reports", null, null)
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
        ): SupportSQLiteDatabase = factory.create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(DB_NAME).callback(callback).build()
        ).writableDatabase

        private fun openEncrypted(context: Context, callback: SupportSQLiteOpenHelper.Callback): SupportSQLiteDatabase {
            System.loadLibrary("sqlcipher")
            val passphrase = DatabaseKeyStore(context, KEY_ALIAS, WRAPPED_PASSPHRASE_FILE, DB_NAME).getDatabasePassphrase()
            try {
                return open(context, SupportOpenHelperFactory(passphrase), callback)
            } finally {
                passphrase.fill(0)
            }
        }
    }
}
