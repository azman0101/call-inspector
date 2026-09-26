package net.slashetc.callinspector

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.db.ArcepDatabaseManager
import net.slashetc.callinspector.viewmodel.ArcepViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SearchBugVerificationTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var application: Application
    private lateinit var viewModel: ArcepViewModel
    private lateinit var dbManager: ArcepDatabaseManager

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        viewModel = ArcepViewModel(application)
        dbManager = ArcepDatabaseManager.getInstance(application)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Test confirming Bug #1:
     * When a call from 0270 (formatted as "02 70 33 44 55") exists in call history,
     * searching "0270" without spaces in the search query should return the call.
     * This test currently FAILS because applyFilter does not normalize numbers when filtering.
     */
    @Test
    fun `searching call history by number without spaces should return matching call`() = runTest {
        viewModel.forceLoadSampleCalls()
        advanceUntilIdle()

        // Sample calls contain "0270334455" which formats as "02 70 33 44 55"
        val sampleCalls = viewModel.uiState.value.calls
        assertTrue("Sample calls should contain number 0270334455", sampleCalls.any { it.rawNumber == "0270334455" })

        // Search for "0270" without space
        viewModel.setCallSearchQuery("0270")
        advanceUntilIdle()

        val filteredCalls = viewModel.uiState.value.filteredCalls
        assertFalse(
            "Searching '0270' without space should return call '02 70 33 44 55', but filteredCalls was empty",
            filteredCalls.isEmpty()
        )
        assertTrue(
            "Filtered calls should contain '0270334455'",
            filteredCalls.any { it.rawNumber == "0270334455" || it.normalizedNumber == "0270334455" }
        )
    }

    /**
     * Test confirming Bug #2:
     * When searching by operator name (e.g. "Bouygues Telecom" or "Free Mobile"),
     * manual search in ArcepViewModel should return matching operator results.
     * This test currently FAILS because onManualSearchInput restricts prefixSearchResults to query lengths between 2 and 6.
     */
    @Test
    fun `searching manual lookup by operator name should return matching operator results`() = runTest {
        // Search by full operator name (> 6 characters)
        viewModel.onManualSearchInput("Bouygues Telecom")
        advanceUntilIdle()

        val results = viewModel.uiState.value.prefixSearchResults
        assertFalse(
            "Searching by operator name 'Bouygues Telecom' should return operator prefix results, but prefixSearchResults was empty",
            results.isEmpty()
        )
    }

    /**
     * Test confirming Bug #2 (Database level):
     * Searching prefixes or operators by name in ArcepDatabaseManager should return operator entries.
     * This test currently FAILS because searchPrefixesOrOperators searches r.operator_name in number_ranges instead of o.name in operators.
     */
    @Test
    fun `searching database by operator name should return operator entries`() = runTest {
        val results = dbManager.searchPrefixesOrOperators("Bouygues Telecom")
        assertFalse(
            "dbManager.searchPrefixesOrOperators('Bouygues Telecom') should return matching entries",
            results.isEmpty()
        )
    }
}
