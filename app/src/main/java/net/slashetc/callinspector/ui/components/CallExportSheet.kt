package net.slashetc.callinspector.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.ui.theme.ArcepBlue
import net.slashetc.callinspector.util.CallExport
import net.slashetc.callinspector.util.CallExportFormat
import net.slashetc.callinspector.util.PhoneNumberFormatter

/**
 * Picks caller numbers from a search result (e.g. every number of one operator) and copies their calls
 * to the clipboard as CSV or Markdown, to answer an operator that asks for the calls to look for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallExportSheet(
    calls: List<CallLogEntry>,
    searchLabel: String,
    initialReceivingNumber: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val numbers = remember(calls) { CallExport.numbersIn(calls) }
    var selected by remember(numbers) { mutableStateOf(numbers.map { it.normalizedNumber }.toSet()) }
    var format by remember { mutableStateOf(CallExportFormat.MARKDOWN) }
    var includeNotes by remember { mutableStateOf(false) }
    var receivingNumber by remember(initialReceivingNumber) { mutableStateOf(initialReceivingNumber) }

    val selectedCalls = CallExport.selectedCalls(calls, selected)
    val hasNotes = calls.any { !it.userNote.isNullOrBlank() }
    val exportText = CallExport.format(
        selectedCalls,
        CallExport.Options(format, includeNotes && hasNotes, receivingNumber, searchLabel)
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Exporter les appels",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Cochez les numéros dont les appels (date et heure) seront copiés, par exemple pour répondre à l'opérateur qui les demande.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            val allState = when (selected.size) {
                0 -> ToggleableState.Off
                numbers.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            // Everything but the copy button scrolls, so the button stays reachable on small screens.
            LazyColumn(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .testTag("export_list")
            ) {
                item(key = "select_all") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (allState == ToggleableState.On) emptySet() else numbers.map { it.normalizedNumber }.toSet()
                            }
                            .padding(vertical = 2.dp)
                            .testTag("export_select_all")
                    ) {
                        TriStateCheckbox(state = allState, onClick = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Tout sélectionner (${numbers.size} numéro${if (numbers.size > 1) "s" else ""})",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                    HorizontalDivider()
                }

                items(numbers, key = { it.normalizedNumber }) { number ->
                    val checked = number.normalizedNumber in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - number.normalizedNumber else selected + number.normalizedNumber
                            }
                            .padding(vertical = 2.dp)
                            .testTag("export_number_${number.normalizedNumber}")
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = number.formattedNumber, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "${number.calls.size} appel${if (number.calls.size > 1) "s" else ""} · " +
                                    "dernier ${PhoneNumberFormatter.formatTimestamp(number.calls.maxOf { it.timestamp })} · ${number.operatorName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }

                item(key = "options") {
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf(CallExportFormat.MARKDOWN to "Markdown", CallExportFormat.CSV to "CSV").forEach { (value, label) ->
                            FilterChip(
                                selected = format == value,
                                onClick = { format = value },
                                label = { Text(label, fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ArcepBlue,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("export_format_${value.name}")
                            )
                        }
                    }

                    if (hasNotes) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { includeNotes = !includeNotes }
                                .testTag("export_include_notes")
                        ) {
                            Checkbox(checked = includeNotes, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Inclure mes notes", fontSize = 13.sp)
                        }
                    }

                    OutlinedTextField(
                        value = receivingNumber,
                        onValueChange = { receivingNumber = it },
                        label = { Text("Mon numéro, qui a reçu les appels (optionnel)", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .testTag("export_receiving_number")
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = exportText.lines().take(8).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            maxLines = 8,
                            modifier = Modifier
                                .padding(8.dp)
                                .testTag("export_preview")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {
                    copyToClipboard(context, "Appels $searchLabel", exportText)
                    // Android 13+ confirms the copy itself.
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(
                            context,
                            "${selectedCalls.size} appel${if (selectedCalls.size > 1) "s" else ""} copié${if (selectedCalls.size > 1) "s" else ""}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    onDismiss()
                },
                enabled = selectedCalls.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("export_copy_button")
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Copier ${selectedCalls.size} appel${if (selectedCalls.size > 1) "s" else ""}",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

// Phone numbers and notes: marked sensitive so Android 13+ keeps them out of the clipboard preview.
private fun copyToClipboard(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text).apply {
        description.extras = PersistableBundle().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            } else {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
}
