package net.slashetc.callinspector.ui

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.ui.components.ArcepDossierContent
import net.slashetc.callinspector.ui.components.CallItemCard
import net.slashetc.callinspector.util.PhoneNumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CopyOnLongPressTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val clipboard = ApplicationProvider.getApplicationContext<Context>()
        .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val copied: String? get() = clipboard.primaryClip?.getItemAt(0)?.text?.toString()

    private val copiedIsSensitive: Boolean
        get() = clipboard.primaryClip?.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true

    private val lookup = ArcepLookupResult(
        queryNumber = "0162001122",
        normalizedNumber = "0162001122",
        formattedNumber = PhoneNumberFormatter.format("0162001122"),
        operator = ArcepOperator(code = "QWLK", name = "Qwalikom", siret = "81234567800019"),
        range = null,
        numberType = PhoneNumberType.classify("0162001122"),
        isFound = true
    )

    private val call = CallLogEntry(
        id = 1,
        rawNumber = "0162001122",
        normalizedNumber = "0162001122",
        formattedNumber = PhoneNumberFormatter.format("0162001122"),
        cachedName = null,
        timestamp = 1_790_000_000_000,
        durationSeconds = 0,
        callType = CallType.MISSED,
        lookupResult = lookup,
        userNote = "Isolation à 1 €, rappel refusé"
    )

    @Test
    fun `a long press on the number copies it, and a tap still opens the call`() {
        var opened = 0
        composeRule.setContent { CallItemCard(call = call, onClick = { opened++ }, onToggleFavorite = {}) }
        // The card merges its texts for accessibility: their own nodes are in the unmerged tree.

        composeRule.onNodeWithTag("call_number_0162001122", useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals("01 62 00 11 22", copied)
        assertTrue(copiedIsSensitive)
        assertEquals("the long press is not a tap on the card", 0, opened)

        composeRule.onNodeWithTag("call_number_0162001122", useUnmergedTree = true).performClick()
        assertEquals(1, opened)
    }

    @Test
    fun `the note is copied without its label, and the time in full`() {
        composeRule.setContent { CallItemCard(call = call, onClick = {}, onToggleFavorite = {}) }

        composeRule.onNodeWithTag("call_note_0162001122", useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals("Isolation à 1 €, rappel refusé", copied)

        composeRule.onNodeWithTag("call_time_0162001122", useUnmergedTree = true).performTouchInput { longClick() }
        assertEquals(PhoneNumberFormatter.formatFullTimestamp(call.timestamp), copied)
    }

    @Test
    fun `TalkBack gets one copy action per value of the card it reads as one node`() {
        composeRule.setContent { CallItemCard(call = call, onClick = {}, onToggleFavorite = {}) }

        val actions = composeRule.onNodeWithTag("call_item_0162001122").fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
        val labels = actions.map { it.label }
        assertTrue(labels.toString(), "Copier : 01 62 00 11 22" in labels)
        assertTrue(labels.toString(), "Copier : Qwalikom (QWLK)" in labels)
        assertTrue(labels.toString(), labels.any { it.startsWith("Copier : Isolation") })

        composeRule.runOnIdle { actions.first { it.label.startsWith("Copier : Isolation") }.action() }
        assertEquals("Isolation à 1 €, rappel refusé", copied)
    }

    @Test
    fun `a call sheet value is copied without its label, public data is not sensitive`() {
        composeRule.setContent { ArcepDossierContent(lookup = lookup) }

        composeRule.onNodeWithTag("dossier_value_SIRET de l'acteur").performTouchInput { longClick() }
        assertEquals("81234567800019", copied)
        assertFalse(copiedIsSensitive)

        composeRule.onNodeWithTag("dossier_operator").performTouchInput { longClick() }
        assertEquals("Qwalikom", copied)

        composeRule.onNodeWithTag("dossier_number").performTouchInput { longClick() }
        assertEquals("01 62 00 11 22", copied)
        assertTrue(copiedIsSensitive)
    }

    @Test
    fun `a swipe across a value copies nothing`() {
        composeRule.setContent { ArcepDossierContent(lookup = lookup) }

        composeRule.onNodeWithTag("dossier_value_SIRET de l'acteur").performTouchInput { swipeUp() }
        assertNull(copied)
    }
}
