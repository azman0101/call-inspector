package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneLinesTest {

    private val sim1 = "com.android.phone/com.android.services.telephony.TelephonyConnectionService|1"
    private val sim2 = "com.android.phone/com.android.services.telephony.TelephonyConnectionService|2"

    private fun call(id: Long, lineId: String?, viaNumber: String? = null, number: String = "0162000000") = CallLogEntry(
        id = id,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = PhoneNumberFormatter.format(number),
        cachedName = null,
        timestamp = id * 1000,
        durationSeconds = 0,
        callType = CallType.MISSED,
        lookupResult = ArcepLookupResult(number, number, PhoneNumberFormatter.format(number), null, null, PhoneNumberType.classify(number), false),
        lineId = lineId,
        viaNumber = viaNumber
    )

    @Test
    fun `lineId joins the phone account component and id, and needs an id`() {
        assertEquals("comp/Svc|2", PhoneLines.lineId("comp/Svc", "2"))
        assertEquals("|89330", PhoneLines.lineId(null, "89330"))
        assertNull(PhoneLines.lineId("comp/Svc", ""))
        assertNull(PhoneLines.lineId("comp/Svc", null))
        assertEquals("comp/Svc" to "2", PhoneLines.accountOf("comp/Svc|2"))
        assertNull(PhoneLines.accountOf("no-separator"))
    }

    @Test
    fun `calls are grouped by line, the busiest line first, with stable fallback names`() {
        val calls = listOf(call(5, sim2), call(4, sim1), call(3, sim2), call(2, null), call(1, sim2))

        val lines = PhoneLines.linesOf(calls, stored = emptyMap(), detected = emptyMap())

        assertEquals(listOf(sim2, sim1, PhoneLines.UNKNOWN_LINE), lines.map { it.key })
        assertEquals(listOf(3, 1, 1), lines.map { it.calls.size })
        // Named by sorted line id, not by call count.
        assertEquals(listOf("Ligne 2", "Ligne 1", "Ligne non identifiée"), lines.map { it.label })
        assertEquals(listOf(null, null, null), lines.map { it.number })
    }

    @Test
    fun `a saved number wins over the call log's, which wins over the system's`() {
        val calls = listOf(call(3, sim1, viaNumber = "+33639980001"), call(2, sim2, viaNumber = "0639980002"), call(1, sim2))
        val detected = mapOf(
            sim1 to DetectedLine(label = "SIM 1 · Free", number = "0600000001"),
            sim2 to DetectedLine(label = "SIM 2", number = "0600000002"),
        )

        val byKey = PhoneLines.linesOf(calls, stored = emptyMap(), detected = detected).associateBy { it.key }
        assertEquals("06 39 98 00 01", byKey.getValue(sim1).number)
        assertEquals("06 39 98 00 02", byKey.getValue(sim2).number)
        assertEquals("SIM 1 · Free", byKey.getValue(sim1).label)

        val corrected = PhoneLines.linesOf(calls, stored = mapOf(sim1 to "07 11 22 33 44"), detected = detected).associateBy { it.key }
        assertEquals("07 11 22 33 44", corrected.getValue(sim1).number)

        val systemOnly = PhoneLines.linesOf(listOf(call(1, sim1)), stored = emptyMap(), detected = detected)
        assertEquals("06 00 00 00 01", systemOnly.single().number)
    }

    @Test
    fun `calls without a phone account form one line whose number can be saved too`() {
        val lines = PhoneLines.linesOf(
            listOf(call(2, null), call(1, null)),
            stored = mapOf(PhoneLines.UNKNOWN_LINE to "0612345678"),
            detected = emptyMap()
        )

        assertEquals(PhoneLines.UNKNOWN_LINE, lines.single().key)
        assertEquals("06 12 34 56 78", lines.single().number)
    }
}
