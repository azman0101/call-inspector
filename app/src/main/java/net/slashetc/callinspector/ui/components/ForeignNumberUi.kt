package net.slashetc.callinspector.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import net.slashetc.callinspector.data.model.ArcepLookupResult
import net.slashetc.callinspector.ui.theme.ArcepNavy
import net.slashetc.callinspector.ui.theme.WarningAmber
import net.slashetc.callinspector.ui.theme.WarningAmberSoft
import net.slashetc.callinspector.util.SpamVocal

/**
 * In place of the ARCEP card, for a number from abroad: its country, kind of line and original operator, as
 * libphonenumber reads them on the phone, or a plain "Numéro étranger" when it cannot read the number.
 */
@Composable
fun ForeignNumberCard(lookup: ArcepLookupResult) {
    val foreign = lookup.foreign
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("foreign_number_card")
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
                    Icon(imageVector = Icons.Default.Public, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "Numéro étranger", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val country = foreign?.countryName ?: "Pays non identifié"
                    Text(
                        text = country,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .copyOnLongPress(country)
                            .testTag("foreign_country")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            if (foreign != null) {
                DossierRow(label = "Indicatif", value = "+${foreign.countryCode}")
                DossierRow(label = "Type de ligne", value = foreign.lineType)
                foreign.originalCarrier?.let { DossierRow(label = "Opérateur d'origine", value = it) }
                if (!foreign.isValid) {
                    DossierRow(label = "Validité", value = "Ne correspond à aucune tranche attribuée de ce pays")
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Hors du plan de numérotation français : l'ARCEP ne le répertorie pas. " +
                    (if (foreign?.originalCarrier != null) "L'opérateur d'origine est celui à qui la tranche a été attribuée, avant une éventuelle portabilité. " else "") +
                    "Ces informations viennent de la base libphonenumber, incluse dans l'application.",
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(14.dp))
}

/**
 * For a received call from abroad: SignalConso and J'alerte l'Arcep only cover French numbers, so the sheet
 * offers the 33700 (spam vocal), and warns against calling the number back.
 */
@Composable
fun ForeignNumberReport(lookup: ArcepLookupResult) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = WarningAmberSoft),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Ne rappelez pas un numéro étranger inconnu : il peut être surtaxé (arnaque au rappel). " +
                    "SignalConso et J'alerte l'Arcep ne traitent que les numéros français ; signalez-le au 33700.",
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = Color(0xFF6D3800),
                modifier = Modifier.testTag("foreign_number_warning")
            )
        }
    }
    Spacer(modifier = Modifier.height(8.dp))
    OutlinedButton(
        onClick = { openSpamVocalSms(context, lookup.foreign?.e164 ?: lookup.normalizedNumber) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("report_33700_button")
    ) {
        Icon(imageVector = Icons.Default.Sms, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = "Signaler au 33700 (spam vocal)", fontWeight = FontWeight.SemiBold)
    }
    Text(
        text = "Ouvre votre application de SMS avec le message prêt : vous l'envoyez vous-même.",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
    )
}

// The SMS app, with the 33700's message ready: nothing is sent until the user sends it.
private fun openSpamVocalSms(context: Context, number: String) {
    val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${SpamVocal.NUMBER}"))
        .putExtra("sms_body", SpamVocal.message(number))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Aucune application de SMS : envoyez « ${SpamVocal.message(number)} » au ${SpamVocal.NUMBER}", Toast.LENGTH_LONG).show()
    }
}
