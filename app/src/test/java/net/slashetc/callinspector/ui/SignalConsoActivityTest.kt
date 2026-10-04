package net.slashetc.callinspector.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.ui.SignalConsoActivity.Companion.PageState
import net.slashetc.callinspector.ui.SignalConsoActivity.Companion.contactDelivery
import net.slashetc.callinspector.ui.SignalConsoActivity.Companion.parsePageState
import net.slashetc.callinspector.ui.SignalConsoActivity.Companion.prefillInjection
import net.slashetc.callinspector.ui.SignalConsoActivity.Companion.toJsExpression
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SignalConsoActivityTest {

    @Test
    fun `toJsExpression wraps null json string correctly`() {
        val contactJson = "null"
        val jsExpr = contactJson.toJsExpression()
        assertEquals("JSON.parse(\"null\")", jsExpr)
    }

    @Test
    fun `toJsExpression keeps a breakout attempt inside the JSON_parse string argument`() {
        val maliciousProfileJson = JSONObject()
            .put("firstName", "\"; alert('XSS'); //")
            .put("lastName", "</script><script>alert('XSS')</script>")
            .put("email", "\\\"); alert('XSS'); (\"")
            .toString()

        val jsExpr = maliciousProfileJson.toJsExpression()

        assertTrue(jsExpr.startsWith("JSON.parse(\""))
        assertTrue(jsExpr.endsWith("\")"))
        val argument = jsExpr.removePrefix("JSON.parse(").removeSuffix(")")
        // Every quote between the delimiters is escaped, so none can close the string literal early.
        val inner = argument.substring(1, argument.length - 1)
        assertFalse(Regex("""(?<!\\)(\\\\)*"""").containsMatchIn(inner))
        // The argument is one string literal that decodes back to exactly the original JSON.
        assertEquals(maliciousProfileJson, JSONTokener(argument).nextValue())
    }

    @Test
    fun `toJsExpression handles unicode line terminators safely`() {
        val jsonWithLineTerminator = JSONObject()
            .put("note", "Line1\u2028Line2\u2029Line3")
            .toString()

        val jsExpr = jsonWithLineTerminator.toJsExpression()

        // Verify that \u2028 and \u2029 are escaped as \u2028 and \u2029 strings to prevent JS syntax error
        assertTrue(jsExpr.contains("\\u2028"))
        assertTrue(jsExpr.contains("\\u2029"))
    }

    // --- What reaches the page (security review, SR-01) ---

    private val script = ApplicationProvider.getApplicationContext<Context>()
        .assets.open("signalconso_prefill.js").bufferedReader().use { it.readText() }

    @Test
    fun `the prefill script keeps the user's data out of page globals`() {
        // It is a function expression the app calls with the plan, not a script reading window variables.
        assertTrue(script.trimEnd().endsWith("})"))
        listOf("window.__icPlan", "window.__icContact", "__icPrefillRun", "__icContactFilled").forEach {
            assertFalse("$it in the script", script.contains(it))
        }
    }

    @Test
    fun `the injection calls the script with the plan, and holds no contact details`() {
        val plan = JSONObject().put("phone", "0612345678").put("description", "Appel insistant").toString()

        val injection = prefillInjection(script, plan)

        // The script, then its call with the plan as argument: nothing assigned to window, no contact details.
        assertEquals(script.trimEnd() + "(" + plan.toJsExpression() + ");", injection)
    }

    @Test
    fun `contact details go through the script's entry point, and null drops them`() {
        val contact = JSONObject().put("email", "jean@example.org").toString()

        assertEquals("if (window.__icFillContact) window.__icFillContact(${contact.toJsExpression()});", contactDelivery(contact))
        assertEquals("if (window.__icFillContact) window.__icFillContact(JSON.parse(\"null\"));", contactDelivery("null"))
    }

    @Test
    fun `the page state is read from flags only`() {
        assertEquals(PageState(sent = true, needsContact = false), parsePageState("{\"sent\":true,\"needsContact\":false}"))
        assertEquals(PageState(sent = false, needsContact = true), parsePageState("{\"sent\":false,\"needsContact\":true}"))
        // A page without the script answers null; an error is no state either.
        assertEquals(PageState(sent = false, needsContact = false), parsePageState("null"))
        assertEquals(PageState(sent = false, needsContact = false), parsePageState(null))
    }
}
