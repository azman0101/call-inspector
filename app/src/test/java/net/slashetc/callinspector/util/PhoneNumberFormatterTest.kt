package net.slashetc.callinspector.util

import org.junit.Assert.assertEquals
import java.util.Calendar
import java.util.TimeZone
import org.junit.Test

class PhoneNumberFormatterTest {

    // --- Tests for normalize ---

    @Test
    fun normalize_withNull_returnsEmptyString() {
        assertEquals("", PhoneNumberFormatter.normalize(null))
    }

    @Test
    fun normalize_withEmptyAndBlankStrings_returnsEmptyString() {
        assertEquals("", PhoneNumberFormatter.normalize(""))
        assertEquals("", PhoneNumberFormatter.normalize("   "))
        assertEquals("", PhoneNumberFormatter.normalize("\t\n"))
    }

    @Test
    fun normalize_removesFormattingCharactersAndSpaces() {
        assertEquals("0123456789", PhoneNumberFormatter.normalize("01 23 45 67 89"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("01-23-45-67-89"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("01.23.45.67.89"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("(01) 23 45 67 89"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("  (01) . 23 - 45 67 89  "))
    }

    @Test
    fun normalize_withFrenchPrefixPlus33_replacesWithLeadingZero() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("+33612345678"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("+33 1 23 45 67 89"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("+33.1.23.45.67.89"))
    }

    @Test
    fun normalize_withFrenchPrefix0033_replacesWithLeadingZero() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("0033612345678"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("0033 1.23.45.67.89"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("0033-1-23-45-67-89"))
    }

    @Test
    fun normalize_withOverseasTerritoriesPrefixes_replacesWithLeadingZero() {
        // Guadeloupe / Saint Martin / Saint Barthélemy (+590)
        assertEquals("0690123456", PhoneNumberFormatter.normalize("+590 690 12 34 56"))
        assertEquals("0690123456", PhoneNumberFormatter.normalize("+590-690-12-34-56"))

        // French Guiana (+594)
        assertEquals("0694123456", PhoneNumberFormatter.normalize("+594 694 12 34 56"))

        // Martinique (+596)
        assertEquals("0696123456", PhoneNumberFormatter.normalize("+596 696 12 34 56"))

        // Réunion / Mayotte (+262)
        assertEquals("0639123456", PhoneNumberFormatter.normalize("+262 639 12 34 56"))
    }

    @Test
    fun normalize_withStandardOrUnmatchedNumbers_returnsCleanedString() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("0612345678"))
        assertEquals("+14155552671", PhoneNumberFormatter.normalize("+1 415 555 2671"))
        assertEquals("3635", PhoneNumberFormatter.normalize("36 35"))
        assertEquals("118218", PhoneNumberFormatter.normalize("118 218"))
    }

    // --- Tests for format ---

    @Test
    fun format_withNullOrBlank_returnsInconnu() {
        assertEquals("Inconnu", PhoneNumberFormatter.format(null))
        assertEquals("Inconnu", PhoneNumberFormatter.format(""))
        assertEquals("Inconnu", PhoneNumberFormatter.format("   "))
    }

    @Test
    fun format_withTenDigitFrenchNumber_formatsInPairs() {
        assertEquals("01 23 45 67 89", PhoneNumberFormatter.format("0123456789"))
        assertEquals("06 12 34 56 78", PhoneNumberFormatter.format("+33612345678"))
        assertEquals("06 12 34 56 78", PhoneNumberFormatter.format("0033 6 12 34 56 78"))
    }

    @Test
    fun format_withFourDigitShortCode_formatsInPairs() {
        assertEquals("36 35", PhoneNumberFormatter.format("3635"))
        assertEquals("36 35", PhoneNumberFormatter.format("36 35"))
    }

    @Test
    fun format_withSixDigitShortCode_formatsInThrees() {
        assertEquals("118 218", PhoneNumberFormatter.format("118218"))
        assertEquals("118 218", PhoneNumberFormatter.format("118 218"))
    }

    @Test
    fun format_withOtherDigitsLongerThanEight_chunksInPairs() {
        assertEquals("01 23 45 67 89 0", PhoneNumberFormatter.format("01234567890"))
    }

    @Test
    fun format_withNonDigitOrUnmatchedLength_returnsOriginal() {
        assertEquals("12345", PhoneNumberFormatter.format("12345"))
        assertEquals("+1 415 555 2671", PhoneNumberFormatter.format("+1 415 555 2671"))
    }

    // --- Tests for formatDuration ---

    @Test
    fun formatDuration_zeroOrNegative_returnsZeroSec() {
        assertEquals("0 s", PhoneNumberFormatter.formatDuration(0))
        assertEquals("0 s", PhoneNumberFormatter.formatDuration(-10))
    }

    @Test
    fun formatDuration_secondsOnly_returnsSeconds() {
        assertEquals("45 s", PhoneNumberFormatter.formatDuration(45))
    }

    @Test
    fun formatDuration_exactMinutes_returnsMinutes() {
        assertEquals("2 min", PhoneNumberFormatter.formatDuration(120))
    }

    @Test
    fun formatDuration_minutesAndSeconds_returnsMinutesAndSeconds() {
        assertEquals("2m 15s", PhoneNumberFormatter.formatDuration(135))
    }

    // --- Tests for formatTimestamp ---

    @Test
    fun formatTimestamp_today_formatsAujourdhui() {
        val now = System.currentTimeMillis()
        val formatted = PhoneNumberFormatter.formatTimestamp(now)
        assert(formatted.startsWith("Aujourd'hui à "))
    }

    @Test
    fun formatTimestamp_yesterday_formatsHier() {
        val yesterday = System.currentTimeMillis() - (1000 * 60 * 60 * 24 + 1000)
        val formatted = PhoneNumberFormatter.formatTimestamp(yesterday)
        assert(formatted.startsWith("Hier à "))
    }

    @Test
    fun formatTimestamp_withinWeek_formatsDayAndMonth() {
        val threeDaysAgo = System.currentTimeMillis() - (3 * 1000 * 60 * 60 * 24 + 1000)
        val formatted = PhoneNumberFormatter.formatTimestamp(threeDaysAgo)
        assert(!formatted.startsWith("Aujourd'hui") && !formatted.startsWith("Hier"))
        assert(formatted.contains(" à "))
    }

    @Test
    fun formatTimestamp_olderThanWeek_formatsFullDate() {
        val tenDaysAgo = System.currentTimeMillis() - (10 * 1000 * 60 * 60 * 24 + 1000)
        val formatted = PhoneNumberFormatter.formatTimestamp(tenDaysAgo)
        assert(formatted.matches(Regex("""\d{2}/\d{2}/\d{4} \d{2}:\d{2}""")))
    }

    // --- formatTimestamp counts calendar days, not 24-hour periods ---

    private val paris = TimeZone.getTimeZone("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: TimeZone = paris): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    @Test
    fun formatTimestamp_yesterdayLessThan24HoursAgo_formatsHier() {
        // The reported bug: 02/10 at 12:16 seen on 03/10 at 09:00 said "Aujourd'hui à 12:16".
        val call = at(2026, 10, 2, 12, 16)
        assertEquals("Hier à 12:16", PhoneNumberFormatter.formatTimestamp(call, at(2026, 10, 3, 9, 0), paris))
    }

    @Test
    fun formatTimestamp_justBeforeAndAfterMidnight_switchesDay() {
        val call = at(2026, 10, 2, 23, 59)
        assertEquals("Aujourd'hui à 23:59", PhoneNumberFormatter.formatTimestamp(call, at(2026, 10, 2, 23, 59), paris))
        assertEquals("Hier à 23:59", PhoneNumberFormatter.formatTimestamp(call, at(2026, 10, 3, 0, 1), paris))
    }

    @Test
    fun formatTimestamp_twoCalendarDaysAgo_formatsDayAndMonthEvenUnder48Hours() {
        val call = at(2026, 10, 1, 23, 0)
        assertEquals("01 oct. à 23:00", PhoneNumberFormatter.formatTimestamp(call, at(2026, 10, 3, 8, 0), paris))
    }

    @Test
    fun formatTimestamp_sixAndSevenCalendarDays_switchesToFullDate() {
        val now = at(2026, 10, 9, 8, 0)
        assertEquals("03 oct. à 20:00", PhoneNumberFormatter.formatTimestamp(at(2026, 10, 3, 20, 0), now, paris))
        assertEquals("02/10/2026 20:00", PhoneNumberFormatter.formatTimestamp(at(2026, 10, 2, 20, 0), now, paris))
    }

    @Test
    fun formatTimestamp_acrossDaylightSavingChange_countsCalendarDays() {
        // 25 October 2026: clocks go back one hour in Paris, that day lasts 25 hours.
        val call = at(2026, 10, 24, 23, 30)
        assertEquals("Hier à 23:30", PhoneNumberFormatter.formatTimestamp(call, at(2026, 10, 25, 23, 45), paris))
    }

    @Test
    fun formatTimestamp_usesThePhoneTimeZone() {
        // 22:30 UTC is already the next day in Paris (00:30), and still the same day in New York (18:30).
        val utc = TimeZone.getTimeZone("UTC")
        val call = at(2026, 10, 2, 22, 30, utc)
        val now = at(2026, 10, 3, 6, 0, utc)
        assertEquals("Aujourd'hui à 00:30", PhoneNumberFormatter.formatTimestamp(call, now, paris))
        assertEquals("Hier à 18:30", PhoneNumberFormatter.formatTimestamp(call, now, TimeZone.getTimeZone("America/New_York")))
    }

    @Test
    fun formatFullTimestamp_isTheDateAndTimeInFull_forCopying() {
        // "Hier à 12:16" on screen; copied, the date it was.
        assertEquals("02/10/2026 12:16", PhoneNumberFormatter.formatFullTimestamp(at(2026, 10, 2, 12, 16), paris))
    }
}
