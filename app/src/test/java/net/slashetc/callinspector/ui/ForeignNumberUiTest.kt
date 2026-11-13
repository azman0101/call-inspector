package net.slashetc.callinspector.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.ArcepOperator
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.ui.components.ArcepDossierContent
import net.slashetc.callinspector.ui.components.CallDetailBottomSheet
import net.slashetc.callinspector.util.ForeignNumbers
import net.slashetc.callinspector.util.PhoneNumberFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForeignNumberUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun lookup(raw: String, operator: ArcepOperator? = null): ArcepLookupResult {
        val normalized = PhoneNumberFormatter.normalize(raw)
        return ArcepLookupResult(
            queryNumber = raw,
            normalizedNumber = normalized,
            formattedNumber = PhoneNumberFormatter.format(raw),
            operator = operator,
            range = null,
            numberType = PhoneNumberType.classify(normalized),
            isFound = operator != null,
            foreign = ForeignNumbers.describe(normalized)
        )
    }

    private fun call(raw: String, type: CallType = CallType.MISSED, operator: ArcepOperator? = null): CallLogEntry {
        val lookup = lookup(raw, operator)
        return CallLogEntry(
            id = 1,
            rawNumber = raw,
            normalizedNumber = lookup.normalizedNumber,
            formattedNumber = lookup.formattedNumber,
            cachedName = null,
            timestamp = 1_790_000_000_000,
            durationSeconds = 0,
            callType = type,
            lookupResult = lookup
        )
    }

    private fun startedIntent(): Intent? =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity

    @Test
    fun `a foreign number shows its country and operator, not the ARCEP card`() {
        composeRule.setContent { ArcepDossierContent(lookup = lookup("+491734119637")) }

        composeRule.onNodeWithTag("foreign_number_card").assertExists()
        composeRule.onNodeWithTag("foreign_country", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("Allemagne").assertExists()
        composeRule.onNodeWithText("Vodafone").assertExists()
        composeRule.onNodeWithText("+49 173 4119637").assertExists()
        composeRule.onNodeWithTag("dossier_operator").assertDoesNotExist()
        composeRule.onNodeWithTag("verify_on_arcep_button").assertDoesNotExist()
    }

    @Test
    fun `the 33700 button opens the SMS app with the message ready, nothing is sent`() {
        composeRule.setContent { ArcepDossierContent(lookup = lookup("+491734119637")) }

        composeRule.onNodeWithTag("foreign_number_warning", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("report_33700_button").performScrollTo().performClick()

        val intent = startedIntent()!!
        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:33700", intent.dataString)
        assertEquals("spam vocal +491734119637", intent.getStringExtra("sms_body"))
    }

    @Test
    fun `a received call from abroad offers the 33700, not SignalConso nor J'alerte l'Arcep`() {
        composeRule.setContent {
            CallDetailBottomSheet(
                call = call("+491734119637"),
                onDismiss = {},
                onToggleSpam = {},
                onToggleFavorite = {},
                onSaveNote = { _, _ -> },
                onReport = {},
                onAlertArcep = {}
            )
        }

        composeRule.onNodeWithTag("report_33700_button").assertExists()
        composeRule.onNodeWithTag("report_signalconso_button").assertDoesNotExist()
        composeRule.onNodeWithTag("alert_arcep_button").assertDoesNotExist()
    }

    @Test
    fun `a French overseas number keeps its reports, a number the user called has no 33700`() {
        composeRule.setContent {
            CallDetailBottomSheet(
                call = call("+262692123456", operator = ArcepOperator("SRR", "Société réunionnaise du radiotéléphone")),
                onDismiss = {},
                onToggleSpam = {},
                onToggleFavorite = {},
                onSaveNote = { _, _ -> },
                onReport = {},
                onAlertArcep = {}
            )
        }

        composeRule.onNodeWithTag("report_signalconso_button").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("alert_arcep_button").assertExists()
        composeRule.onNodeWithTag("report_33700_button").assertDoesNotExist()
        composeRule.onNodeWithTag("foreign_number_card").assertDoesNotExist()
    }

    @Test
    fun `an outgoing call to a foreign number has no 33700`() {
        composeRule.setContent {
            CallDetailBottomSheet(
                call = call("+491734119637", type = CallType.OUTGOING),
                onDismiss = {},
                onToggleSpam = {},
                onToggleFavorite = {},
                onSaveNote = { _, _ -> }
            )
        }

        composeRule.onNodeWithTag("foreign_number_card").assertExists()
        composeRule.onNodeWithTag("report_33700_button").assertDoesNotExist()
    }
}
