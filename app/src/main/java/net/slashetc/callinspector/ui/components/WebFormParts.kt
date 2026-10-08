package net.slashetc.callinspector.ui.components

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import net.slashetc.callinspector.data.db.ReporterProfile

// Shared by the two web forms the app prefills: SignalConso and J'alerte l'Arcep.

/** One short note above the form, closable. */
@Composable
internal fun FormNote(text: String, highlighted: Boolean, onClose: () -> Unit) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(16.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Masquer", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Whether the soft keyboard is open, from the window insets (works with and without edge-to-edge). */
@Composable
internal fun rememberKeyboardOpen(): Boolean {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            open = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return open
}

@Composable
internal fun ReporterProfileDialog(
    initial: ReporterProfile,
    canClear: Boolean,
    onSave: (ReporterProfile) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(initial.firstName) }
    var lastName by remember { mutableStateOf(initial.lastName) }
    var email by remember { mutableStateOf(initial.email) }
    var phone by remember { mutableStateOf(initial.phone) }
    var referenceNumber by remember { mutableStateOf(initial.referenceNumber) }
    var shareContact by remember { mutableStateOf(initial.shareContact) }
    var postalCode by remember { mutableStateOf(initial.postalCode) }
    var city by remember { mutableStateOf(initial.city) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mes coordonnées") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Préremplies dans SignalConso et J'alerte l'Arcep. Elles sont enregistrées uniquement sur ce " +
                        "téléphone (ni sauvegarde, ni transfert) et n'en sortent que dans un signalement que vous validez.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Prénom") }, singleLine = true)
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(
                    email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("Téléphone (facultatif)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    referenceNumber, { referenceNumber = it }, label = { Text("Numéro de référence (facultatif)") },
                    placeholder = { Text("ex : ZYX987654321") }, singleLine = true
                )
                Text("Votre commune (J'alerte l'Arcep)", fontSize = 13.sp)
                OutlinedTextField(
                    postalCode, { postalCode = it }, label = { Text("Code postal") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    city, { city = it }, label = { Text("Commune (si plusieurs pour ce code)") }, singleLine = true
                )
                Text("Partager vos coordonnées avec l'entreprise ? (SignalConso)", fontSize = 13.sp)
                listOf(null to "Ne pas préremplir", true to "Je partage", false to "Je ne partage pas").forEach { (value, text) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = shareContact == value, onClick = { shareContact = value }, role = Role.RadioButton)
                    ) {
                        RadioButton(selected = shareContact == value, onClick = null)
                        Text(text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ReporterProfile(firstName, lastName, email, phone, referenceNumber, shareContact, postalCode, city))
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (canClear) TextButton(onClick = onClear) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}
