package net.slashetc.callinspector.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import net.slashetc.callinspector.ui.components.AppUpdateBanner
import net.slashetc.callinspector.ui.components.ReleaseNotesSheet
import net.slashetc.callinspector.util.AppRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val release = AppRelease(
        versionCode = 180,
        versionName = "1.0.180",
        pageUrl = "https://github.com/azman0101/call-inspector/releases/tag/v1.0.180",
        apkUrl = "https://github.com/azman0101/call-inspector/releases/download/v1.0.180/info-operateur-1.0.180.apk",
        notes = """
            ## Vérifier

            - SHA-256 de l'APK : `abc`

            ## What's Changed
            * feat: update check by @azman0101 in https://github.com/azman0101/call-inspector/pull/53
            * fix: something else by @azman0101 in https://github.com/azman0101/call-inspector/pull/54

            **Full Changelog**: https://github.com/azman0101/call-inspector/compare/v1.0.174...v1.0.180
        """.trimIndent(),
    )

    private fun lastStartedUrl(): String? =
        shadowOf(ApplicationProvider.getApplicationContext<Application>()).nextStartedActivity
            ?.takeIf { it.action == Intent.ACTION_VIEW }?.dataString

    @Test
    fun `the banner names the version, downloads its apk and can be dismissed`() {
        var notesShown = false
        var dismissed = false
        composeRule.setContent {
            AppUpdateBanner(release, onShowNotes = { notesShown = true }, onDismiss = { dismissed = true })
        }

        composeRule.onNodeWithTag("app_update_banner").assertExists()
        composeRule.onNodeWithText("Version 1.0.180 disponible").assertExists()
        composeRule.onNodeWithTag("app_update_download").performClick()
        assertEquals(release.apkUrl, lastStartedUrl())
        composeRule.onNodeWithTag("app_update_notes").performClick()
        composeRule.onNodeWithTag("app_update_dismiss").performClick()
        assertTrue(notesShown)
        assertTrue(dismissed)
    }

    @Test
    fun `the notes sheet lists the pull requests only, by kind, and offers the newer apk`() {
        composeRule.setContent { ReleaseNotesSheet(release, offerDownload = true, onDismiss = {}) }

        composeRule.onNodeWithText("Nouveautés de la version 1.0.180").assertExists()
        composeRule.onAllNodesWithTag("release_change").assertCountEquals(2)
        composeRule.onNodeWithTag("release_group_feature").assertExists()
        composeRule.onNodeWithTag("release_group_fix").assertExists()
        composeRule.onNodeWithTag("release_group_other").assertDoesNotExist()
        composeRule.onNodeWithText("Update check (#53)").assertExists()
        composeRule.onNodeWithText("Something else (#54)").assertExists()
        composeRule.onNodeWithTag("release_notes_since").assertDoesNotExist()
        composeRule.onNodeWithText("SHA-256", substring = true).assertDoesNotExist()
        composeRule.onNodeWithTag("release_notes_download").performClick()
        assertEquals(release.apkUrl, lastStartedUrl())
    }

    @Test
    fun `the notes of several releases say which version they follow`() {
        val cumulated = release.copy(changes = listOf("feat: first (#52)") + release.changes, changesSince = "1.0.174")
        composeRule.setContent { ReleaseNotesSheet(cumulated, offerDownload = true, onDismiss = {}) }

        composeRule.onNodeWithText("Changements de toutes les versions publiées depuis la 1.0.174").assertExists()
        composeRule.onAllNodesWithTag("release_change").assertCountEquals(3)
        composeRule.onNodeWithText("First (#52)").assertExists()
    }

    @Test
    fun `the installed version's notes offer no download`() {
        composeRule.setContent { ReleaseNotesSheet(release, offerDownload = false, onDismiss = {}) }

        composeRule.onNodeWithText("Quoi de neuf dans la 1.0.180").assertExists()
        composeRule.onNodeWithTag("release_notes_download").assertDoesNotExist()
    }
}
