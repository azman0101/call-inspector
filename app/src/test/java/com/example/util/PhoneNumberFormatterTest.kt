package com.example.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PhoneNumberFormatterTest {

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
    }

    @Test
    fun normalize_withFrenchPrefixPlus33_replacesWithLeadingZero() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("+33612345678"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("+33 1 23 45 67 89"))
    }

    @Test
    fun normalize_withFrenchPrefix0033_replacesWithLeadingZero() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("0033612345678"))
        assertEquals("0123456789", PhoneNumberFormatter.normalize("0033 1.23.45.67.89"))
    }

    @Test
    fun normalize_withOverseasTerritoriesPrefixes_replacesWithLeadingZero() {
        // Guadeloupe / Saint Martin / Saint Barthélemy (+590)
        assertEquals("0690123456", PhoneNumberFormatter.normalize("+590 690 12 34 56"))

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
    }
}
