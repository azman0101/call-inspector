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
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.ui.components.CallExportSheet
import net.slashetc.callinspector.util.PhoneNumberFormatter
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

    private fun clipboardText(): String? =
        ApplicationProvider.getApplicationContext<Context>().getSystemService(ClipboardManager::class.java)
            .primaryClip?.getItemAt(0)?.text?.toString()

    @Test
    fun `every number starts selected and copying puts all their calls on the clipboard`() {
        var dismissed = false
        composeRule.setContent {
            CallExportSheet(calls, "QWALIKOM", "06 12 34 56 78", onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithTag("export_copy_button").assertTextContains("Copier 3 appels").performClick()

        val text = clipboardText()!!
        assertTrue(text.contains("**Appels correspondant à « QWALIKOM »** : 3 appels de 2 numéros"))
        assertTrue(text.contains("Ligne ayant reçu les appels : 06 12 34 56 78"))
        assertEquals(3, text.lines().count { it.startsWith("| 01 59 39") })
        assertTrue(dismissed)
    }

    @Test
    fun `unchecking a number leaves its calls out, and select all toggles every number`() {
        composeRule.setContent { CallExportSheet(calls, "QWALIKOM", "", onDismiss = {}) }

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
        assertTrue(csv.startsWith("Numéro appelant,Date,Heure,Type,Durée (s),Opérateur\n"))
        assertEquals(3, csv.trimEnd().lines().size)
        assertFalse(csv.contains("01 59 39 56 78"))
        assertFalse(csv.contains("Ligne appelée"))
    }
}
