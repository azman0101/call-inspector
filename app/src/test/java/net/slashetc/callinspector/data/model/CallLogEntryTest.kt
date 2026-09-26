package net.slashetc.callinspector.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class CallLogEntryTest {

    private val sampleLookupResult = ArcepLookupResult(
        queryNumber = "0162000000",
        normalizedNumber = "0162000000",
        formattedNumber = "01 62 00 00 00",
        operator = null,
        range = null,
        numberType = PhoneNumberType.INCONNU,
        isFound = false
    )

    @Test
    fun `CallLogEntry default properties are correctly initialized`() {
        val entry = CallLogEntry(
            id = 100L,
            rawNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            cachedName = null,
            timestamp = 1600000000000L,
            durationSeconds = 45L,
            callType = CallType.INCOMING,
            lookupResult = sampleLookupResult
        )

        assertEquals(100L, entry.id)
        assertFalse(entry.isSpamFlagged)
        assertFalse(entry.isFavorite)
        assertNull(entry.userNote)
    }

    @Test
    fun `CallLogEntry custom properties are correctly preserved`() {
        val entry = CallLogEntry(
            id = 101L,
            rawNumber = "+33162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            cachedName = "John Doe",
            timestamp = 1600000000000L,
            durationSeconds = 120L,
            callType = CallType.MISSED,
            lookupResult = sampleLookupResult,
            isSpamFlagged = true,
            isFavorite = true,
            userNote = "Potential telemarketer"
        )

        assertEquals("John Doe", entry.cachedName)
        assertEquals(CallType.MISSED, entry.callType)
        assertEquals(true, entry.isSpamFlagged)
        assertEquals(true, entry.isFavorite)
        assertEquals("Potential telemarketer", entry.userNote)
    }
}
