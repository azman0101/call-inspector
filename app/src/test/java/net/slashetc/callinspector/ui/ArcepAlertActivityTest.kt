package net.slashetc.callinspector.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.db.ReporterProfile
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.Note
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.PageState
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.communeDelivery
import net.slashetc.callinspector.ui.ArcepAlertActivity.Companion.contactDelivery
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
    fun `the profile gives J'alerte l'Arcep its contact fields and the commune separately`() {
        val profile = ReporterProfile("Camille", "Test", "camille@example.invalid", "0611223344", "REF", true, "69003", "Lyon")
        val contact = JSONObject(profile.toContactJson())
        assertEquals("camille@example.invalid", contact.getString("email"))
        assertEquals("Test", contact.getString("lastName"))
        assertEquals("Camille", contact.getString("firstName"))
        assertEquals("0611223344", contact.getString("phone"))
        // SignalConso's reference number and share choice are not J'alerte l'Arcep's business.
        assertFalse(contact.has("referenceNumber"))
        assertFalse(contact.has("shareContact"))
        val commune = JSONObject(profile.toCommuneJson())
        assertEquals("69003", commune.getString("postalCode"))
        assertEquals("Lyon", commune.getString("city"))
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
        operatorTyped: Boolean = false,
        hasPostalCode: Boolean = true,
        hasContact: Boolean = true,
        closed: Set<Note> = emptySet(),
    ) = visibleNotes(step, keyboardOpen, operatorTyped, hasPostalCode, hasContact, closed)

    @Test
    fun `each note shows at its step`() {
        assertEquals(listOf(Note.PREFILLED), notes(1))
        assertEquals(listOf(Note.PREFILLED), notes(2))
        assertEquals(emptyList<Note>(), notes(3))
        assertEquals(listOf(Note.OPERATOR_TYPED, Note.NO_POSTAL_CODE), notes(3, operatorTyped = true, hasPostalCode = false))
        assertEquals(emptyList<Note>(), notes(4))
        assertEquals(listOf(Note.PROFILE), notes(5, hasContact = false))
        assertEquals(emptyList<Note>(), notes(5))
    }

    @Test
    fun `no note while typing, and a closed note stays closed`() {
        assertEquals(emptyList<Note>(), notes(1, keyboardOpen = true))
        assertEquals(emptyList<Note>(), notes(3, keyboardOpen = true, hasPostalCode = false))
        assertEquals(listOf(Note.NO_POSTAL_CODE), notes(3, operatorTyped = true, hasPostalCode = false, closed = setOf(Note.OPERATOR_TYPED)))
    }
}
