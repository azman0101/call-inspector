package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.ui.theme.ArcepBlue
import com.example.ui.theme.DangerRed
import com.example.ui.theme.DangerRedSoft
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.SuccessGreenSoft
import com.example.ui.theme.WarningAmber
import com.example.util.PhoneNumberFormatter

@Composable
fun CallItemCard(
    call: CallLogEntry,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    val lookup = call.lookupResult
    val isSpamOrDemarchage = call.isSpamFlagged || lookup.numberType.isDemarchage

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("call_item_${call.rawNumber}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSpamOrDemarchage) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Call Type Indicator
                CallTypeIcon(callType = call.callType)

                Spacer(modifier = Modifier.width(12.dp))

                // Caller identity & number
                Column(modifier = Modifier.weight(1f)) {
                    if (!call.cachedName.isNullOrBlank()) {
                        Text(
                            text = call.cachedName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                    }
                    Text(
                        text = call.formattedNumber,
                        style = if (call.cachedName.isNullOrBlank()) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (call.cachedName.isNullOrBlank()) FontWeight.Bold else FontWeight.Medium,
                        color = if (call.cachedName.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Date & Time + Favorite button
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = PhoneNumberFormatter.formatTimestamp(call.timestamp),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (call.durationSeconds > 0) {
                        Text(
                            text = PhoneNumberFormatter.formatDuration(call.durationSeconds),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // ARCEP Operator Information & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    OperatorBadge(
                        operatorName = lookup.operatorDisplayName,
                        operatorCode = lookup.operatorCode
                    )
                    PhoneCategoryBadge(type = lookup.numberType)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (call.isFavorite) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Favori",
                            tint = WarningAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Détails ARCEP",
                        tint = ArcepBlue,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // User Note snippet if present
            if (!call.userNote.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Note : ${call.userNote}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CallTypeIcon(callType: CallType) {
    val (bgColor, iconColor, icon) = when (callType) {
        CallType.INCOMING -> Triple(
            SuccessGreenSoft,
            SuccessGreen,
            Icons.AutoMirrored.Filled.CallReceived
        )
        CallType.OUTGOING -> Triple(
            Color(0xFFE3F2FD),
            ArcepBlue,
            Icons.AutoMirrored.Filled.CallMade
        )
        CallType.MISSED -> Triple(
            DangerRedSoft,
            DangerRed,
            Icons.AutoMirrored.Filled.CallMissed
        )
        CallType.REJECTED, CallType.BLOCKED -> Triple(
            Color(0xFFEEEEEE),
            Color(0xFF757575),
            Icons.Default.Block
        )
        CallType.UNKNOWN -> Triple(
            Color(0xFFEEEEEE),
            Color(0xFF757575),
            Icons.AutoMirrored.Filled.CallReceived
        )
    }

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = callType.name,
            tint = iconColor,
            modifier = Modifier.size(20.dp)
        )
    }
}
