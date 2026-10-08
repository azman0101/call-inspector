package net.slashetc.callinspector.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepNumberRange
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.ui.ArcepAlertActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArcepAlertTest {

    private val paris = TimeZone.getTimeZone("Europe/Paris")
    private val list = ArcepAlertActivity.jalerteOperators(ApplicationProvider.getApplicationContext<Context>())

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(paris).apply {
            clear()
            set(2026, Calendar.OCTOBER, day, hour, minute)
        }.timeInMillis

    private val kavel = ArcepOperator(code = "KAVL", name = "Kav El International", siret = "12345678900011")
    private val orange = ArcepOperator(code = "FRTE", name = "Orange")

    private fun call(
        id: Long,
        number: String,
        timestamp: Long = at(2, 12, 16),
        operator: ArcepOperator? = kavel,
        type: CallType = CallType.MISSED,
        spam: Boolean = false,
        note: String? = null,
        withRange: Boolean = false,
    ) = CallLogEntry(
        id = id,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = PhoneNumberFormatter.format(number),
        cachedName = null,
        timestamp = timestamp,
        durationSeconds = 0L,
        callType = type,
        lookupResult = ArcepLookupResult(
            queryNumber = number,
            normalizedNumber = number,
            formattedNumber = PhoneNumberFormatter.format(number),
            operator = operator,
            range = if (withRange && operator != null) {
                ArcepNumberRange(ezabpqm = number.take(6), trancheDebut = number.take(6) + "0000", trancheFin = number.take(6) + "9999",
                    operatorCode = operator.code, operatorName = operator.name)
            } else {
                null
            },
            numberType = PhoneNumberType.classify(number),
            isFound = operator != null
        ),
        isSpamFlagged = spam,
        userNote = note
    )

    // --- The form's address ---

    @Test
    fun `only the alert form over HTTPS is the form`() {
        assertTrue(ArcepAlert.isFormUrl(ArcepAlert.URL))
        assertTrue(ArcepAlert.isFormUrl("https://jalerte.arcep.fr/jalerte/?3"))
        assertTrue(ArcepAlert.isFormUrl("https://jalerte.arcep.fr/jalerte?0"))
        listOf(
            null,
            "",
            "http://jalerte.arcep.fr/jalerte/?0",
            "https://jalerte.arcep.fr/",
            "https://jalerte.arcep.fr/cgu/",
            "https://jalerte.arcep.fr/jalerte/autre",
            "https://jalerte.arcep.fr/jalerte%2F..%2Fcgu/",
            "https://jalerte.arcep.fr:8443/jalerte/",
            "https://jalerte.arcep.fr.example.com/jalerte/",
            "https://jalerte.arcep.fr@example.com/jalerte/",
            "https://www.arcep.fr/jalerte/",
        ).forEach { assertFalse(it.toString(), ArcepAlert.isFormUrl(it)) }
    }

    @Test
    fun `the sent message is looked for on any HTTPS page of J'alerte l'Arcep`() {
        assertTrue(ArcepAlert.isJalerteUrl("https://jalerte.arcep.fr/jalerte/?7"))
        assertTrue(ArcepAlert.isJalerteUrl("https://jalerte.arcep.fr/confirmation"))
        assertFalse(ArcepAlert.isJalerteUrl("http://jalerte.arcep.fr/jalerte/?7"))
        assertFalse(ArcepAlert.isJalerteUrl("https://jalerte.arcep.fr.example.com/"))
        assertTrue(ArcepAlert.SENT_QUERY.contains("'votre alerte a été soumise'"))
        // The rendered text only: innerText leaves out the page's scripts.
        assertTrue(ArcepAlert.SENT_QUERY.contains("b.innerText"))
    }

    @Test
    fun `the alert summary counts the alerts and dates the last one`() {
        assertNull(ArcepAlert.alertSummary(0, null))
        assertNull(ArcepAlert.alertSummary(2, null))
        assertEquals("Alerté à l'Arcep 2 fois · dernière le 08/10/2026", ArcepAlert.alertSummary(2, at(8, 18), paris))
    }

    // --- Step 2: the kind of number ---

    @Test
    fun `each kind of number gets its checkbox`() {
        assertEquals(ArcepAlert.TYPE_CALL_CENTER, ArcepAlert.numberType(call(1, "0162000000")))
        assertEquals(ArcepAlert.TYPE_MOBILE, ArcepAlert.numberType(call(1, "0612345678")))
        assertEquals(ArcepAlert.TYPE_FIXED, ArcepAlert.numberType(call(1, "0145000000")))
        assertEquals(ArcepAlert.TYPE_FIXED, ArcepAlert.numberType(call(1, "0970000000")))
        assertEquals(ArcepAlert.TYPE_FOREIGN, ArcepAlert.numberType(call(1, "+4420700000")))
        assertEquals(ArcepAlert.TYPE_HIDDEN, ArcepAlert.numberType(call(1, "", operator = null)))
        assertNull(ArcepAlert.numberType(call(1, "0890000000")))
    }

    // --- The calls an operator alert covers ---

    @Test
    fun `an operator alert gathers the telemarketing calls from the same operator`() {
        val selected = call(1, "0612345678", operator = orange, spam = true)
        val history = listOf(
            call(2, "0612345678", timestamp = at(1, 10), operator = orange),            // same number: kept
            call(3, "0699999999", timestamp = at(1, 11), operator = orange),            // a contact on Orange: not swept in
            call(4, "0699999998", timestamp = at(1, 12), operator = orange, spam = true), // flagged as spam: kept
            call(5, "0162000000", timestamp = at(1, 13), operator = orange),            // telemarketing range: kept
            call(6, "0162000001", timestamp = at(1, 14), operator = kavel),             // another operator
            call(7, "0162000002", timestamp = at(1, 15), operator = orange, type = CallType.OUTGOING),
        )
        assertEquals(listOf(2L, 4L, 5L, 1L), ArcepAlert.callsFromSameOperator(selected, history).map { it.id })
    }

    @Test
    fun `a number without operator only alerts on its own call`() {
        val selected = call(1, "", operator = null)
        assertEquals(listOf(1L), ArcepAlert.callsFromSameOperator(selected, listOf(call(2, "", operator = null))).map { it.id })
    }

    // --- The plan ---

    @Test
    fun `one call - number, date, range, operator with code and SIRET, note`() {
        val plan = ArcepAlert.buildPlan(listOf(call(1, "0162000000", note = "Isolation à 1 euro", withRange = true)), list, paris)
        assertEquals(listOf(ArcepAlert.TYPE_CALL_CENTER), plan.numberTypes)
        assertEquals("Kav El International", plan.operatorName)
        assertEquals("Kavokom / Kav El International", plan.jalerteOperator)
        assertEquals(1, plan.callCount)
        val text = plan.description
        assertTrue(text, text.startsWith("Appel de démarchage commercial non sollicité reçu le 02/10/2026 à 12h16 depuis le 01 62 00 00 00."))
        assertTrue(text, text.contains("tranche 0162000000 à 0162009999, attribuée par l'Arcep à Kav El International (code ARCEP KAVL, SIRET 12345678900011)"))
        assertTrue(text, text.contains("je signale l'opérateur qui le lui fournit"))
        assertTrue(text, text.contains("Ma note : Isolation à 1 euro"))
    }

    @Test
    fun `one call without known range names the operator only`() {
        val text = ArcepAlert.buildPlan(listOf(call(1, "0162000000")), list, paris).description
        assertTrue(text, text.contains("Ce numéro est attribué par l'Arcep à Kav El International"))
        assertFalse(text, text.contains("Tranche non déterminée"))
    }

    @Test
    fun `several calls are listed per number, most frequent first`() {
        val calls = listOf(
            call(1, "0162000000", timestamp = at(1, 10)),
            call(2, "0270000000", timestamp = at(1, 11)),
            call(3, "0270000000", timestamp = at(2, 9, 5)),
            call(4, "0612345678", timestamp = at(3, 18, 30), spam = true),
        )
        val plan = ArcepAlert.buildPlan(calls, list, paris)
        assertEquals(listOf(ArcepAlert.TYPE_CALL_CENTER, ArcepAlert.TYPE_MOBILE), plan.numberTypes)
        assertEquals(4, plan.callCount)
        val lines = plan.description.lines()
        assertEquals(
            "4 appels de démarchage commercial non sollicités reçus du 01/10/2026 au 03/10/2026 depuis 3 numéros " +
                "attribués par l'Arcep à Kav El International (code ARCEP KAVL, SIRET 12345678900011) :",
            lines[0]
        )
        assertEquals("- 02 70 00 00 00 : 2 appels (01/10/2026 à 11h00, 02/10/2026 à 09h05)", lines[1])
        assertTrue(lines.contains("- 01 62 00 00 00 : 1 appel (01/10/2026 à 10h00)"))
        assertTrue(lines.contains("- 06 12 34 56 78 : 1 appel (03/10/2026 à 18h30)"))
    }

    @Test
    fun `an operator missing from the list goes into Autre`() {
        val plan = ArcepAlert.buildPlan(listOf(call(1, "0162000000", operator = ArcepOperator("XXXX", "Opérateur Inconnu Télécom"))), list, paris)
        assertEquals("Opérateur Inconnu Télécom", plan.operatorName)
        assertNull(plan.jalerteOperator)
    }

    // --- The operator in J'alerte l'Arcep's list ---

    @Test
    fun `the bundled list is J'alerte l'Arcep's`() {
        assertTrue(list.size > 500)
        assertTrue("Orange" in list)
        assertTrue("Kavokom / Kav El International" in list)
    }

    @Test
    fun `the operator is found under its name, its brand or its other name`() {
        assertEquals("Orange", ArcepAlert.matchOperator("Orange", list))
        assertEquals("SFR", ArcepAlert.matchOperator("Société française du radiotéléphone", list))
        assertEquals("SFR", ArcepAlert.matchOperator("SFR fibre SAS", list))
        assertEquals("Kavokom / Kav El International", ArcepAlert.matchOperator("Kav El International", list))
        assertEquals("Canal Plus Telecom / Canalbox", ArcepAlert.matchOperator("Canal+ Telecom", list))
        // "Completel · SFR": what follows "·" is the host network.
        assertEquals("Completel · SFR", ArcepAlert.matchOperator("Completel", list))
    }

    @Test
    fun `an entry named exactly so wins over one that also goes by that name`() {
        // The list has both "EINOVA / UNIXO" (earlier) and "Unixo".
        assertEquals("Unixo", ArcepAlert.matchOperator("Unixo", list))
        assertEquals("EINOVA / UNIXO", ArcepAlert.matchOperator("Einova", list))
        // Known under one name by two entries, named by neither: no guess.
        assertNull(ArcepAlert.matchOperator("Unixo", listOf("EINOVA / UNIXO", "Autre nom / Unixo")))
    }

    @Test
    fun `a longer ARCEP name is matched by the one entry that starts it`() {
        assertEquals("Legos", ArcepAlert.matchOperator("Legos-Local exchange global operation services", list))
        assertEquals("Koesio", ArcepAlert.matchOperator("Koesio networks", list))
        assertEquals("TATA COMMUNICATIONS", ArcepAlert.matchOperator("Tata communications (France)", list))
    }

    @Test
    fun `no guess on short or ambiguous names`() {
        // "Free" (4 letters) is too short to stand for "Free dial".
        assertNull(ArcepAlert.matchOperator("Free dial", list))
        // Two entries start with "Sewan": neither is picked.
        assertNull(ArcepAlert.matchOperator("Sewan", list))
        assertNull(ArcepAlert.matchOperator("", list))
    }

    @Test
    fun `names are compared without accents, case, punctuation or legal form`() {
        assertEquals("kav el international", ArcepAlert.normalizeName("Kav-El International SAS"))
        assertEquals("canal plus telecom", ArcepAlert.normalizeName("Canal+ Télécom"))
        assertEquals(setOf("bt blue", "bretagne telecom", "bt blue ex bretagne telecom"), ArcepAlert.entryNames("BT Blue (ex Bretagne Telecom)"))
        assertEquals(setOf("sosh"), ArcepAlert.entryNames("Sosh · Orange"))
    }
}
