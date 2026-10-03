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
import java.util.Calendar
import java.util.TimeZone

class SignalConsoReportTest {

    private val paris = TimeZone.getTimeZone("Europe/Paris")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(paris).apply {
            clear()
            set(year, month - 1, day, hour, minute)
        }.timeInMillis

    private fun call(
        id: Long,
        timestamp: Long,
        number: String = "0162000000",
        type: CallType = CallType.MISSED,
        note: String? = null,
        cachedName: String? = null,
        operator: ArcepOperator? = null,
    ) = CallLogEntry(
        id = id,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = PhoneNumberFormatter.format(number),
        cachedName = cachedName,
        timestamp = timestamp,
        durationSeconds = 0L,
        callType = type,
        lookupResult = ArcepLookupResult(
            queryNumber = number,
            normalizedNumber = number,
            formattedNumber = PhoneNumberFormatter.format(number),
            operator = operator,
            range = null,
            numberType = PhoneNumberType.classify(number),
            isFound = operator != null
        ),
        userNote = note
    )

    // 2026-09-22 is a Tuesday, 2026-09-26 a Saturday, 2026-07-14 a public holiday (Tuesday).
    private val now = at(2026, 9, 28, 12)

    @Test
    fun `weekday call within allowed hours matches no case`() {
        val c = call(1, at(2026, 9, 22, 11))
        assertTrue(SignalConsoReport.applicableCases(c, emptyList(), now, paris).isEmpty())
        val plan = SignalConsoReport.buildPlan(c, emptyList(), now, paris)
        // Nothing in the call itself: default reason, not claimed in the description.
        assertEquals(DemarchageCase.REFUSED_WITHIN_60_DAYS.label, plan.subcategory)
        assertTrue(plan.isDefaultReason)
        assertFalse(plan.description.contains("refusé"))
        assertEquals(listOf("2026-09-22"), plan.dates)
        assertEquals("0162000000", plan.phone)
    }

    @Test
    fun `weekday call outside allowed hours`() {
        for (hour in listOf(9, 13, 20)) {
            val cases = SignalConsoReport.applicableCases(call(1, at(2026, 9, 22, hour, 30)), emptyList(), now, paris)
            assertEquals("hour $hour", listOf(DemarchageCase.OUTSIDE_WEEKDAY_HOURS), cases)
        }
        for (hour in listOf(10, 12, 14, 19)) {
            assertTrue("hour $hour", SignalConsoReport.applicableCases(call(1, at(2026, 9, 22, hour, 30)), emptyList(), now, paris).isEmpty())
        }
    }

    @Test
    fun `weekend and public holiday calls`() {
        assertEquals(
            listOf(DemarchageCase.WEEKEND_OR_HOLIDAY),
            SignalConsoReport.applicableCases(call(1, at(2026, 9, 26, 11)), emptyList(), now, paris)
        )
        assertEquals(
            listOf(DemarchageCase.WEEKEND_OR_HOLIDAY),
            SignalConsoReport.applicableCases(call(1, at(2026, 7, 14, 11)), emptyList(), at(2026, 7, 20, 12), paris)
        )
    }

    @Test
    fun `easter based holidays`() {
        val easter2026 = SignalConsoReport.easterSunday(2026, paris)
        assertEquals(Calendar.APRIL, easter2026.get(Calendar.MONTH))
        assertEquals(5, easter2026.get(Calendar.DAY_OF_MONTH))
        for ((month, day) in listOf(4 to 6, 5 to 14, 5 to 25)) {
            val cal = Calendar.getInstance(paris).apply { clear(); set(2026, month - 1, day, 12, 0) }
            assertTrue("$month/$day", SignalConsoReport.isFrenchPublicHoliday(cal))
        }
        val ordinary = Calendar.getInstance(paris).apply { clear(); set(2026, Calendar.SEPTEMBER, 22, 12, 0) }
        assertFalse(SignalConsoReport.isFrenchPublicHoliday(ordinary))
    }

