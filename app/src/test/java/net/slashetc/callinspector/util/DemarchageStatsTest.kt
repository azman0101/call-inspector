package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class DemarchageStatsTest {

    private val paris = TimeZone.getTimeZone("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(paris).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private var nextId = 0L
    private fun call(timestamp: Long, number: String, spam: Boolean = false) = CallLogEntry(
        id = nextId++,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = number,
        cachedName = null,
        timestamp = timestamp,
        durationSeconds = 0,
        callType = CallType.MISSED,
        lookupResult = ArcepLookupResult(number, number, number, null, null, PhoneNumberType.classify(number), true),
        isSpamFlagged = spam || PhoneNumberType.classify(number).isDemarchage
    )

    // Thursday 1 October 2026, 15:00 in Paris.
    private val now = at(2026, 10, 1, 15)

    @Test
    fun `today counts from midnight and the week from Monday midnight, local time`() {
        val calls = listOf(
            call(at(2026, 10, 1, 9), "0162001122"),        // today, telemarketing range
            call(at(2026, 10, 1, 0, 0), "0948123456"),     // today, at midnight exactly
            call(at(2026, 9, 30, 23, 59), "0270334455"),   // yesterday
            call(at(2026, 9, 28, 0, 0), "0377000000"),     // Monday midnight: this week
            call(at(2026, 9, 27, 23, 59), "0424000000"),   // Sunday before: last week
        )

        assertEquals(DemarchageCounts(today = 2, thisWeek = 4), DemarchageStats.count(calls, now, paris))
    }

    @Test
    fun `calls flagged as spam count, ordinary and future calls do not`() {
        val calls = listOf(
            call(at(2026, 10, 1, 10), "0612345678", spam = true), // mobile marked spam by the user
            call(at(2026, 10, 1, 11), "0612345679"),              // ordinary mobile
            call(at(2026, 10, 1, 16), "0162001122"),              // after "now" (clock change, bad data)
        )

        assertEquals(DemarchageCounts(today = 1, thisWeek = 1), DemarchageStats.count(calls, now, paris))
    }

    @Test
    fun `on a Monday the week and the day start together, on a Sunday the week started six days earlier`() {
        val monday = at(2026, 9, 28, 8)
        assertEquals(DemarchageStats.startOfDay(monday, paris), DemarchageStats.startOfWeek(monday, paris))

        val sunday = at(2026, 10, 4, 22)
        assertEquals(at(2026, 9, 28, 0), DemarchageStats.startOfWeek(sunday, paris))
    }

    @Test
    fun `no calls, no telemarketing`() {
        assertEquals(DemarchageCounts(0, 0), DemarchageStats.count(emptyList(), now, paris))
    }
}
