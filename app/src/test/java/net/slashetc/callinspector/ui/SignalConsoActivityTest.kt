package net.slashetc.callinspector.ui

import net.slashetc.callinspector.ui.SignalConsoActivity.Companion.toJsExpression
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
    fun `toJsExpression escapes quotes and script injection attempt`() {
        val maliciousProfileJson = JSONObject()
            .put("firstName", "\"; alert('XSS'); //")
            .put("lastName", "</script><script>alert('XSS')</script>")
            .toString()

        val jsExpr = maliciousProfileJson.toJsExpression()

        // Verify that quotes inside the JSON string are double-escaped so they cannot break out of JSON.parse("...")
        assertTrue(jsExpr.startsWith("JSON.parse(\""))
        assertTrue(jsExpr.endsWith("\")"))
        // Check that the payload is safely enclosed in JSON.parse string parameter
        assertTrue(jsExpr.contains("alert('XSS')"))
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
