package net.slashetc.callinspector.data.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.slashetc.callinspector.util.PhoneNumberFormatter

data class CallNote(
    val phoneNumber: String,
    val isFavorite: Boolean,
    val isSpam: Boolean,
    val userTag: String?,
    val userNote: String?,
    val updatedAt: Long? = null,
)

/** Where call notes lived before [CallNotesStore]: the call_notes table of the unencrypted arcep_data.db. */
interface LegacyCallNotes {
    fun read(): List<CallNote>

    /** Deletes the plaintext notes once they are safely stored encrypted. */
    fun erase()
}

/**
 * The user's notes, favorites and spam flags per number (security audit M2), in their own SQLCipher
 * database under an Android Keystore key, apart from arcep_data.db, which only holds public ARCEP data
 * and is replaced on every update. Excluded from backups and device transfers
 * (data_extraction_rules.xml): its key never leaves the phone, so a copy could not be read anyway.
 */
class CallNotesStore internal constructor(
    context: Context,
    private val openDatabase: (Context, SupportSQLiteOpenHelper.Callback) -> SupportSQLiteDatabase,
    private val legacyNotes: LegacyCallNotes?,
) {
    private val appContext = context.applicationContext

    private val database: SupportSQLiteDatabase by lazy {
        openDatabase(appContext, Schema).also { migrateLegacyNotes(it) }
    }

    internal object Schema : SupportSQLiteOpenHelper.Callback(1) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE call_notes (
                    phone_number TEXT PRIMARY KEY,
                    is_favorite INTEGER NOT NULL DEFAULT 0,
                    is_spam INTEGER NOT NULL DEFAULT 0,
                    user_tag TEXT,
                    user_note TEXT,
                    updated_at INTEGER
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    // Copies the plaintext notes in one transaction, then erases them. Interrupted before the erase, the
    // next start copies the same rows again (INSERT OR REPLACE), so no note is lost or left behind.
    private fun migrateLegacyNotes(db: SupportSQLiteDatabase) {
        val legacy = legacyNotes ?: return
        val notes = legacy.read()
        if (notes.isEmpty()) return
        db.beginTransaction()
        try {
            notes.forEach { insert(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        legacy.erase()
    }

    private fun insert(db: SupportSQLiteDatabase, note: CallNote) {
        db.insert(
            "call_notes",
            SQLiteDatabase.CONFLICT_REPLACE,
            ContentValues().apply {
                put("phone_number", note.phoneNumber)
                put("is_favorite", if (note.isFavorite) 1 else 0)
                put("is_spam", if (note.isSpam) 1 else 0)
                put("user_tag", note.userTag)
                put("user_note", note.userNote)
                put("updated_at", note.updatedAt ?: System.currentTimeMillis())
            }
        )
    }

    suspend fun get(phoneNumber: String): CallNote? = withContext(Dispatchers.IO) {
        database.query(
            "SELECT phone_number, is_favorite, is_spam, user_tag, user_note, updated_at FROM call_notes WHERE phone_number = ?",
            arrayOf<Any?>(PhoneNumberFormatter.normalize(phoneNumber))
        ).use { if (it.moveToFirst()) it.toCallNote() else null }
    }

    /** Every note, by normalized number: one query for a whole call list. */
    suspend fun all(): Map<String, CallNote> = withContext(Dispatchers.IO) {
        database.query(
            "SELECT phone_number, is_favorite, is_spam, user_tag, user_note, updated_at FROM call_notes",
            emptyArray()
        ).use { cursor ->
            buildMap { while (cursor.moveToNext()) cursor.toCallNote().let { put(it.phoneNumber, it) } }
        }
    }

    suspend fun save(
        phoneNumber: String,
        isFavorite: Boolean,
        isSpam: Boolean,
        userTag: String?,
        userNote: String?,
    ) = withContext(Dispatchers.IO) {
        insert(
            database,
            CallNote(PhoneNumberFormatter.normalize(phoneNumber), isFavorite, isSpam, userTag, userNote, System.currentTimeMillis())
        )
    }

    private fun android.database.Cursor.toCallNote() = CallNote(
        phoneNumber = getString(0),
        isFavorite = getInt(1) == 1,
        isSpam = getInt(2) == 1,
        userTag = if (isNull(3)) null else getString(3),
        userNote = if (isNull(4)) null else getString(4),
        updatedAt = if (isNull(5)) null else getLong(5),
    )

    companion object {
        const val DB_NAME = "user_notes_secure.db"
        private const val KEY_ALIAS = "user_notes_wrapping_key_v1"
        private const val WRAPPED_PASSPHRASE_FILE = "user-notes-db-key.bin"

        @Volatile
        private var INSTANCE: CallNotesStore? = null

        fun getInstance(context: Context): CallNotesStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: CallNotesStore(
                    context.applicationContext,
                    { ctx, callback -> EncryptedDatabases.openEncrypted(ctx, DB_NAME, KEY_ALIAS, WRAPPED_PASSPHRASE_FILE, callback) },
                    ArcepDatabaseManager.getInstance(context)
                ).also { INSTANCE = it }
            }
    }
}
