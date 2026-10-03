package net.slashetc.callinspector.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.slashetc.callinspector.data.db.ArcepDatabaseManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArcepAutoUpdateTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var clock = 1_000_000_000L

    // --- Fake extranet ARCEP: dates answered to HEAD, synthetic CSV files to GET ---

    private var majnumDate = "Fri, 02 Oct 2026 08:00:00 GMT"
    private var ceDate = "Thu, 01 Oct 2026 08:00:00 GMT"
    private var offline = false
    private val requests = mutableListOf<String>()

    // Plausible volumes (ArcepUpdateManager.MIN_EXPECTED_*): 600 operators, 10 000 ranges.
    private val ceCsv = buildString {
        appendLine("CODE_OPERATEUR;IDENTITE_OPERATEUR;SIRET_ACTEUR")
        repeat(600) { appendLine("OP$it;Opérateur $it;${100_000_000 + it}") }
    }
    private val majnumCsv = buildString {
        appendLine("EZABPQM;Tranche_Debut;Tranche_Fin;Mnémo;Territoire;Date_Attribution")
        repeat(10_000) {
            val prefix = "06%05d".format(it)
            appendLine("$prefix;${prefix}000;${prefix}999;OP${it % 600};Métropole;01/01/2026")
        }
    }

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        requests += "${request.method} $url"
        if (offline) throw IOException("offline")
        val (date, body) = when (url) {
            ArcepUpdateManager.MAJNUM_URL -> majnumDate to majnumCsv
            ArcepUpdateManager.CE_URL -> ceDate to ceCsv
            else -> "" to ""
        }
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .header("Last-Modified", date)
            .body((if (request.method == "HEAD") "" else body).toByteArray(Charsets.ISO_8859_1).toResponseBody("text/csv".toMediaType()))
            .build()
    }.build()

    private val manager = ArcepUpdateManager(context, client)
    private val policy = ArcepAutoUpdate(context) { clock }
    private val gets get() = requests.filter { it.startsWith("GET") }

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("arcep_updates", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // The tests replace the active database with synthetic data: put the bundled one back for other tests.
    @After
    fun restoreBundledDatabase() {
        val copy = File(context.cacheDir, "arcep_bundled_copy.db")
        context.assets.open("arcep_data.db").use { input -> copy.outputStream().use { input.copyTo(it) } }
        ArcepDatabaseManager.getInstance(context).replaceDatabaseFile(copy)
    }

    private fun autoUpdate(): ArcepUpdateResult = runBlocking {
        manager.checkAndDownloadUpdate(onlyIfChanged = true) {}.also { policy.record(it) }
    }

    @Test
    fun `first launch is due, then not again before a week`() {
        assertTrue(policy.isDue())
        policy.record(ArcepUpdateResult.UP_TO_DATE)
        assertFalse(policy.isDue())
        clock += ArcepAutoUpdate.CHECK_INTERVAL_MS - 1
        assertFalse(policy.isDue())
        clock += 1
        assertTrue(policy.isDue())
    }

    @Test
    fun `a failed check is retried at the next launch, a rejected one waits a week`() {
        policy.record(ArcepUpdateResult.FAILED)
        assertTrue(policy.isDue())
        policy.record(ArcepUpdateResult.REJECTED)
        assertFalse(policy.isDue())
    }

    @Test
    fun `first launch downloads the files and records their dates`() {
        // The bundled database has no date for identifiants_CE.csv: the first check must download.
        assertEquals(ArcepUpdateResult.UPDATED, autoUpdate())
        assertEquals(2, gets.size)

        val metadata = ArcepDatabaseManager.getInstance(context).metadata()
        assertEquals(majnumDate, metadata[ArcepUpdateManager.META_MAJNUM_DATE])
        assertEquals(ceDate, metadata[ArcepUpdateManager.META_CE_DATE])
        assertEquals("10000", metadata["ranges_count"])
        assertFalse(policy.isDue())
    }

    @Test
    fun `unchanged files are not downloaded again, a newer file is`() {
        assertEquals(ArcepUpdateResult.UPDATED, autoUpdate())
        requests.clear()

        assertEquals(ArcepUpdateResult.UP_TO_DATE, autoUpdate())
        assertEquals(listOf("HEAD ${ArcepUpdateManager.MAJNUM_URL}", "HEAD ${ArcepUpdateManager.CE_URL}"), requests)

        ceDate = "Sat, 03 Oct 2026 08:00:00 GMT"
        assertEquals(ArcepUpdateResult.UPDATED, autoUpdate())
        assertEquals(2, gets.size)
        assertEquals(ceDate, ArcepDatabaseManager.getInstance(context).metadata()[ArcepUpdateManager.META_CE_DATE])
    }

    @Test
    fun `offline, the automatic check downloads nothing, keeps the database and is retried`() {
        val before = ArcepDatabaseManager.getInstance(context).metadata()
        offline = true

        assertEquals(ArcepUpdateResult.FAILED, autoUpdate())
        assertTrue(gets.isEmpty())
        assertEquals(before, ArcepDatabaseManager.getInstance(context).metadata())
        assertTrue(policy.isDue())
    }

    @Test
    fun `a tap on the button downloads even when the files are unchanged`() = runBlocking {
        assertEquals(ArcepUpdateResult.UPDATED, autoUpdate())
        requests.clear()

        assertEquals(ArcepUpdateResult.UPDATED, manager.checkAndDownloadUpdate {})
        assertEquals(2, gets.size)
    }

    @Test
    fun `isUpToDate needs both dates, equal to the recorded ones`() {
        val recorded = mapOf(ArcepUpdateManager.META_MAJNUM_DATE to "A", ArcepUpdateManager.META_CE_DATE to "B")
        assertTrue(ArcepUpdateManager.isUpToDate("A", "B", recorded))
        assertFalse(ArcepUpdateManager.isUpToDate("A", "C", recorded))
        assertFalse(ArcepUpdateManager.isUpToDate("", "B", recorded))
        assertFalse(ArcepUpdateManager.isUpToDate("A", "B", mapOf(ArcepUpdateManager.META_MAJNUM_DATE to "A")))
        assertFalse(ArcepUpdateManager.isUpToDate("", "", emptyMap()))
    }
}
