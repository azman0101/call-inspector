package net.slashetc.callinspector.data.db

import android.content.Context
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
class ReporterProfileStoreTest {

    private lateinit var store: ReporterProfileStore

    @Before
    fun setUp() = runBlocking {
        store = ReporterProfileStore.getInstance(ApplicationProvider.getApplicationContext<Context>())
        store.clear()
    }

    @Test
    fun `nothing is stored by default`() = runBlocking {
        assertNull(store.load())
    }

    @Test
    fun `saved profile is trimmed and read back`() = runBlocking {
        store.save(ReporterProfile(" Camille ", "Test", "camille@example.invalid ", ""))
        assertEquals(ReporterProfile("Camille", "Test", "camille@example.invalid", ""), store.load())
    }

    @Test
    fun `saving again replaces the single profile`() = runBlocking {
        store.save(ReporterProfile("Camille", "Test", "camille@example.invalid", ""))
        store.save(ReporterProfile("Dominique", "Test", "dominique@example.invalid", "0611223344"))
        assertEquals(ReporterProfile("Dominique", "Test", "dominique@example.invalid", "0611223344"), store.load())
    }

    @Test
    fun `clear removes the profile`() = runBlocking {
        store.save(ReporterProfile("Camille", "Test", "camille@example.invalid", ""))
        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `blank profile is empty`() {
        assertTrue(ReporterProfile(" ", "", "", "").isEmpty())
        assertFalse(ReporterProfile(email = "camille@example.invalid").isEmpty())
    }
}
