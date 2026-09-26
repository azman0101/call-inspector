package net.slashetc.callinspector.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/** The user's own contact details, as SignalConso asks for them in step 4 ("Vos coordonnées"). */
data class ReporterProfile(
    val firstName: String = "",
    val lastName: String = "",
    val email: String = "",
    val phone: String = "",
) {
    fun isEmpty() = firstName.isBlank() && lastName.isBlank() && email.isBlank() && phone.isBlank()
}

/**
 * Keeps [ReporterProfile] in its own app-private database, separate from arcep_data.db (which is replaced
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

    private object Schema : SupportSQLiteOpenHelper.Callback(1) {
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
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    suspend fun load(): ReporterProfile? = withContext(Dispatchers.IO) {
        database.query("SELECT first_name, last_name, email, phone FROM reporter_profile WHERE id = 1", emptyArray()).use {
            if (it.moveToFirst()) ReporterProfile(it.getString(0), it.getString(1), it.getString(2), it.getString(3)) else null
        }
    }

    suspend fun save(profile: ReporterProfile) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", 1)
            put("first_name", profile.firstName.trim())
            put("last_name", profile.lastName.trim())
            put("email", profile.email.trim())
            put("phone", profile.phone.trim())
        }
        database.insert("reporter_profile", SQLiteDatabase.CONFLICT_REPLACE, values)
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        database.delete("reporter_profile", null, null)
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
