package net.slashetc.callinspector.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.db.ReporterProfile
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.Note
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.PROFILE_CHANGED_SCRIPT
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.PageState
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.communeDelivery
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.contactDelivery
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.hasJalerteContact
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.operatorNote
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.parsePageState
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.prefillInjection
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.toCommuneJson
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.toContactJson
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.visibleNotes
import net.slashetc.callinspector.util.toJsExpression
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ArcepAlertActivityTest {

    private val script = ApplicationProvider.getApplicationContext<Context>()
        .assets.open("jalerte_prefill.js").bufferedReader().use { it.readText() }

    // --- What reaches the page (as for SignalConso, SR-01) ---

    @Test
    fun `the prefill script keeps the user's data out of page globals`() {
        assertTrue(script.trimEnd().endsWith("})"))
        listOf("window.__iaPlan", "window.__iaContact", "window.__iaCommune").forEach {
            assertFalse("$it in the script", script.contains(it))
        }
    }

    @Test
    fun `the injection calls the script with the plan only`() {
        val plan = JSONObject().put("description", "Appel de démarchage").toString()
        assertEquals(script.trimEnd() + "(" + plan.toJsExpression() + ");", prefillInjection(script, plan))
    }

    @Test
    fun `contact details and commune go through the script's entry points, null drops them`() {
        val contact = JSONObject().put("email", "camille@example.invalid").toString()
        assertEquals("if (window.__iaFillContact) window.__iaFillContact(${contact.toJsExpression()});", contactDelivery(contact))
        val commune = JSONObject().put("postalCode", "75011").toString()
        assertEquals("if (window.__iaFillCommune) window.__iaFillCommune(${commune.toJsExpression()});", communeDelivery(commune))
        assertEquals("if (window.__iaFillCommune) window.__iaFillCommune(JSON.parse(\"null\"));", communeDelivery("null"))
    }

    @Test
    fun `a profile change only drops what the page holds, it hands nothing over`() {
        assertEquals(
            "if (window.__iaFillContact) window.__iaFillContact(JSON.parse(\"null\"));" +
                "if (window.__iaFillCommune) window.__iaFillCommune(JSON.parse(\"null\"));",
            PROFILE_CHANGED_SCRIPT
        )
    }

    @Test
    fun `the profile gives J'alerte l'Arcep its contact fields and the commune separately`() {
        val profile = ReporterProfile("Camille", "Test", "camille@example.invalid", "0611223344", "REF", true, "69003", "Lyon")
        val contact = JSONObject(profile.toContactJson())
        assertEquals("camille@example.invalid", contact.getString("email"))
        assertEquals("Test", contact.getString("lastName"))
        assertEquals("Camille", contact.getString("firstName"))
        assertEquals("0611223344", contact.getString("phone"))
        // The share choice also answers the third-party question; SignalConso's reference number is its own.
        assertTrue(contact.getBoolean("shareContact"))
        assertFalse(contact.has("referenceNumber"))
        val commune = JSONObject(profile.toCommuneJson())
        assertEquals("69003", commune.getString("postalCode"))
        assertEquals("Lyon", commune.getString("city"))
    }

    @Test
    fun `the share choice alone is handed over, and is not contact details to save`() {
        val refused = ReporterProfile(shareContact = false)
        assertFalse(JSONObject(refused.toContactJson()).getBoolean("shareContact"))
        assertFalse(refused.hasJalerteContact())
        assertTrue(JSONObject(ReporterProfile(email = "camille@example.invalid").toContactJson()).isNull("shareContact"))
        assertTrue(ReporterProfile(email = "camille@example.invalid").hasJalerteContact())
    }

    @Test
    fun `nothing is handed over without the details`() {
        assertEquals("null", (null as ReporterProfile?).toContactJson())
        assertEquals("null", ReporterProfile(postalCode = "75011").toContactJson())
        assertEquals("null", ReporterProfile(email = "camille@example.invalid").toCommuneJson())
        assertEquals("null", ReporterProfile(city = "Lyon").toCommuneJson())
    }

    @Test
    fun `the page state is read from flags only`() {
        assertEquals(PageState(3, needsContact = false, needsCommune = true), parsePageState("{\"step\":3,\"needsContact\":false,\"needsCommune\":true}"))
        assertEquals(PageState(5, needsContact = true, needsCommune = false), parsePageState("{\"step\":5,\"needsContact\":true,\"needsCommune\":false}"))
        assertEquals(PageState(0, needsContact = false, needsCommune = false), parsePageState("null"))
        assertEquals(PageState(0, needsContact = false, needsCommune = false), parsePageState(null))
    }

    // --- Notes above the form ---

    private fun notes(
        step: Int,
        keyboardOpen: Boolean = false,
        hasOperator: Boolean = false,
        hasPostalCode: Boolean = true,
        hasContact: Boolean = true,
        closed: Set<Note> = emptySet(),
    ) = visibleNotes(step, keyboardOpen, hasOperator, hasPostalCode, hasContact, closed)

    @Test
    fun `each note shows at its step`() {
        assertEquals(listOf(Note.PREFILLED), notes(1))
        assertEquals(listOf(Note.PREFILLED), notes(2))
        assertEquals(emptyList<Note>(), notes(3))
        assertEquals(listOf(Note.OPERATOR, Note.NO_POSTAL_CODE), notes(3, hasOperator = true, hasPostalCode = false))
        assertEquals(listOf(Note.OPERATOR), notes(3, hasOperator = true))
        assertEquals(emptyList<Note>(), notes(4))
        assertEquals(listOf(Note.PROFILE), notes(5, hasContact = false))
        assertEquals(emptyList<Note>(), notes(5))
    }

    @Test
    fun `no note while typing, and a closed note stays closed`() {
        assertEquals(emptyList<Note>(), notes(1, keyboardOpen = true))
        assertEquals(emptyList<Note>(), notes(3, keyboardOpen = true, hasPostalCode = false))
        assertEquals(listOf(Note.NO_POSTAL_CODE), notes(3, hasOperator = true, hasPostalCode = false, closed = setOf(Note.OPERATOR)))
    }

    @Test
    fun `step 3 recalls the operator the ARCEP assigned the number to, and how the list names it`() {
        assertEquals("Opérateur attribué par l'Arcep : Unixo.", operatorNote("Unixo", "Unixo"))
        assertEquals("Opérateur attribué par l'Arcep : ORANGE SA.", operatorNote("ORANGE SA", "Orange"))
        assertEquals(
            "Opérateur attribué par l'Arcep : Kav El International, « Kavokom / Kav El International » dans la liste.",
            operatorNote("Kav El International", "Kavokom / Kav El International")
        )
        assertEquals(
            "Opérateur attribué par l'Arcep : Opérateur Inconnu. Absent de la liste : saisi dans « Autre ».",
            operatorNote("Opérateur Inconnu", null)
        )
    }
}
