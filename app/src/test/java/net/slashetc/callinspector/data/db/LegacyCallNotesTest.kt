package net.slashetc.callinspector.data.db

import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyCallNotesTest {

    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        db = SQLiteDatabase.create(null)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `no call_notes table means nothing to migrate`() {
        assertTrue(readLegacyCallNotes(db).isEmpty())
        eraseLegacyCallNotes(db)
    }

    @Test
    fun `legacy notes are read, then the table is dropped`() {
        db.execSQL(
            "CREATE TABLE call_notes (phone_number TEXT PRIMARY KEY, is_favorite INTEGER DEFAULT 0, " +
                "is_spam INTEGER DEFAULT 0, user_tag TEXT, user_note TEXT, updated_at INTEGER)"
        )
        db.execSQL("INSERT INTO call_notes VALUES ('0162000000', 0, 1, NULL, 'CPF', 42)")
        db.execSQL("INSERT INTO call_notes VALUES ('0612345678', 1, 0, 'tag', NULL, NULL)")
        assertEquals(
            listOf(
                CallNote("0162000000", isFavorite = false, isSpam = true, userTag = null, userNote = "CPF", updatedAt = 42L),
                CallNote("0612345678", isFavorite = true, isSpam = false, userTag = "tag", userNote = null, updatedAt = null),
            ),
            readLegacyCallNotes(db).sortedBy { it.phoneNumber }
        )
        eraseLegacyCallNotes(db)
        assertTrue(readLegacyCallNotes(db).isEmpty())
    }
}
