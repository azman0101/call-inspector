package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
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
class ReporterProfileStoreTest {

    private lateinit var store: ReporterProfileStore

    @Before
    fun setUp() = runBlocking {
        // SQLCipher's native library and the Android Keystore don't exist on the JVM: the storage logic is
        // tested on plain SQLite here, the encryption in androidTest (ReporterProfileEncryptionTest).
        store = ReporterProfileStore(ApplicationProvider.getApplicationContext<Context>()) { context, callback ->
            ReporterProfileStore.open(context, FrameworkSQLiteOpenHelperFactory(), callback)
        }
        store.clear()
        store.clearReports()
        store.clearLineNumbers()
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

    @Test
    fun `reference number and share choice are stored`() = runBlocking {
        store.save(ReporterProfile("Camille", "Test", "camille@example.invalid", "", " ZYX987654321 ", false))
        assertEquals(
            ReporterProfile("Camille", "Test", "camille@example.invalid", "", "ZYX987654321", false),
            store.load()
        )
        store.save(ReporterProfile(firstName = "Camille", shareContact = true))
        assertEquals(true, store.load()?.shareContact)
        store.save(ReporterProfile(firstName = "Camille"))
        assertNull(store.load()?.shareContact)
        assertFalse(ReporterProfile(shareContact = false).isEmpty())
    }

    @Test
    fun `postal code and city are stored for J'alerte l'Arcep`() = runBlocking {
        store.save(ReporterProfile(firstName = "Camille", postalCode = " 69003 ", city = " Lyon "))
        assertEquals(ReporterProfile(firstName = "Camille", postalCode = "69003", city = "Lyon"), store.load())
        assertFalse(ReporterProfile(postalCode = "69003").isEmpty())
        assertFalse(ReporterProfile(city = "Lyon").isEmpty())
    }

    @Test
    fun `a version 1 profile is migrated with defaults`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(ReporterProfileStore.DB_NAME)
        val v1 = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE reporter_profile (id INTEGER PRIMARY KEY CHECK (id = 1), first_name TEXT NOT NULL, " +
                        "last_name TEXT NOT NULL, email TEXT NOT NULL, phone TEXT NOT NULL)"
                )
                db.execSQL("INSERT INTO reporter_profile VALUES (1, 'Camille', 'Test', 'camille@example.invalid', '')")
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        ReporterProfileStore.open(context, FrameworkSQLiteOpenHelperFactory(), v1).close()

        val migrated = ReporterProfileStore(context) { ctx, callback ->
            ReporterProfileStore.open(ctx, FrameworkSQLiteOpenHelperFactory(), callback)
        }
        assertEquals(ReporterProfile("Camille", "Test", "camille@example.invalid", ""), migrated.load())
    }

    @Test
    fun `reports are counted per number and survive clearing the profile`() = runBlocking {
        assertTrue(store.reportStats().isEmpty())
        store.save(ReporterProfile(firstName = "Camille"))
        store.recordReport("0162000000", 1_000L)
        store.recordReport("0162000000", 3_000L)
        store.recordReport("0270000000", 2_000L)
        store.clear()
        assertEquals(
            mapOf("0162000000" to ReportStats(2, 3_000L), "0270000000" to ReportStats(1, 2_000L)),
            store.reportStats()
        )
    }

    @Test
    fun `line numbers are remembered per line and a correction replaces the old one`() = runBlocking {
        assertTrue(store.lineNumbers().isEmpty())
        store.saveLineNumber("comp/Svc|1", " 06 39 98 00 01 ")
        store.saveLineNumber("comp/Svc|2", "0639980002")
        store.saveLineNumber("comp/Svc|1", "07 11 22 33 44")
        store.clear()
        assertEquals(mapOf("comp/Svc|1" to "07 11 22 33 44", "comp/Svc|2" to "0639980002"), store.lineNumbers())
    }
}
