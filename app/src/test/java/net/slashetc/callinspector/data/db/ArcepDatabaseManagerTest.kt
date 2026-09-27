package net.slashetc.callinspector.data.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    @Test
    fun `test lookupNumbers with an empty collection returns an empty map`() = runBlocking {
        val result = dbManager.lookupNumbers(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `test lookupNumbers finds an assigned range and its operator`() = runBlocking {
        val result = dbManager.lookupNumbers(listOf("0105612345"))
        val lookup = result["0105612345"]

        assertNotNull(lookup)
        assertTrue(lookup!!.isFound)
        assertEquals("0105612345", lookup.normalizedNumber)
        assertEquals(PhoneNumberType.FIXE_ILE_DE_FRANCE, lookup.numberType)
        assertEquals("FRTE", lookup.operator?.code)
        assertEquals("Orange", lookup.operator?.name)
    }

    @Test
    fun `test lookupNumbers returns not found for an unassigned number`() = runBlocking {
        val result = dbManager.lookupNumbers(listOf("0000000000"))
        val lookup = result["0000000000"]

        assertNotNull(lookup)
        assertFalse(lookup!!.isFound)
        assertNull(lookup.operator)
        assertNull(lookup.range)
    }

    @Test
    fun `test lookupNumbers marks a blank number as unknown`() = runBlocking {
        val result = dbManager.lookupNumbers(listOf(""))
        val lookup = result[""]

        assertNotNull(lookup)
        assertFalse(lookup!!.isFound)
        assertEquals("", lookup.normalizedNumber)
        assertEquals("Numéro masqué", lookup.formattedNumber)
        assertEquals(PhoneNumberType.INCONNU, lookup.numberType)
    }

    @Test
    fun `test lookupNumbers deduplicates repeated raw numbers but keeps one result per key`() = runBlocking {
        val result = dbManager.lookupNumbers(listOf("0105612345", "0105612345", "0000000000"))

        assertEquals(2, result.size)
        assertTrue(result.getValue("0105612345").isFound)
        assertFalse(result.getValue("0000000000").isFound)
    }

    @Test
    fun `test lookupNumbers batch matches lookupNumber called individually for the same numbers`() = runBlocking {
        val numbers = listOf("0105612345", "1010", "0000000000", "")

        val batched = dbManager.lookupNumbers(numbers)
        for (number in numbers) {
            val single = dbManager.lookupNumber(number)
            val fromBatch = batched.getValue(number)
            assertEquals(single, fromBatch)
        }
    }
}
