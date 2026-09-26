package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallNotesStoreTest {

    private lateinit var context: Context

    private class FakeLegacyNotes(var notes: List<CallNote>, private val failOnErase: Boolean = false) : LegacyCallNotes {
        var erased = false
        override fun read() = if (erased) emptyList() else notes
        override fun erase() {
            if (failOnErase) throw IllegalStateException("interrupted before erasing")
            erased = true
        }
    }

    // SQLCipher and the Keystore don't exist on the JVM: plain SQLite here, encryption in androidTest.
    private fun store(legacy: LegacyCallNotes? = null) = CallNotesStore(context, { ctx, callback ->
        EncryptedDatabases.open(ctx, FrameworkSQLiteOpenHelperFactory(), CallNotesStore.DB_NAME, callback)
    }, legacy)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(CallNotesStore.DB_NAME)
    }

    @Test
    fun `notes are saved per normalized number`() = runBlocking {
        val store = store()
        assertNull(store.get("0162000000"))
        store.save("+33 1 62 00 00 00", isFavorite = true, isSpam = false, userTag = null, userNote = "Isolation")
        val note = store.get("0162000000")!!
        assertEquals("0162000000", note.phoneNumber)
        assertTrue(note.isFavorite)
        assertFalse(note.isSpam)
        assertEquals("Isolation", note.userNote)
        store.save("0162000000", isFavorite = false, isSpam = true, userTag = null, userNote = null)
        assertEquals(listOf("0162000000"), store.all().keys.toList())
        assertTrue(store.all().getValue("0162000000").isSpam)
        assertNull(store.all().getValue("0162000000").userNote)
    }

    @Test
    fun `cleartext notes are moved to the encrypted store then erased`() = runBlocking {
        val legacy = FakeLegacyNotes(
            listOf(
                CallNote("0162000000", isFavorite = false, isSpam = true, userTag = "tag", userNote = "CPF", updatedAt = 42L),
                CallNote("0612345678", isFavorite = true, isSpam = false, userTag = null, userNote = null, updatedAt = null),
            )
        )
        val all = store(legacy).all()
        assertTrue(legacy.erased)
        assertEquals(setOf("0162000000", "0612345678"), all.keys)
        assertEquals(CallNote("0162000000", false, true, "tag", "CPF", 42L), all["0162000000"])
    }

    @Test
    fun `an interrupted migration is completed on the next start without duplicates`() = runBlocking {
        val notes = listOf(CallNote("0162000000", false, true, null, "CPF", 42L))
        runCatching { store(FakeLegacyNotes(notes, failOnErase = true)).all() }
        val legacy = FakeLegacyNotes(notes)
        assertEquals(notes, store(legacy).all().values.toList())
        assertTrue(legacy.erased)
    }

    @Test
    fun `nothing to migrate leaves the legacy source alone`() = runBlocking {
        val legacy = FakeLegacyNotes(emptyList())
        assertTrue(store(legacy).all().isEmpty())
        assertFalse(legacy.erased)
    }
}