    @Test
    fun `five calls from the same number in 30 days prefill the five latest dates`() {
        val history = listOf(
            call(1, at(2026, 8, 20, 11)), // older than 30 days: ignored
            call(2, at(2026, 9, 1, 11)),
            call(3, at(2026, 9, 3, 11)),
            call(4, at(2026, 9, 8, 11)),
            call(5, at(2026, 9, 15, 11)),
            call(6, at(2026, 9, 18, 11), type = CallType.OUTGOING), // our own call: ignored
            call(7, at(2026, 9, 20, 11), number = "0612345678"), // another number: ignored
        )
        val selected = call(8, at(2026, 9, 22, 11))
        val plan = SignalConsoReport.buildPlan(selected, history, now, paris)
        assertEquals(DemarchageCase.REPEATED_CALLS.label, plan.subcategory)
        assertEquals(listOf("2026-09-01", "2026-09-03", "2026-09-08", "2026-09-15", "2026-09-22"), plan.dates)
        assertTrue(plan.description.contains("5 appels reçus de ce numéro"))
    }

    @Test
    fun `four calls are not enough for the repeated-calls case`() {
        val history = listOf(call(2, at(2026, 9, 1, 11)), call(3, at(2026, 9, 3, 11)), call(4, at(2026, 9, 8, 11)))
        val cases = SignalConsoReport.applicableCases(call(5, at(2026, 9, 22, 11)), history, now, paris)
        assertFalse(DemarchageCase.REPEATED_CALLS in cases)
    }

    @Test
    fun `mobile numbers and non French numbers`() {
        val mobile = call(1, at(2026, 9, 22, 11), number = "0612345678")
        assertEquals(listOf(DemarchageCase.MOBILE_NUMBER), SignalConsoReport.applicableCases(mobile, emptyList(), now, paris))
        val foreign = call(2, at(2026, 9, 22, 11), number = "+442071234567")
        assertNull(SignalConsoReport.buildPlan(foreign, emptyList(), now, paris).phone)
    }

    @Test
    fun `outgoing calls are not reportable`() {
        assertFalse(SignalConsoReport.isReportable(call(1, now, type = CallType.OUTGOING)))
        assertTrue(SignalConsoReport.isReportable(call(1, now, type = CallType.INCOMING)))
    }

    @Test
    fun `description includes call time, number and the user's note`() {
        val plan = SignalConsoReport.buildPlan(call(1, at(2026, 9, 26, 9, 5), note = "Isolation à 1 euro"), emptyList(), now, paris)
        assertTrue(plan.description, plan.description.startsWith("Appel de démarchage reçu le 26/09/2026 à 09h05 depuis le 01 62 00 00 00."))
        assertTrue(plan.description.contains("samedi, un dimanche ou un jour férié"))
        assertTrue(plan.description.endsWith("Ma note : Isolation à 1 euro"))
    }

    @Test
    fun `operator holding the number is reported by SIRET when the caller is unknown`() {
        val operator = ArcepOperator(code = "VOIP", name = "Voip Telecom", siret = "833 508 617 00010")
        val plan = SignalConsoReport.buildPlan(call(1, at(2026, 9, 22, 11), operator = operator), emptyList(), now, paris)
        assertEquals(ReportedCompany(ReportedCompany.Source.OPERATOR, "Voip Telecom", "83350861700010"), plan.company)
        assertTrue(plan.description, plan.description.contains("je signale l'opérateur qui lui fournit ce numéro"))
    }

    @Test
    fun `operator without a valid SIRET is searched by name`() {
        val operator = ArcepOperator(code = "VOIP", name = "Voip Telecom", siret = "RCS Paris")
        val company = SignalConsoReport.reportedCompany(call(1, now, operator = operator))
        assertEquals(ReportedCompany(ReportedCompany.Source.OPERATOR, "Voip Telecom", null), company)
    }

    @Test
    fun `caller name from the call log wins over the operator`() {
        val operator = ArcepOperator(code = "VOIP", name = "Voip Telecom", siret = "83350861700010")
        val plan = SignalConsoReport.buildPlan(call(1, now, cachedName = " Isolation Machin ", operator = operator), emptyList(), now, paris)
        assertEquals(ReportedCompany(ReportedCompany.Source.CALLER_NAME, "Isolation Machin", null), plan.company)
        assertFalse(plan.description.contains("je signale l'opérateur"))
    }

    @Test
    fun `no company when neither the caller nor the operator is known`() {
        assertNull(SignalConsoReport.reportedCompany(call(1, now)))
    }

