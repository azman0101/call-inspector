package net.slashetc.callinspector.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
 * on every ARCEP update). It is excluded from backups and device transfers (data_extraction_rules.xml):
 * it only ever leaves the phone when the user submits a SignalConso report.
 */
class ReporterProfileStore private constructor(context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
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

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    suspend fun load(): ReporterProfile? = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "reporter_profile", arrayOf("first_name", "last_name", "email", "phone"),
            "id = 1", null, null, null, null
        ).use {
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
        writableDatabase.insertWithOnConflict("reporter_profile", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        writableDatabase.delete("reporter_profile", null, null)
        Unit
    }

    companion object {
        const val DB_NAME = "reporter_profile.db"

        @Volatile
        private var INSTANCE: ReporterProfileStore? = null

        fun getInstance(context: Context): ReporterProfileStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ReporterProfileStore(context.applicationContext).also { INSTANCE = it }
            }
    }
}
