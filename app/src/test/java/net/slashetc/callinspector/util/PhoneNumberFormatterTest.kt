package net.slashetc.callinspector.util

import org.junit.Assert.assertEquals
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
}