    @Test
    fun `subject of the call is detected from the user's note`() {
        fun casesFor(note: String) = SignalConsoReport.applicableCases(call(1, at(2026, 9, 22, 11), note = note), emptyList(), now, paris)
        assertEquals(listOf(DemarchageCase.RENOVATION), casesFor("Isolation à 1 euro, pompe à chaleur"))
        assertEquals(listOf(DemarchageCase.CPF), casesFor("Formation financée par mon CPF"))
        assertEquals(listOf(DemarchageCase.ADMINISTRATION), casesFor("Se fait passer pour la CAF"))
        assertEquals(listOf(DemarchageCase.REFUSED_WITHIN_60_DAYS), casesFor("J'ai déjà refusé le mois dernier"))
        assertTrue(casesFor("Café offert, voix enregistrée").isEmpty())
    }

    @Test
    fun `subject comes before time based cases`() {
        val plan = SignalConsoReport.buildPlan(call(1, at(2026, 9, 26, 9, 5), note = "Panneaux solaires"), emptyList(), now, paris)
        assertEquals(DemarchageCase.RENOVATION.label, plan.subcategory)
        assertTrue(plan.description, plan.description.contains("travaux ou de la rénovation énergétique"))
        assertEquals(
            listOf(DemarchageCase.RENOVATION, DemarchageCase.WEEKEND_OR_HOLIDAY),
            SignalConsoReport.applicableCases(call(1, at(2026, 9, 26, 9, 5), note = "Panneaux solaires"), emptyList(), now, paris)
        )
    }

    @Test
    fun `report summary shows the count and the last report date`() {
        assertNull(SignalConsoReport.reportSummary(0, null, paris))
        assertEquals("Signalé 1 fois · dernier le 26/09/2026", SignalConsoReport.reportSummary(1, at(2026, 9, 26, 23, 30), paris))
        assertEquals("Signalé 3 fois · dernier le 22/09/2026", SignalConsoReport.reportSummary(3, at(2026, 9, 22, 11), paris))
    }

    // --- Where the app hands the user's data (security review, SR-01) ---

    @Test
    fun `the form URL the app opens is the form`() {
        assertTrue(SignalConsoReport.isFormUrl(SignalConsoReport.URL))
        assertTrue(SignalConsoReport.isFormUrl(SignalConsoReport.URL + "?step=2#top"))
        assertTrue(SignalConsoReport.isFormUrl(SignalConsoReport.URL + "/etape-4"))
    }

    @Test
    fun `no other page, scheme or host is the form`() {
        listOf(
            null,
            "",
            "pas une url",
            "http://signal.conso.gouv.fr/fr/demarchage-abusif/faire-un-signalement",
            "https://signal.conso.gouv.fr/fr/mes-signalements",
            "https://signal.conso.gouv.fr/fr/demarchage-abusif",
            "https://signal.conso.gouv.fr/fr/demarchage-abusif/faire-un-signalement-bis",
            "https://signal.conso.gouv.fr/fr/demarchage-abusif/faire-un-signalement/../../autre-page",
            "https://signal.conso.gouv.fr:8443/fr/demarchage-abusif/faire-un-signalement",
            "https://signal.conso.gouv.fr.example.com/fr/demarchage-abusif/faire-un-signalement",
            "https://entreprise.signal.conso.gouv.fr/fr/demarchage-abusif/faire-un-signalement",
            "https://signal.conso.gouv.fr@example.com/fr/demarchage-abusif/faire-un-signalement",
            "https://example.com/fr/demarchage-abusif/faire-un-signalement?u=https://signal.conso.gouv.fr",
            "file:///android_asset/signalconso_prefill.js",
            "javascript:alert(1)",
        ).forEach { url -> assertFalse(url.toString(), SignalConsoReport.isFormUrl(url)) }
    }

    @Test
    fun `any HTTPS page of SignalConso may be asked whether a report was sent`() {
        assertTrue(SignalConsoReport.isSignalConsoUrl("https://signal.conso.gouv.fr/fr/merci"))
        assertFalse(SignalConsoReport.isSignalConsoUrl("http://signal.conso.gouv.fr/fr/merci"))
        assertFalse(SignalConsoReport.isSignalConsoUrl("https://signal.conso.gouv.fr.example.com/"))
    }
}
