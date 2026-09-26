package net.slashetc.callinspector

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import net.slashetc.callinspector.data.db.ArcepDatabaseManager
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.util.SearchQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchBugVerificationTest {

    // The Android call log stores numbers in international format.
    private val callFrom0270 = CallLogEntry(
        id = 1L,
        rawNumber = "+33270334455",
        normalizedNumber = "0270334455",
        formattedNumber = "02 70 33 44 55",
        cachedName = null,
        timestamp = 0L,
        durationSeconds = 0L,
        callType = CallType.MISSED,
        lookupResult = ArcepLookupResult(
            queryNumber = "+33270334455",
            normalizedNumber = "0270334455",
            formattedNumber = "02 70 33 44 55",
            operator = null,
            range = null,
            numberType = PhoneNumberType.DEMARCHAGE_COMMERCIAL,
            isFound = false
        )
    )

    // Bug 1: typing "0270" without a space didn't find "02 70 33 44 55".
    @Test
    fun `searching call history by number without spaces should return matching call`() {
        assertTrue(SearchQueries.matchesCall(callFrom0270, "0270"))
        assertTrue(SearchQueries.matchesCall(callFrom0270, "0270334455"))
        assertTrue(SearchQueries.matchesCall(callFrom0270, "+33270"))
    }

    @Test
    fun `call history search keeps matching spaced numbers and rejects other numbers`() {
        assertTrue(SearchQueries.matchesCall(callFrom0270, "02 70"))
        assertTrue(SearchQueries.matchesCall(callFrom0270, ""))
        assertFalse(SearchQueries.matchesCall(callFrom0270, "0612"))
    }

    // Bug 2: operator names (longer than 6 characters) never reached the database search.
    @Test
    fun `searching manual lookup by operator name should query operators`() {
        assertEquals("Bouygues Telecom", SearchQueries.prefixSearchQuery("Bouygues Telecom"))
        assertEquals("Orange", SearchQueries.prefixSearchQuery("  Orange "))
    }

    @Test
    fun `manual prefix search ignores spaces in numbers`() {
        assertEquals("0270", SearchQueries.prefixSearchQuery("02 70"))
        assertEquals("0270", SearchQueries.prefixSearchQuery("0270"))
    }

    @Test
    fun `manual prefix search skips full numbers and single characters`() {
        assertNull(SearchQueries.prefixSearchQuery("0270334455"))
        assertNull(SearchQueries.prefixSearchQuery("0"))
        assertNull(SearchQueries.prefixSearchQuery("B"))
    }

    @Test
    fun `searching database by operator name should return operator entries`() = runTest {
        val dbManager = ArcepDatabaseManager.getInstance(ApplicationProvider.getApplicationContext<Application>())
        assertFalse(dbManager.searchPrefixesOrOperators("Bouygues Telecom").isEmpty())
    }
}
