package net.slashetc.callinspector.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.DialogProperties

/**
 * Shows the terms of use. With [requireAcceptance] the dialog can't be dismissed: the user accepts
 * ([onAccept]) or leaves the app ([onDismiss]); otherwise it is a plain reading view.
 */
@Composable
fun LegalTermsDialog(
    markdown: String,
    requireAcceptance: Boolean,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!requireAcceptance) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !requireAcceptance,
            dismissOnClickOutside = !requireAcceptance
        ),
        text = {
            MarkdownDocument(
                markdown = markdown,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("legal_terms_text")
            )
        },
        confirmButton = {
            if (requireAcceptance) {
                Button(onClick = onAccept, modifier = Modifier.testTag("legal_terms_accept")) { Text("J'accepte") }
            } else {
                Button(onClick = onDismiss) { Text("Fermer") }
            }
        },
        dismissButton = if (requireAcceptance) {
            { TextButton(onClick = onDismiss) { Text("Quitter l'application") } }
        } else {
            null
        }
    )
}
