package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ForeignNumbersTest {

    @Test
    fun `a German mobile gives its country, kind of line and original operator`() {
        val number = ForeignNumbers.describe(PhoneNumberFormatter.normalize("+491734119637"))!!

        assertEquals("+491734119637", number.e164)
        assertEquals("+49 173 4119637", number.formatted)
        assertEquals(49, number.countryCode)
        assertEquals("DE", number.regionCode)
        assertEquals("Allemagne", number.countryName)
        assertEquals("Mobile", number.lineType)
        assertEquals("Vodafone", number.originalCarrier)
        assertTrue(number.isValid)
        assertEquals("Vodafone · Allemagne", number.summary)
    }

    @Test
    fun `a number dialled with 00 is read as international`() {
        assertEquals("+49301234567", PhoneNumberFormatter.normalize("00 49 30 1234567"))
        val berlin = ForeignNumbers.describe(PhoneNumberFormatter.normalize("0049301234567"))!!
        assertEquals("Allemagne", berlin.countryName)
        assertEquals("Fixe", berlin.lineType)
        assertNull("fixed lines have no operator in the data", berlin.originalCarrier)
        assertEquals("Allemagne", berlin.summary)
    }

    @Test
    fun `a number that fits no range of its country is flagged, with its country`() {
        // 07700 900xxx: the range Ofcom keeps for fiction.
        val fictional = ForeignNumbers.describe("+447700900123")!!
        assertFalse(fictional.isValid)
        assertEquals("Royaume-Uni", fictional.countryName)
        assertEquals("Type inconnu", fictional.lineType)
    }

    @Test
    fun `codes shared by no country and unreadable numbers`() {
        assertEquals("Réseau international", ForeignNumbers.describe("+80012345678")!!.countryName)
        assertNull(ForeignNumbers.describe("+1"))
        assertNull(ForeignNumbers.describe("+"))
    }

    @Test
    fun `the numbers the ARCEP publishes stay French, overseas ones included`() {
        assertEquals("0612345678", PhoneNumberFormatter.normalize("+33 6 12 34 56 78"))
        assertEquals("0612345678", PhoneNumberFormatter.normalize("0033612345678"))
        assertEquals("0692123456", PhoneNumberFormatter.normalize("+262 692 12 34 56")) // La Réunion
        assertEquals("0639123456", PhoneNumberFormatter.normalize("+262 639 12 34 56")) // Mayotte
        assertEquals("0508412345", PhoneNumberFormatter.normalize("+508 41 23 45")) // Saint-Pierre-et-Miquelon
        assertEquals("0508412345", PhoneNumberFormatter.normalize("00508412345"))
        assertEquals("0690123456", PhoneNumberFormatter.normalize("+590 690 12 34 56")) // Guadeloupe, St-Martin, St-Barthélemy
        assertEquals("0694123456", PhoneNumberFormatter.normalize("+594 694 12 34 56")) // Guyane
        assertEquals("0696123456", PhoneNumberFormatter.normalize("+596 696 12 34 56")) // Martinique
        for (french in listOf("0612345678", "0692123456", "0508412345", "3635", "")) {
            assertFalse(french, ForeignNumbers.isForeign(french))
            assertNull(french, ForeignNumbers.describe(french))
        }
        assertEquals(PhoneNumberType.FIXE_OUTRE_MER, PhoneNumberType.classify("0508412345"))
    }

    @Test
    fun `the territories with their own regulator are foreign`() {
        mapOf(
            "+687 75 12 34" to "Nouvelle-Calédonie",
            "+689 87 12 34 56" to "Polynésie française",
            "+681 72 12 34" to "Wallis-et-Futuna",
            "+269 321 23 45" to "Comores",
        ).forEach { (raw, country) ->
            val normalized = PhoneNumberFormatter.normalize(raw)
            assertTrue(raw, ForeignNumbers.isForeign(normalized))
            assertEquals(raw, country, ForeignNumbers.describe(normalized)?.countryName)
            assertEquals(raw, PhoneNumberType.INTERNATIONAL, PhoneNumberType.classify(normalized))
        }
    }

    @Test
    fun `foreign numbers are written as their country writes them, French ones as before`() {
        assertEquals("+49 173 4119637", PhoneNumberFormatter.format("+491734119637"))
        assertEquals("+32 470 12 34 56", PhoneNumberFormatter.format("0032470123456"))
        assertEquals("06 12 34 56 78", PhoneNumberFormatter.format("+33612345678"))
        assertEquals("05 08 41 23 45", PhoneNumberFormatter.format("+508412345"))
    }

    @Test
    fun `the 33700 message is "spam vocal" followed by the number`() {
        assertEquals("33700", SpamVocal.NUMBER)
        assertEquals("spam vocal +491734119637", SpamVocal.message("+491734119637"))
    }
}
