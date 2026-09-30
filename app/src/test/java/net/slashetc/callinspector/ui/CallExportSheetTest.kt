package net.slashetc.callinspector.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.ui.components.CallExportSheet
import net.slashetc.callinspector.util.PhoneNumberFormatter
import net.slashetc.callinspector.util.ReceivingLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallExportSheetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun call(id: Long, number: String, timestamp: Long) = CallLogEntry(
        id = id,
        rawNumber = number,
        normalizedNumber = number,
        formattedNumber = PhoneNumberFormatter.format(number),
        cachedName = null,
        timestamp = timestamp,
        durationSeconds = 0,
        callType = CallType.MISSED,
        lookupResult = ArcepLookupResult(
            queryNumber = number,
            normalizedNumber = number,
            formattedNumber = PhoneNumberFormatter.format(number),
            operator = ArcepOperator(code = "QWLK", name = "Qwalikom"),
            range = null,
            numberType = PhoneNumberType.classify(number),
            isFound = true
        )
    )

    private val calls = listOf(
        call(3, "0159391234", 1_790_000_300_000),
        call(2, "0159395678", 1_790_000_200_000),
        call(1, "0159391234", 1_790_000_100_000),
    )

    private fun oneLine(number: String?) = listOf(ReceivingLine("comp|1", "SIM 1", number, calls))

    private fun clipboardText(): String? =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            .primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun `every number starts selected and copying puts all their calls and the line number on the clipboard`() {
        var dismissed = false
        composeRule.setContent {
            CallExportSheet(oneLine("06 39 98 00 01"), "QWALIKOM", onSaveLineNumber = { _, _ -> }, onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithTag("export_line_number").assertTextContains("Ligne appelée : 06 39 98 00 01 (SIM 1)")
        composeRule.onNodeWithTag("export_line_0").assertDoesNotExist() // one line: nothing to choose
        composeRule.onNodeWithTag("export_copy_button").assertTextContains("Copier 3 appels").performClick()

        val text = clipboardText()!!
        assertTrue(text.contains("**Appels correspondant à « QWALIKOM »** : 3 appels de 2 numéros"))
        assertTrue(text.contains("Ligne ayant reçu les appels : 06 39 98 00 01"))
        assertEquals(3, text.lines().count { it.startsWith("- ") && it.contains(", 01 59 39") })
        assertTrue(dismissed)
    }

    @Test
    fun `unchecking a number leaves its calls out, and select all toggles every number`() {
        composeRule.setContent { CallExportSheet(oneLine("06 39 98 00 01"), "QWALIKOM", onSaveLineNumber = { _, _ -> }, onDismiss = {}) }

        composeRule.onNodeWithTag("export_list").performScrollToNode(hasTestTag("export_number_0159395678"))
        composeRule.onNodeWithTag("export_number_0159395678").performClick()
        composeRule.onNodeWithTag("export_copy_button").assertTextContains("Copier 2 appels")

        composeRule.onNodeWithTag("export_list").performScrollToNode(hasTestTag("export_select_all"))
        composeRule.onNodeWithTag("export_select_all").performClick() // partial selection -> all
        composeRule.onNodeWithTag("export_copy_button").assertTextContains("Copier 3 appels")

        composeRule.onNodeWithTag("export_select_all").performClick() // all -> none
        composeRule.onNodeWithTag("export_copy_button").assertIsNotEnabled()

        composeRule.onNodeWithTag("export_list").performScrollToNode(hasTestTag("export_number_0159391234"))
        composeRule.onNodeWithTag("export_number_0159391234").performClick()
        composeRule.onNodeWithTag("export_list").performScrollToNode(hasTestTag("export_format_CSV"))
        composeRule.onNodeWithTag("export_format_CSV").performClick()
        composeRule.onNodeWithTag("export_copy_button").assertIsEnabled().performClick()

        val csv = clipboardText()!!
        assertTrue(csv.startsWith("Numéro appelant,Date,Heure,Type,Durée (s),Opérateur,Ligne appelée\n"))
        assertEquals(3, csv.trimEnd().lines().size)
        assertFalse(csv.contains("01 59 39 56 78"))
        assertTrue(csv.trimEnd().lines().drop(1).all { it.endsWith(",06 39 98 00 01") })
    }

    @Test
    fun `with two lines only the chosen line's calls are exported, under its number`() {
        val lines = listOf(
            ReceivingLine("comp|1", "SIM 1", "06 39 98 00 01", calls.filter { it.normalizedNumber == "0159391234" }),
            ReceivingLine("comp|2", "SIM 2", "06 39 98 00 02", calls.filter { it.normalizedNumber == "0159395678" }),
        )
        composeRule.setContent { CallExportSheet(lines, "QWALIKOM", onSaveLineNumber = { _, _ -> }, onDismiss = {}) }

        composeRule.onNodeWithTag("export_line_0").assertTextContains("06 39 98 00 01 · 2 appels")
        composeRule.onNodeWithTag("export_line_1").assertTextContains("06 39 98 00 02 · 1 appel").performClick()
        composeRule.onNodeWithTag("export_line_number").assertTextContains("Ligne appelée : 06 39 98 00 02 (SIM 2)")
        composeRule.onNodeWithTag("export_number_0159391234").assertDoesNotExist()
        composeRule.onNodeWithTag("export_copy_button").assertTextContains("Copier 1 appel").performClick()

        val text = clipboardText()!!
        assertTrue(text.contains("Ligne ayant reçu les appels : 06 39 98 00 02"))
        assertFalse(text.contains("06 39 98 00 01"))
        // (The assistant instructions' example sentence mentions 01 59 39 12 34: check the call lines only.)
        assertEquals(listOf(", 01 59 39 56 78,"), text.lines().filter { it.startsWith("- ") }.map { Regex(", [0-9 ]+,").find(it)!!.value })
    }

    @Test
    fun `an unknown line number is asked for and saved, and a known one can be corrected`() {
        val saved = mutableListOf<Pair<String, String>>()
        composeRule.setContent { CallExportSheet(oneLine(null), "QWALIKOM", onSaveLineNumber = { k, n -> saved += k to n }, onDismiss = {}) }

        composeRule.onNodeWithTag("export_line_number").assertDoesNotExist()
        composeRule.onNodeWithTag("export_line_number_save").assertIsNotEnabled()
        composeRule.onNodeWithTag("export_line_number_input").performTextInput("06 39 98 00 01")
        composeRule.onNodeWithTag("export_line_number_save").assertIsEnabled().performClick()
        assertEquals(listOf("comp|1" to "06 39 98 00 01"), saved)

        // Without a number, the export still works but names no line.
        composeRule.onNodeWithTag("export_copy_button").performClick()
        assertFalse(clipboardText()!!.contains("Ligne ayant reçu"))
    }

    @Test
    fun `modifier reopens the number field prefilled with the known number`() {
        composeRule.setContent { CallExportSheet(oneLine("06 39 98 00 01"), "QWALIKOM", onSaveLineNumber = { _, _ -> }, onDismiss = {}) }

        composeRule.onNodeWithTag("export_line_number_edit").performClick()
        composeRule.onNodeWithTag("export_line_number_input").assertTextContains("06 39 98 00 01")
    }
}
