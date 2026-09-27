package net.slashetc.callinspector.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.slashetc.callinspector.data.model.CallType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallLogRepositoryTest {

    private lateinit var repository: CallLogRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = CallLogRepository(context)
    }

    @Test
    fun `generateSampleCalls returns the 10 fixed sample entries in order with their raw metadata`() = runBlocking {
        val entries = repository.generateSampleCalls()

        assertEquals((1000L..1009L).toList(), entries.map { it.id })
        assertEquals(
            listOf(
                "0162001122", "0639980112", "0270334455", "0199000134", "0639980567",
                "0948123456", "0491002233", "0892353535", "0556000000", "0590203040"
            ),
            entries.map { it.rawNumber }
        )
        assertEquals(
            listOf(
                CallType.MISSED, CallType.INCOMING, CallType.MISSED, CallType.INCOMING, CallType.INCOMING,
                CallType.REJECTED, CallType.INCOMING, CallType.MISSED, CallType.INCOMING, CallType.MISSED
            ),
            entries.map { it.callType }
        )
    }

    @Test
    fun `generateSampleCalls only attaches a cached contact name to the fictional numbers`() = runBlocking {
        val entries = repository.generateSampleCalls()
        val namesByNumber = entries.associate { it.rawNumber to it.cachedName }

        assertEquals("Sophie Martin", namesByNumber["0639980112"])
        assertEquals("Cabinet Médical", namesByNumber["0199000134"])
        assertEquals("Alexandre D.", namesByNumber["0639980567"])
        val withoutName = entries.filterNot { it.rawNumber in setOf("0639980112", "0199000134", "0639980567") }
        assertTrue(withoutName.all { it.cachedName == null })
    }

    @Test
    fun `generateSampleCalls resolves each entry's lookup result through the ARCEP database`() = runBlocking {
        val entries = repository.generateSampleCalls()

        for (entry in entries) {
            assertEquals(entry.rawNumber, entry.lookupResult.queryNumber)
            assertTrue(entry.normalizedNumber.isNotBlank())
            assertEquals(entry.lookupResult.normalizedNumber, entry.normalizedNumber)
            assertEquals(entry.lookupResult.formattedNumber, entry.formattedNumber)
        }

        // 0948123456 falls in a DEMARCHAGE_COMMERCIAL-reserved prefix: it must be flagged as spam by default.
        val demarchage = entries.first { it.rawNumber == "0948123456" }
        assertTrue(demarchage.isSpamFlagged)
    }

    @Test
    fun `getCallLogs falls back to the sample calls when it can't read the device call log`() = runBlocking {
        val fromGetCallLogs = repository.getCallLogs()
        val sample = repository.generateSampleCalls()

        assertEquals(sample.map { it.id }, fromGetCallLogs.map { it.id })
        assertEquals(sample.map { it.rawNumber }, fromGetCallLogs.map { it.rawNumber })
        assertEquals(sample.map { it.lookupResult }, fromGetCallLogs.map { it.lookupResult })
    }

    @Test
    fun `getCallLogs returns nothing when it can't read the device call log and the sample fallback is disabled`() =
        runBlocking {
            val entries = repository.getCallLogs(useSampleIfEmpty = false)
            assertTrue(entries.isEmpty())
        }
}
