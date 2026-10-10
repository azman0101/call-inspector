package net.slashetc.callinspector.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.slashetc.callinspector.ui.theme.ArcepBlue
import net.slashetc.callinspector.ui.theme.ArcepNavy
import net.slashetc.callinspector.util.AppRelease
import net.slashetc.callinspector.util.AppUpdates

/** "Version 1.0.180 disponible", above every tab, until the user downloads it or dismisses this version. */
@Composable
fun AppUpdateBanner(
    release: AppRelease,
    onShowNotes: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        color = Color(0xFFE8F1FC),
        modifier = modifier
            .fillMaxWidth()
            .testTag("app_update_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Icon(Icons.Default.NewReleases, contentDescription = null, tint = ArcepBlue, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Version ${release.versionName} disponible",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = ArcepNavy,
                modifier = Modifier
                    .weight(1f)
                    .copyOnLongPress(release.versionName)
            )
            TextButton(onClick = onShowNotes, modifier = Modifier.testTag("app_update_notes")) {
                Text("Nouveautés", fontSize = 11.sp, color = ArcepBlue)
            }
            TextButton(
                onClick = { openUrl(context, release.apkUrl ?: release.pageUrl) },
                modifier = Modifier.testTag("app_update_download")
            ) {
                Text("Télécharger", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = ArcepBlue)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.testTag("app_update_dismiss")) {
                Icon(Icons.Default.Close, contentDescription = "Ignorer cette version", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * A release's notes, as the list of changes it brings. [offerDownload] for a newer version than the one
 * installed; otherwise the notes of the installed version (after an update, or from the Observatoire).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReleaseNotesSheet(
    release: AppRelease,
    offerDownload: Boolean,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val changes = AppUpdates.changes(release.notes)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .testTag("release_notes_sheet")
        ) {
            Text(
                text = if (offerDownload) "Nouveautés de la version ${release.versionName}" else "Quoi de neuf dans la ${release.versionName}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (changes.isEmpty()) {
                Text(
                    "Pas de liste de changements pour cette version : voir la page de la release.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(changes) { change ->
                        Row {
                            Text("•", fontSize = 13.sp, color = ArcepBlue)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(change, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.copyOnLongPress(change).testTag("release_change"))
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { openUrl(context, release.pageUrl) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Page de la release", fontSize = 12.sp)
                }
                if (offerDownload) {
                    Button(
                        onClick = { openUrl(context, release.apkUrl ?: release.pageUrl) },
                        colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("release_notes_download")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Télécharger", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// The browser downloads the APK; Android then offers to install it over the current version (same key).
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucun navigateur pour ouvrir $url", Toast.LENGTH_LONG).show()
    }
}
