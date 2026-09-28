package net.slashetc.callinspector.ui

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
}
