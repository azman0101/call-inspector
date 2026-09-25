package com.example.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureArcepDatabaseTest {
    @Test
    fun callNotesRoundTripAndAreNotPresentInDatabaseBytes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ArcepDatabaseManager.getInstance(context)
        val cleartextMarker = "secret-note-marker-9f4c8d7b"

        manager.saveCallNote("+33123456789", true, true, "test-tag", cleartextMarker)

        val note = manager.getCallNote("+33123456789")
        assertEquals(cleartextMarker, note?.userNote)
        assertEquals("test-tag", note?.userTag)
        assertEquals(true, note?.isFavorite)
        assertEquals(true, note?.isSpam)

        val database = context.getDatabasePath("arcep_data_secure.db")
        assertFalse(database.readBytes().toString(Charsets.ISO_8859_1).contains(cleartextMarker))
    }

    @Test
    fun arcepRefreshKeepsUserNotesInEncryptedDatabase() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = ArcepDatabaseManager.getInstance(context)
        val cleartextMarker = "note-survives-arcep-refresh"
        manager.saveCallNote("+33123456789", true, false, "tag", cleartextMarker)

        val updateFile = java.io.File(context.cacheDir, "arcep-update-test.db")
        val update = SQLiteDatabase.openOrCreateDatabase(updateFile.absolutePath, null)
        try {
            update.execSQL("CREATE TABLE operators (code TEXT PRIMARY KEY, name TEXT NOT NULL, siret TEXT, rcs TEXT, address TEXT, declaration_date TEXT)")
            update.execSQL("CREATE TABLE number_ranges (id INTEGER PRIMARY KEY AUTOINCREMENT, ezabpqm TEXT NOT NULL, tranche_debut TEXT NOT NULL, tranche_fin TEXT NOT NULL, operator_code TEXT NOT NULL, operator_name TEXT NOT NULL, territory TEXT, attribution_date TEXT)")
            update.execSQL("CREATE TABLE arcep_metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            update.execSQL("INSERT INTO number_ranges VALUES (1, '3312345', '3312345000', '3312345999', 'TEST', 'Test operator', 'FR', '2026-01-01')")
        } finally {
            update.close()
        }

        manager.replaceDatabaseFile(updateFile)
        assertEquals(cleartextMarker, manager.getCallNote("+33123456789")?.userNote)
        assertEquals(1, manager.getStats().totalRanges)
    }

    @Test
    fun wrappedDatabasePassphraseIsStableAndThirtyTwoBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = KeyStoreHelper(context).getDatabasePassphrase()
        val second = KeyStoreHelper(context).getDatabasePassphrase()
        assertEquals(32, first.size)
        org.junit.Assert.assertArrayEquals(first, second)
        first.fill(0)
        second.fill(0)
    }
}
