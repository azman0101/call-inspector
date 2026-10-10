package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhoCalledTest {

    private fun call(
        number: String,
        operator: ArcepOperator?,
        type: CallType = CallType.MISSED,
        name: String? = null,
        spam: Boolean = false,
    ) = CallLogEntry(
        id = 1,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = PhoneNumberFormatter.format(number),
        cachedName = name,
        timestamp = System.currentTimeMillis() - 60_000,
        durationSeconds = 0,
        callType = type,
        lookupResult = ArcepLookupResult(number, number, PhoneNumberFormatter.format(number), operator, null, PhoneNumberType.classify(number), operator != null),
        isSpamFlagged = spam || PhoneNumberType.classify(number).isDemarchage
    )

    @Test
    fun `the answer names the number, when, the operator and its code, and warns about telemarketing`() {
        val answer = WhoCalled.answer(call("0162001122", ArcepOperator("QWLK", "Qwalikom")), hasPermission = true)

        assertEquals("01 62 00 11 22", answer.title)
        val lines = answer.message.lines()
        assertTrue(lines[0].startsWith("Appel manqué aujourd'hui à "))
        assertEquals("Opérateur : Qwalikom (QWLK)", lines[1])
        assertEquals(PhoneNumberType.DEMARCHAGE_COMMERCIAL.label, lines[2])
        assertEquals("⚠️ Numéro de démarchage ou signalé comme spam", lines[3])
    }

    @Test
    fun `a contact name is shown, an unknown operator has no code, an ordinary number no warning`() {
        val answer = WhoCalled.answer(call("0612345678", null, type = CallType.REJECTED, name = "Camille"), hasPermission = true)

        assertTrue(answer.message.startsWith("Appel rejeté "))
        assertTrue(answer.message.contains("\nContact : Camille\n"))
        assertTrue(answer.message.contains("\nOpérateur : Opérateur non identifié\n"))
        assertFalse(answer.message.contains("⚠️"))
    }

    @Test
    fun `a long press copies each value without its label, the warning has nothing to copy`() {
        val call = call("0162001122", ArcepOperator("QWLK", "Qwalikom"), name = "Camille")
        val answer = WhoCalled.answer(call, hasPermission = true)

        assertEquals("01 62 00 11 22", answer.titleCopy)
        val copies = answer.lines.map { it.copy }
        assertEquals(PhoneNumberFormatter.formatFullTimestamp(call.timestamp), copies[0])
        assertEquals("Camille", copies[1])
        assertTrue(answer.lines[1].sensitive)
        assertEquals("Qwalikom (QWLK)", copies[2])
        assertEquals(PhoneNumberType.DEMARCHAGE_COMMERCIAL.label, copies[3])
        assertNull(copies[4])
        assertNull(WhoCalled.answer(null, hasPermission = true).titleCopy)
    }

    @Test
    fun `without the permission or a missed call the tile says so`() {
        assertTrue(WhoCalled.answer(null, hasPermission = false).message.contains("Autorisez l'accès au journal d'appels"))
        assertEquals("Aucun appel manqué dans le journal.", WhoCalled.answer(null, hasPermission = true).message)
    }

    @Test
    fun `the subtitle is the operator, shortened to fit the tile`() {
        assertNull(WhoCalled.subtitle(null))
        assertEquals("Qwalikom", WhoCalled.subtitle(call("0162001122", ArcepOperator("QWLK", "Qwalikom"))))
        assertEquals(
            "Société Réunionnais…",
            WhoCalled.subtitle(call("0262000000", ArcepOperator("SRR", "Société Réunionnaise du Radiotéléphone")))
        )
    }
}
