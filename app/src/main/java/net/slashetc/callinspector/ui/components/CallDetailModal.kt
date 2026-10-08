package net.slashetc.callinspector.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.ui.theme.ArcepBlue
import net.slashetc.callinspector.ui.theme.ArcepNavy
import net.slashetc.callinspector.ui.theme.DangerRed
import net.slashetc.callinspector.ui.theme.DangerRedSoft
import net.slashetc.callinspector.ui.theme.SuccessGreen
import net.slashetc.callinspector.ui.theme.WarningAmber
import net.slashetc.callinspector.ui.theme.WarningAmberSoft
import net.slashetc.callinspector.util.ArcepAlert
import net.slashetc.callinspector.util.SignalConsoReport

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallDetailBottomSheet(
    call: CallLogEntry,
    onDismiss: () -> Unit,
    onToggleSpam: (CallLogEntry) -> Unit,
    onToggleFavorite: (CallLogEntry) -> Unit,
    onSaveNote: (String, String?) -> Unit,
    onReport: ((CallLogEntry) -> Unit)? = null,
    /** The telemarketing calls from the same operator (see ArcepAlert.callsFromSameOperator). */
    operatorCalls: List<CallLogEntry> = emptyList(),
    onAlertArcep: ((List<CallLogEntry>) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val lookup = call.lookupResult

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        ArcepDossierContent(
            lookup = lookup,
            cachedName = call.cachedName,
            isSpam = call.isSpamFlagged,
            isFavorite = call.isFavorite,
            userNote = call.userNote,
            onToggleSpam = { onToggleSpam(call) },
            onToggleFavorite = { onToggleFavorite(call) },
            onSaveNote = { note -> onSaveNote(call.rawNumber, note) },
            onReport = onReport?.takeIf { SignalConsoReport.isReportable(call) }?.let { report -> { report(call) } },
            reportSummary = SignalConsoReport.reportSummary(call.reportCount, call.lastReportedAt),
            onAlertArcep = onAlertArcep?.takeIf { SignalConsoReport.isReportable(call) }?.let { alert -> { alert(listOf(call)) } },
            onAlertArcepOperator = onAlertArcep
                ?.takeIf { SignalConsoReport.isReportable(call) && operatorCalls.size > 1 }
                ?.let { alert -> { alert(operatorCalls) } },
            operatorCallCount = operatorCalls.size,
            alertSummary = ArcepAlert.alertSummary(call.arcepAlertCount, call.lastArcepAlertAt),
            onDismiss = onDismiss
        )
    }
}

