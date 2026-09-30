package net.slashetc.callinspector.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneCallback
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.ui.SignalConsoActivity
import net.slashetc.callinspector.ui.components.CallDetailBottomSheet
import net.slashetc.callinspector.ui.components.CallExportSheet
import net.slashetc.callinspector.ui.components.CallItemCard
import net.slashetc.callinspector.ui.components.PermissionRationaleDialog
import net.slashetc.callinspector.ui.theme.ArcepBlue
import net.slashetc.callinspector.ui.theme.ArcepNavy
import net.slashetc.callinspector.ui.theme.DangerRed
import net.slashetc.callinspector.ui.theme.SuccessGreen
import net.slashetc.callinspector.ui.theme.WarningAmber
import net.slashetc.callinspector.ui.theme.WarningAmberSoft
import net.slashetc.callinspector.viewmodel.ArcepUiState
import net.slashetc.callinspector.viewmodel.ArcepViewModel
import net.slashetc.callinspector.viewmodel.CallFilter

private val callFilters = listOf(
    Pair(CallFilter.TOUS, "Tous"),
    Pair(CallFilter.DEMARCHAGE_SPAM, "⚠️ Démarchage / Spam"),
    Pair(CallFilter.MANQUES, "Manqués"),
    Pair(CallFilter.ENTRANTS, "Entrants"),
    Pair(CallFilter.FAVORIS, "Favoris")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(
    uiState: ArcepUiState,
    viewModel: ArcepViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionResult(isGranted)
    }
    // A report sent from the form updates the number's report count in the list and the open sheet.
    val reportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.loadCalls()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Header / Hero Bar
        Surface(
            color = ArcepNavy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Journal d'appels",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Identification opérateur selon le plan officiel",
                            fontSize = 12.sp,
                            color = Color(0xFFBACEDB)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Permission status chip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (uiState.hasPermission) Color(0xFF1B5E20) else WarningAmber,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { viewModel.openPermissionDialog() }
                                .testTag("permission_status_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (uiState.hasPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.hasPermission) "Connecté" else "Non autorisé",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.loadCalls() },
                            modifier = Modifier.testTag("refresh_calls_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Actualiser",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Permission & Mode Banner
        if (!uiState.hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = WarningAmberSoft),
                shape = RoundedCornerShape(0.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(WarningAmber.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = WarningAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Autorisation d'accès requise",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = WarningAmber
                            )
                        }

                        TextButton(onClick = { viewModel.openPermissionDialog() }) {
                            Text("Détails & Sécurité", fontSize = 11.sp, color = ArcepBlue)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pour identifier l'entreprise titulaire de vos appels entrants et manqués, l'application doit lire l'historique d'appels. Toutes les données sont traitées 100% localement sur votre téléphone.",
                        fontSize = 12.sp,
                        color = Color(0xFF5D2E00),
                        lineHeight = 16.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.isPermanentlyDenied) {
                            Button(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("open_settings_banner_button")
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ouvrir Paramètres", fontSize = 11.sp)
                            }
                        } else {
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("request_permission_button")
                            ) {
                                Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Autoriser l'accès", fontSize = 11.sp)
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.forceLoadSampleCalls() },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mode Démo (Exemples)", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else if (uiState.isUsingSampleData) {
            // Notice that sample data is loaded
            Surface(
                color = Color(0xFFE8F1FC),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Mode Démo : 10 exemples d'appels français analysés",
                        fontSize = 12.sp,
                        color = ArcepNavy,
                        fontWeight = FontWeight.Medium
                    )
                    TextButton(onClick = { viewModel.loadCalls() }) {
                        Text("Recharger vrais appels", fontSize = 11.sp, color = ArcepBlue)
                    }
                }
            }
        }

        // Search Bar for Call History
        OutlinedTextField(
            value = uiState.callSearchQuery,
            onValueChange = { viewModel.setCallSearchQuery(it) },
            placeholder = { Text("Rechercher numéro, contact, opérateur...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Rechercher",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.callSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setCallSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Effacer")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("call_search_input")
        )

        // Filter Chips Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            items(callFilters) { (filter, label) ->
                val selected = uiState.selectedFilter == filter
                FilterChip(
                    selected = selected,
                    onClick = { viewModel.setFilter(filter) },
                    label = { Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ArcepBlue,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_chip_${filter.name}")
                )
            }
        }

        // A search or a filter narrowed the list: its calls can be copied, e.g. to answer an operator
        val exportLabel = uiState.callSearchQuery.trim().ifEmpty {
            callFilters.firstOrNull { it.first == uiState.selectedFilter && it.first != CallFilter.TOUS }?.second.orEmpty()
        }
        if (!uiState.isLoading && uiState.filteredCalls.isNotEmpty() && exportLabel.isNotEmpty()) {
            val numberCount = uiState.filteredCalls.distinctBy { it.normalizedNumber }.size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${uiState.filteredCalls.size} appel${if (uiState.filteredCalls.size > 1) "s" else ""} · " +
                        "$numberCount numéro${if (numberCount > 1) "s" else ""}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { viewModel.openExport(uiState.filteredCalls) },
                    modifier = Modifier.testTag("export_calls_button")
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ArcepBlue)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Exporter / copier", fontSize = 12.sp, color = ArcepBlue)
                }
            }
        }

        // Call List or Empty State
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = ArcepBlue)
            }
        } else if (uiState.filteredCalls.isEmpty()) {
            EmptyCallsView(
                searchQuery = uiState.callSearchQuery,
                onLoadSamples = { viewModel.forceLoadSampleCalls() }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.filteredCalls, key = { it.id }) { call ->
                    CallItemCard(
                        call = call,
                        onClick = { viewModel.selectCallDetail(call) },
                        onToggleFavorite = { viewModel.toggleFavorite(call) }
                    )
                }
            }
        }
    }

    // Modal Sheet for selected call
    uiState.selectedCallDetail?.let { call ->
        CallDetailBottomSheet(
            call = call,
            onDismiss = { viewModel.selectCallDetail(null) },
            onToggleSpam = { viewModel.toggleSpamFlag(it) },
            onToggleFavorite = { viewModel.toggleFavorite(it) },
            onSaveNote = { phone, note -> viewModel.saveCallNote(phone, note) },
            onReport = { reported -> reportLauncher.launch(SignalConsoActivity.intent(context, reported, uiState.calls)) }
        )
    }

    uiState.exportLines?.let { lines ->
        CallExportSheet(
            lines = lines,
            searchLabel = uiState.callSearchQuery.trim().ifEmpty {
                callFilters.firstOrNull { it.first == uiState.selectedFilter }?.second.orEmpty()
            },
            onSaveLineNumber = { key, number -> viewModel.saveLineNumber(key, number) },
            onDismiss = { viewModel.closeExport() }
        )
    }

    // Permission Rationale & Settings Dialog
    if (uiState.showPermissionDialog) {
        PermissionRationaleDialog(
            isPermanentlyDenied = uiState.isPermanentlyDenied,
            onDismiss = { viewModel.closePermissionDialog() },
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
            onUseSampleData = { viewModel.forceLoadSampleCalls() }
        )
    }
}

@Composable
private fun EmptyCallsView(
    searchQuery: String,
    onLoadSamples: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F1FC)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PhoneCallback,
                contentDescription = null,
                tint = ArcepBlue,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) "Aucun appel trouvé" else "Aucun appel dans le journal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (searchQuery.isNotEmpty()) {
                "Essayez un autre mot-clé ou réinitialisez le filtre."
            } else {
                "Votre appareil n'a pas encore d'historique d'appels récents."
            },
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onLoadSamples,
            colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue)
        ) {
            Text("Charger des exemples d'appels")
        }
    }
}
