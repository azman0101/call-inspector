package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReporterProfileEncryptionTest {

    @Test
    fun profileRoundTripsAndIsNotStoredInCleartext() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ReporterProfileStore.getInstance(context)
        val marker = "reporter-marker-5c1e9a0f@example.invalid"

        store.save(ReporterProfile("Camille", "Test", marker, "0611223344"))
        assertEquals(marker, store.load()?.email)

        val databaseDir = context.getDatabasePath(ReporterProfileStore.DB_NAME).parentFile!!
        databaseDir.listFiles { file -> file.name.startsWith(ReporterProfileStore.DB_NAME) }!!.forEach { file ->
            assertFalse(file.name, file.readBytes().toString(Charsets.ISO_8859_1).contains(marker))
        }
        store.clear()
    }

    @Test
    fun wrappedPassphraseIsStableAndThirtyTwoBytes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val keyStore = { DatabaseKeyStore(context, "test_wrapping_key", "test-db-key.bin", "test_secure.db") }
        val first = keyStore().getDatabasePassphrase()
        val second = keyStore().getDatabasePassphrase()
        assertEquals(32, first.size)
        assertArrayEquals(first, second)
        first.fill(0)
        second.fill(0)
    }
}
