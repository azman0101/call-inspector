package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CallNotesEncryptionTest {

    @Test
    fun noteRoundTripsAndIsNotStoredInCleartext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = CallNotesStore.getInstance(context)
        val marker = "call-note-marker-7d2b4e91"

        store.save("0162000000", isFavorite = true, isSpam = true, userTag = null, userNote = marker)
        assertEquals(marker, store.get("0162000000")?.userNote)

        val databaseDir = context.getDatabasePath(CallNotesStore.DB_NAME).parentFile!!
        databaseDir.listFiles { file -> file.name.startsWith(CallNotesStore.DB_NAME) }!!.forEach { file ->
            assertFalse(file.name, file.readBytes().toString(Charsets.ISO_8859_1).contains(marker))
        }
        // arcep_data.db no longer holds notes at all.
        assertFalse(ArcepDatabaseManager.getInstance(context).read().any { it.userNote == marker })
    }
}
