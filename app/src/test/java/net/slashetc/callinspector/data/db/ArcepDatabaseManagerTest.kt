package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArcepDatabaseManagerTest {

    private lateinit var dbManager: ArcepDatabaseManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        dbManager = ArcepDatabaseManager.getInstance(context)
    }

    @Test
    fun `test searchPrefixesOrOperators with plain query returns matching results`() = runBlocking {
        val results = dbManager.searchPrefixesOrOperators("Orange")
        assertTrue(results.isNotEmpty())
        assertTrue(results.any { it.operator?.name?.contains("Orange", ignoreCase = true) == true })
    }

    @Test
    fun `test searchPrefixesOrOperators with wildcard percent character does not match all entries`() = runBlocking {
        // Query '%' as a raw literal string. Since '%' is escaped as '\%', it should only match operators/prefixes containing literal '%' (which don't exist here).
        val wildcardResults = dbManager.searchPrefixesOrOperators("%")
        assertEquals(0, wildcardResults.size)
    }

    @Test
    fun `test searchPrefixesOrOperators with wildcard underscore character does not match single random characters`() = runBlocking {
        // Query '_' as a raw literal string. Since '_' is escaped as '\_', it should only match operators/prefixes containing literal '_'.
        val underscoreResults = dbManager.searchPrefixesOrOperators("_")
        assertEquals(0, underscoreResults.size)
    }
}