@Composable
fun ArcepDossierContent(
    lookup: ArcepLookupResult,
    cachedName: String? = null,
    isSpam: Boolean = false,
    isFavorite: Boolean = false,
    userNote: String? = null,
    onToggleSpam: (() -> Unit)? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onSaveNote: ((String?) -> Unit)? = null,
    onReport: (() -> Unit)? = null,
    reportSummary: String? = null,
    onAlertArcep: (() -> Unit)? = null,
    onAlertArcepOperator: (() -> Unit)? = null,
    operatorCallCount: Int = 0,
    alertSummary: String? = null,
    onDismiss: (() -> Unit)? = null,
    isScrollable: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingNote by remember { mutableStateOf(userNote ?: "") }
    var isNoteExpanded by remember { mutableStateOf(!userNote.isNullOrBlank()) }
    var choosingAlert by remember { mutableStateOf(false) }

    if (choosingAlert && onAlertArcep != null && onAlertArcepOperator != null) {
        AlertDialog(
            onDismissRequest = { choosingAlert = false },
            title = { Text("Alerter l'Arcep") },
            text = {
                Text(
                    "Une alerte sur cet appel seulement, ou sur les $operatorCallCount appels de démarchage reçus " +
                        "de numéros de ${lookup.operatorDisplayName} ? Regrouper les appels montre à l'Arcep un " +
                        "opérateur dont les numéros servent souvent à démarcher.",
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { choosingAlert = false; onAlertArcepOperator() }) {
                    Text("Les $operatorCallCount appels")
                }
            },
            dismissButton = {
                TextButton(onClick = { choosingAlert = false; onAlertArcep() }) { Text("Cet appel") }
            }
        )
    }

    val scrollModifier = if (isScrollable) {
        Modifier.verticalScroll(rememberScrollState())
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(scrollModifier)
            .padding(horizontal = 20.dp)
            .padding(bottom = 32.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (!cachedName.isNullOrBlank()) {
                    Text(
                        text = cachedName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = lookup.formattedNumber,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row {
                onToggleFavorite?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.testTag("modal_favorite_button")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                            contentDescription = "Favori",
                            tint = if (isFavorite) WarningAmber else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                onToggleSpam?.let {
                    IconButton(
                        onClick = it,
                        modifier = Modifier.testTag("modal_spam_button")
                    ) {
                        Icon(
                            imageVector = if (isSpam) Icons.Default.Block else Icons.Outlined.Block,
                            contentDescription = "Indésirable",
                            tint = if (isSpam) DangerRed else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Status tags
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PhoneCategoryBadge(type = lookup.numberType)
            if (isSpam || lookup.numberType.isDemarchage) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = DangerRedSoft
                ) {
                    Text(
                        text = "Numéro Signalé",
                        color = DangerRed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
        }

        // Telemarketing Warning Banner if applicable
        if (lookup.numberType.isDemarchage) {
            Spacer(modifier = Modifier.height(14.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = WarningAmberSoft),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = WarningAmber,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Démarchage Commercial Réglementé",
                            fontWeight = FontWeight.Bold,
                            color = WarningAmber,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Ce numéro appartient à la plage légale imposée par l'ARCEP (Décision 2022-1583) pour les centres d'appels commerciaux et téléprospecteurs.",
                            fontSize = 12.sp,
                            color = Color(0xFF6D3800),
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ARCEP Official Identity Card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ArcepNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Business,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Opérateur titulaire de la tranche",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lookup.operatorDisplayName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(12.dp))

                // Detail Items Grid
                DossierRow(
                    label = "Code exploitant (CE)",
                    value = lookup.operatorCode
                )

                lookup.operator?.siret?.let { siret ->
                    if (siret.isNotBlank()) {
                        DossierRow(label = "SIRET de l'acteur", value = siret)
                    }
                }

                lookup.operator?.rcs?.let { rcs ->
                    if (rcs.isNotBlank()) {
                        DossierRow(label = "RCS", value = rcs)
                    }
                }

                lookup.operator?.address?.let { addr ->
                    if (addr.isNotBlank()) {
                        DossierRow(
                            label = "Siège social déclaré",
                            value = addr,
                            icon = Icons.Default.LocationOn
                        )
                    }
                }

                DossierRow(
                    label = "Plage de numérotation",
                    value = lookup.blockDisplay
                )

                DossierRow(
                    label = "Territoire",
                    value = lookup.territory
                )

                lookup.attributionDate?.let { date ->
                    if (date.isNotBlank()) {
                        DossierRow(label = "Date d'attribution", value = date)
                    }
                }

                lookup.operator?.declarationDate?.let { decDate ->
                    if (decDate.isNotBlank()) {
                        DossierRow(label = "Déclaration opérateur", value = decDate)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Official ARCEP Portal button & actions
        Button(
            onClick = {
                // Copy number to clipboard so user can easily search
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Numéro de téléphone", lookup.normalizedNumber)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Numéro copié ! Ouverture du site officiel...", Toast.LENGTH_SHORT).show()

                // Open official ARCEP webpage
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(lookup.officialArcepUrl))
                context.startActivity(intent)
            },
            colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("verify_on_arcep_button")
        ) {
            Icon(imageVector = Icons.Default.OpenInBrowser, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Consulter le portail officiel (arcep.fr)",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Détails ARCEP", "${lookup.formattedNumber} : Opérateur ${lookup.operatorDisplayName} (${lookup.operatorCode})")
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(context, "Informations copiées", Toast.LENGTH_SHORT).show()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Copier", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = {
                    val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${lookup.normalizedNumber}"))
                    context.startActivity(dialIntent)
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(imageVector = Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Appeler", fontSize = 13.sp)
            }
        }

        if (onReport != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onReport,
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("report_signalconso_button")
            ) {
                Icon(imageVector = Icons.Default.Warning, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Signaler ce démarchage (SignalConso)", fontWeight = FontWeight.SemiBold)
            }
            if (reportSummary != null) {
                Text(
                    text = reportSummary,
                    color = DangerRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("report_summary")
                )
            }
        }

        if (onAlertArcep != null) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { if (onAlertArcepOperator != null) choosingAlert = true else onAlertArcep() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("alert_arcep_button")
            ) {
                Icon(imageVector = Icons.Default.Campaign, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Alerter l'Arcep (J'alerte l'Arcep)", fontWeight = FontWeight.SemiBold)
            }
            if (alertSummary != null) {
                Text(
                    text = alertSummary,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .testTag("alert_summary")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Note section
        if (onSaveNote != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    .padding(14.dp)
            ) {
                Text(
                    text = "Note personnelle sur ce numéro",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editingNote,
                    onValueChange = { editingNote = it },
                    placeholder = { Text("Ex: Démarchage isolation, Ne pas répondre...", fontSize = 13.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = false,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        onSaveNote(editingNote.trim().ifEmpty { null })
                        Toast.makeText(context, "Note enregistrée", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Enregistrer")
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Regulatory Notice Note
        Text(
            text = "Information réglementaire : Cet outil identifie l'opérateur attributaire de la tranche initiale selon le plan national de numérotation. En cas de portabilité du numéro, l'abonné final peut être chez un autre opérateur, mais la responsabilité de routage initial incombe à l'opérateur mentionné ci-dessus.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun DossierRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.1f)
        )
        Row(
            modifier = Modifier.weight(1.3f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(3.dp))
            }
            Text(
                text = value,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.End
            )
        }
    }
}
