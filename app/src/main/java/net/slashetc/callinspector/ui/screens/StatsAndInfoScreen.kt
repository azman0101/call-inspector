package net.slashetc.callinspector.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.sentry.Sentry
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import net.slashetc.callinspector.ui.components.LegalTermsDialog
import net.slashetc.callinspector.ui.components.ProvideCopyActions
import net.slashetc.callinspector.ui.components.copyActions
import net.slashetc.callinspector.ui.components.copyOnLongPress
import net.slashetc.callinspector.ui.components.rememberCopyActions
import net.slashetc.callinspector.util.LegalTerms
import net.slashetc.callinspector.util.SentryHelper
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.slashetc.callinspector.data.repository.UpdateStatus
import net.slashetc.callinspector.ui.theme.ArcepBlue
import net.slashetc.callinspector.ui.theme.ArcepNavy
import net.slashetc.callinspector.ui.theme.DangerRed
import net.slashetc.callinspector.ui.theme.SuccessGreen
import net.slashetc.callinspector.ui.theme.WarningAmber
import net.slashetc.callinspector.viewmodel.ArcepUiState

@Composable
fun StatsAndInfoScreen(
    uiState: ArcepUiState,
    onTriggerUpdate: (() -> Unit)? = null,
    onResetUpdateStatus: (() -> Unit)? = null,
    onSetUpdateCheckEnabled: ((Boolean) -> Unit)? = null,
    onSetUpdateCheckEnabledInDebug: ((Boolean) -> Unit)? = null,
    onCheckAppUpdateNow: (() -> Unit)? = null,
    onShowInstalledReleaseNotes: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val stats = uiState.databaseStats
    val calls = uiState.calls
    val updateStatus = uiState.updateStatus

    var devModeEnabled by remember { mutableStateOf(SentryHelper.isDevModeEnabled(context)) }
    var tapCount by remember { mutableIntStateOf(0) }
    var isToastEnabled by remember { mutableStateOf(SentryHelper.isToastEnabled(context)) }
    var isTelemetryEnabled by remember { mutableStateOf(SentryHelper.isTelemetryEnabled(context)) }
    var showCguDialog by remember { mutableStateOf(false) }

    val totalCalls = calls.size
    val demarchageCalls = calls.count { it.isSpamFlagged || it.lookupResult.numberType.isDemarchage }
    val missedCalls = calls.count { it.callType.name.contains("MISSED") || it.callType.name.contains("REJECTED") }

    // Operator breakdown in user call history
    val operatorCounts = calls
        .map { it.lookupResult.operatorDisplayName }
        .filter { it.isNotBlank() && it != "Opérateur non identifié" }
        .groupingBy { it }
        .eachCount()
        .toList()
        .sortedByDescending { it.second }
        .take(5)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Screen Header
        Surface(
            color = ArcepNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = "Observatoire & Cadre Légal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Statistiques d'appels & Cadre réglementaire",
                    fontSize = 12.sp,
                    color = Color(0xFFBACEDB)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mention légale / Disclaimer de non-affiliation
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Avertissement : Info Opérateur est une application tierce indépendante. Elle n'est ni affiliée, ni éditée, ni approuvée par l'ARCEP. Les attributions de blocs proviennent des publications ouvertes du plan de numérotation.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )
                }
            }

            // ARCEP Database Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F1FC)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = ArcepBlue, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Base nationale de numérotation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Plan officiel français en données ouvertes",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatMetricCard(
                            label = "Blocs attribués",
                            value = "${stats?.totalRanges ?: 20655}",
                            color = ArcepBlue,
                            modifier = Modifier.weight(1f)
                        )
                        StatMetricCard(
                            label = "Opérateurs déclarés",
                            value = "${stats?.totalOperators ?: 1592}",
                            color = SuccessGreen,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Source : Extranet ARCEP & data.gouv.fr. Base complète intégrée localement pour une consultation instantanée et confidentielle sans envoyer vos numéros à des tiers.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 15.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Traçabilité de la version
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Version officielle :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val versionDate = stats?.databaseVersionDate ?: "Tue, 15 Sep 2026"
                            Text(
                                text = versionDate,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.copyOnLongPress(versionDate)
                            )
                        }

                        stats?.generatedAt?.let { genDate ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Génération locale :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(genDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.copyOnLongPress(genDate))
                            }
                        }

                        stats?.latestAttributionDate?.let { attrDate ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Dernière décision ARCEP :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(attrDate, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.copyOnLongPress(attrDate))
                            }
                        }

                        stats?.majnumChecksum?.let { sha ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Empreinte SHA-256 :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // The whole fingerprint, though only its start is shown.
                                Text(
                                    "${sha.take(16)}...",
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.copyOnLongPress(sha).testTag("stats_sha256")
                                )
                            }
                        }

                        // Ligne Version de l'application cliquable (activation du mode développeur par 7 taps)
                        val copyActions = rememberCopyActions()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (devModeEnabled) {
                                        Toast.makeText(context, "Mode développeur déjà actif !", Toast.LENGTH_SHORT).show()
                                    } else {
                                        tapCount++
                                        if (tapCount in 3..6) {
                                            val remaining = 7 - tapCount
                                            Toast.makeText(context, "Plus que $remaining étape${if (remaining > 1) "s" else ""} pour activer le mode développeur", Toast.LENGTH_SHORT).show()
                                        } else if (tapCount >= 7) {
                                            devModeEnabled = true
                                            SentryHelper.setDevModeEnabled(context, true)
                                            Toast.makeText(context, "🚀 Mode développeur activé !", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                                .copyActions(copyActions)
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // TalkBack reads the row as one node: it offers the copy action of the version.
                            ProvideCopyActions(copyActions) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Version de l'application :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (devModeEnabled) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "DEV",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                val appVersion = "${net.slashetc.callinspector.BuildConfig.VERSION_NAME} (build ${net.slashetc.callinspector.BuildConfig.VERSION_CODE})"
                                // A tap still counts towards the developer mode.
                                Text(
                                    text = appVersion,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.copyOnLongPress(appVersion)
                                )
                            }
                        }

                        // Versions published on GitHub (tools/publish_release.sh)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (onShowInstalledReleaseNotes != null) {
                                TextButton(
                                    onClick = onShowInstalledReleaseNotes,
                                    modifier = Modifier.testTag("installed_release_notes_button")
                                ) {
                                    Text("Nouveautés de cette version", fontSize = 11.sp)
                                }
                            }
                            if (onCheckAppUpdateNow != null && uiState.isUpdateCheckEnabled) {
                                TextButton(
                                    onClick = onCheckAppUpdateNow,
                                    modifier = Modifier.testTag("check_app_update_button")
                                ) {
                                    Text("Rechercher une mise à jour", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            // OTA Update Card
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE0F4FF)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CloudDownload,
                                    contentDescription = null,
                                    tint = ArcepBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Mise à jour en direct (OTA)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Télécharger les données ouvertes de numérotation",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    when (updateStatus) {
                        is UpdateStatus.Idle -> {
                            Text(
                                text = "Télécharge MAJNUM.csv et identifiants_CE.csv directement depuis l'extranet de l'ARCEP, recompile la base SQLite locale et conserve vos notes et favoris.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onTriggerUpdate?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("ota_update_button")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Vérifier et actualiser la base locale", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        is UpdateStatus.Checking -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ArcepBlue)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(updateStatus.message, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }

                        is UpdateStatus.Downloading -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(updateStatus.step, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("${(updateStatus.progress * 100).toInt()}%", fontSize = 12.sp, color = ArcepBlue)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { updateStatus.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = ArcepBlue
                                )
                            }
                        }

                        is UpdateStatus.Processing -> {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(updateStatus.step, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Text("${(updateStatus.progress * 100).toInt()}%", fontSize = 12.sp, color = ArcepBlue)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { updateStatus.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = SuccessGreen
                                )
                            }
                        }

                        is UpdateStatus.Success -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(updateStatus.message, fontWeight = FontWeight.Bold, color = SuccessGreen, fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val summary = "${updateStatus.rangesCount} tranches et ${updateStatus.operatorsCount} opérateurs compilés avec succès (${updateStatus.date})."
                                    Text(
                                        text = summary,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1B5E20),
                                        modifier = Modifier.copyOnLongPress(summary)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextButton(
                                        onClick = { onResetUpdateStatus?.invoke() },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Text("Fermer", fontSize = 11.sp, color = SuccessGreen)
                                    }
                                }
                            }
                        }

                        is UpdateStatus.Error -> {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFFEBEE),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Échec de la mise à jour", fontWeight = FontWeight.Bold, color = DangerRed, fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        updateStatus.errorMessage,
                                        fontSize = 11.sp,
                                        color = Color(0xFFB71C1C),
                                        modifier = Modifier.copyOnLongPress(updateStatus.errorMessage)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                        TextButton(onClick = { onResetUpdateStatus?.invoke() }) {
                                            Text("Fermer", fontSize = 11.sp)
                                        }
                                        Button(
                                            onClick = { onTriggerUpdate?.invoke() },
                                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Réessayer", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // User Calls Statistics Card
            if (totalCalls > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFFF3E0)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Assessment, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(20.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Analyse de votre journal",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StatMetricCard(
                                label = "Total analysés",
                                value = "$totalCalls",
                                color = ArcepNavy,
                                modifier = Modifier.weight(1f)
                            )
                            StatMetricCard(
                                label = "Démarchage / Spam",
                                value = "$demarchageCalls",
                                color = DangerRed,
                                modifier = Modifier.weight(1f)
                            )
                            StatMetricCard(
                                label = "Manqués",
                                value = "$missedCalls",
                                color = WarningAmber,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        if (operatorCounts.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Top opérateurs dans vos appels :",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                operatorCounts.forEach { (opName, count) ->
                                    val percent = (count * 100) / totalCalls
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(text = opName, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.copyOnLongPress(opName))
                                        // The count is the operator's: copied with its name.
                                        Text(
                                            text = "$count appels ($percent%)",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.copyOnLongPress("$opName : $count appels ($percent %)")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Legal Telemarketing Rules (ARCEP Framework)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFEBEE)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Policy, contentDescription = null, tint = DangerRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Réglementation Démarchage (2023)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Depuis le 1er janvier 2023, la loi française et l'ARCEP (Décision n° 2022-1583) imposent des règles strictes aux démarcheurs téléphoniques :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    RuleItem(
                        icon = Icons.Default.Warning,
                        title = "Préfixes obligatoires pour le télémarketing",
                        desc = "0162, 0163, 0270, 0271, 0377, 0378, 0424, 0425, 0568, 0569, 0948, 0949."
                    )
                    RuleItem(
                        icon = Icons.Default.ReportProblem,
                        title = "Interdiction des numéros 06 et 07",
                        desc = "Les centres d'appels n'ont plus le droit d'utiliser des numéros de téléphone mobile."
                    )
                    RuleItem(
                        icon = Icons.Default.Security,
                        title = "Mécanisme d'Authentification (MAN)",
                        desc = "Protocole inter-opérateurs empêchant l'usurpation de numéros (spoofing téléphonique)."
                    )
                }
            }

            // Menu Développeur & Diagnostics
            if (devModeEnabled) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dev_diagnostic_menu_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Build,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Options Développeur & Diagnostics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            TextButton(
                                onClick = {
                                    devModeEnabled = false
                                    tapCount = 0
                                    SentryHelper.setDevModeEnabled(context, false)
                                    Toast.makeText(context, "Mode développeur masqué", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Masquer", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Console de validation et télémétrie de crash",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // État et DSN
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Statut du collecteur :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val isTelemetryActive = isTelemetryEnabled
                                    val collectorStatus = if (isTelemetryActive) "Actif (Logs + Erreurs)" else "Désactivé (Opt-out)"
                                    Text(
                                        collectorStatus,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTelemetryActive) SuccessGreen else DangerRed,
                                        modifier = Modifier.copyOnLongPress(collectorStatus)
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Point d'ingestion configuré :", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val dsn = SentryHelper.getResolvedDsn(context)
                                    val masked = if (dsn.length > 25) "${dsn.take(16)}...${dsn.takeLast(10)}" else dsn
                                    Text(masked, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.copyOnLongPress(masked))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch Toaster en direct
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Toaster d'événements de diagnostic",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Affiche une bulle Toast dès qu'un log ou une erreur de test est émis",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = isToastEnabled,
                                onCheckedChange = {
                                    isToastEnabled = it
                                    SentryHelper.setToastEnabled(context, it)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Déclencheurs de test en direct :", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Bouton 1: Erreur Dummy (RuntimeException)
                        Button(
                            onClick = {
                                SentryHelper.sendTestDummyError(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_dummy_error_button")
                        ) {
                            Icon(Icons.Default.BugReport, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Déclencher une erreur test (Dummy Error)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bouton 2: Logs structurés Dummy (INFO, WARN, ERROR)
                        Button(
                            onClick = {
                                SentryHelper.sendTestDummyLogs(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_dummy_logs_button")
                        ) {
                            Icon(Icons.Default.Article, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Émettre 3 logs structurés (INFO, WARN, ERROR)")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Bouton 3: Message simple
                        OutlinedButton(
                            onClick = {
                                SentryHelper.sendTestMessage(context)
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("send_dummy_message_button")
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Envoyer un message de test")
                        }
                    }
                }
            } else {
                // Info discrète pour débloquer le mode développeur
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Astuce : appuyez 7 fois sur la « Version de l'application » ci-dessus pour déverrouiller le Mode Développeur et accéder aux outils de diagnostic.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            // Section Confidentialité & Télémétrie anonyme (Sans mention Sentry)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("privacy_telemetry_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFE8F5E9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = SuccessGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Confidentialité & Données",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Protection de la vie privée & diagnostic",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Rapports techniques d'anomalies",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = if (isTelemetryEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (isTelemetryEnabled) "Activé" else "Désactivé",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isTelemetryEnabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Désactivés par défaut. Si vous les activez, l'application envoie des données techniques (erreurs, performances, stabilité) liées à un identifiant d'installation aléatoire, sans numéro, contact, note, nom ni email.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Switch(
                            checked = isTelemetryEnabled,
                            onCheckedChange = { enabled ->
                                isTelemetryEnabled = enabled
                                SentryHelper.setTelemetryEnabled(context, enabled)
                                Toast.makeText(
                                    context,
                                    if (enabled) "Rapports d'anomalies activés (Opt-in)" else "Rapports d'anomalies désactivés",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.testTag("telemetry_opt_in_switch")
                        )
                    }

                    if (onSetUpdateCheckEnabled != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Vérifier les mises à jour",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Une fois par jour au plus, l'application consulte la liste publique des versions sur GitHub (api.github.com) et vous signale la nouvelle. Aucune donnée vous concernant n'est envoyée.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = uiState.isUpdateCheckEnabled,
                                onCheckedChange = onSetUpdateCheckEnabled,
                                modifier = Modifier.testTag("update_check_switch")
                            )
                        }
                    }

                    // Debug builds only: a release installs next to them instead of updating them.
                    if (onSetUpdateCheckEnabledInDebug != null && net.slashetc.callinspector.BuildConfig.DEBUG && uiState.isUpdateCheckEnabled) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Signaler les releases dans la version debug",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Désactivé par défaut : une release ne met pas à jour cette version debug, elle s'installe à côté. « Rechercher une mise à jour » reste disponible.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 15.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Switch(
                                checked = uiState.isUpdateCheckEnabledInDebug,
                                onCheckedChange = onSetUpdateCheckEnabledInDebug,
                                modifier = Modifier.testTag("update_check_debug_switch")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showCguDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_cgu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gavel,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Conditions Générales d'Utilisation (CGU)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Direct Links to ARCEP Portal & J'alerte l'Arcep
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Liens utiles & démarches officielles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Accédez aux formulaires officiels de vérification et de signalement :",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.arcep.fr/mes-demarches-et-services/entreprises/fiches-pratiques/identifier-un-operateur-par-un-numero.html"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Portail officiel (arcep.fr)")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://jalerte.arcep.fr/"))
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Signaler un abus (jalerte.arcep.fr)")
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showCguDialog) {
        LegalTermsDialog(
            markdown = remember { LegalTerms.load(context) },
            requireAcceptance = false,
            onAccept = {},
            onDismiss = { showCguDialog = false }
        )
    }
}

@Composable
private fun StatMetricCard(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        color = color.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        // A count means nothing without what it counts: its label is copied with it.
        modifier = modifier
            .copyOnLongPress("$label : $value")
            .testTag("stat_$label")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun RuleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = DangerRed,
            modifier = Modifier
                .size(18.dp)
                .padding(top = 2.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
