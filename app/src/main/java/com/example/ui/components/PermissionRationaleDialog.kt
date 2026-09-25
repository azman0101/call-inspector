package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.ui.theme.ArcepBlue
import com.example.ui.theme.ArcepNavy
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarningAmber

@Composable
fun PermissionRationaleDialog(
    isPermanentlyDenied: Boolean,
    onDismiss: () -> Unit,
    onRequestPermission: () -> Unit,
    onUseSampleData: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_rationale_dialog"),
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isPermanentlyDenied) Color(0xFFFFEBEE) else Color(0xFFE8F1FC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPermanentlyDenied) Icons.Default.Warning else Icons.Default.Security,
                        contentDescription = null,
                        tint = if (isPermanentlyDenied) DangerRed else ArcepBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isPermanentlyDenied) "Accès au Journal Bloqué" else "Autorisation Journal d'appels",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Protection & Résolution ARCEP",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = if (isPermanentlyDenied) {
                        "L'accès au journal d'appels a été refusé précédemment. Pour analyser vos appels en direct, veuillez activer la permission dans les Paramètres Android de l'application."
                    } else {
                        "Pour analyser automatiquement vos correspondants et révéler quel opérateur télécom et quelle entreprise détient le numéro, l'application doit accéder au journal d'appels."
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 18.sp
                )

                // Privacy Guarantees Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrivacyItem(
                            icon = Icons.Default.Lock,
                            title = "100% Confidentiel & Local",
                            desc = "Aucun numéro ni appel n'est partagé ou transmis sur internet. La base ARCEP est stockée sur votre téléphone."
                        )
                        PrivacyItem(
                            icon = Icons.Default.Shield,
                            title = "Alerte Anti-Démarchage",
                            desc = "Identification directe des plateformes de télémarketing (décision ARCEP 2022-1583)."
                        )
                        PrivacyItem(
                            icon = Icons.Default.PhoneInTalk,
                            title = "Transparence Totale",
                            desc = "Vous découvrez le nom légal de l'opérateur attributaire de chaque ligne."
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (isPermanentlyDenied) {
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("open_settings_button")
                ) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Ouvrir les Paramètres")
                }
            } else {
                Button(
                    onClick = {
                        onRequestPermission()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcepBlue),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("dialog_grant_permission_button")
                ) {
                    Text("Autoriser l'accès")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onUseSampleData()
                    onDismiss()
                }
            ) {
                Text("Mode Démo (Exemples)")
            }
        }
    )
}

@Composable
private fun PrivacyItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = ArcepBlue,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 1.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 14.sp
            )
        }
    }
}
