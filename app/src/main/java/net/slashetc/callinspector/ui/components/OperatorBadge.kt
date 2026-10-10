package net.slashetc.callinspector.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.slashetc.callinspector.data.model.PhoneNumberType
import net.slashetc.callinspector.ui.theme.ArcepBlue
import net.slashetc.callinspector.ui.theme.ArcepNavy
import net.slashetc.callinspector.ui.theme.DangerRed
import net.slashetc.callinspector.ui.theme.DangerRedSoft
import net.slashetc.callinspector.ui.theme.PurpleSva
import net.slashetc.callinspector.ui.theme.PurpleSvaSoft
import net.slashetc.callinspector.ui.theme.WarningAmber
import net.slashetc.callinspector.ui.theme.WarningAmberSoft

@Composable
fun OperatorBadge(
    operatorName: String,
    operatorCode: String,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor) = getOperatorColors(operatorName)
    val hasCode = operatorCode.isNotBlank() && operatorCode != "—"

    Row(
        modifier = modifier
            // The name, and the code that goes with it.
            .copyOnLongPress(if (hasCode) "$operatorName ($operatorCode)" else operatorName)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Business,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = operatorName,
            color = textColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (hasCode) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($operatorCode)",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

@Composable
fun PhoneCategoryBadge(
    type: PhoneNumberType,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, icon) = when {
        type.isDemarchage -> Triple(
            DangerRedSoft,
            DangerRed,
            Icons.Default.Warning
        )
        type == PhoneNumberType.MOBILE -> Triple(
            Color(0xFFE3F2FD),
            ArcepBlue,
            Icons.Default.PhoneAndroid
        )
        type == PhoneNumberType.SVA_SPECIAL -> Triple(
            PurpleSvaSoft,
            PurpleSva,
            Icons.Default.PhoneInTalk
        )
        else -> Triple(
            Color(0xFFF1F5F9),
            Color(0xFF475569),
            Icons.Default.PhoneInTalk
        )
    }

    Row(
        modifier = modifier
            .copyOnLongPress(type.label)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = type.label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun getOperatorColors(name: String): Pair<Color, Color> {
    val lower = name.lowercase()
    return when {
        lower.contains("orange") || lower.contains("france telecom") -> Pair(Color(0xFFFFF3E0), Color(0xFFE65100)) // Orange
        lower.contains("sfr") || lower.contains("radiotéléphone") -> Pair(Color(0xFFFFEBEE), Color(0xFFC62828)) // Red SFR
        lower.contains("bouygues") -> Pair(Color(0xFFE0F7FA), Color(0xFF00838F)) // Cyan Bouygues
        lower.contains("free") -> Pair(Color(0xFFFBE9E7), Color(0xFFD84315)) // Free Red
        lower.contains("ovh") -> Pair(Color(0xFFEDE7F6), Color(0xFF512DA8)) // OVH Purple
        lower.contains("manifone") || lower.contains("bjt") -> Pair(WarningAmberSoft, WarningAmber) // Call center operator
        else -> Pair(Color(0xFFEBF3FA), ArcepNavy)
    }
}
