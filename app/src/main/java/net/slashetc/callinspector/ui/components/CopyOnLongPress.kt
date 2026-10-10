package net.slashetc.callinspector.ui.components

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics

/**
 * Copies [text] to the clipboard. [sensitive] (phone numbers, names, notes) keeps it out of Android 13+'s
 * clipboard preview. Android 13+ confirms the copy itself; older versions get a toast.
 */
fun copyToClipboard(context: Context, label: String, text: String, sensitive: Boolean, confirmation: String = "Copié") {
    val clip = ClipData.newPlainText(label, text).apply {
        if (sensitive) {
            description.extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
        }
    }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) Toast.makeText(context, confirmation, Toast.LENGTH_SHORT).show()
}

/**
 * A long press on this text copies [value]: the information itself, not the label shown next to it, unless
 * the value means nothing without it (a count, a percentage), which the caller then includes. Null or blank:
 * nothing to copy.
 *
 * A tap is left alone, so a text inside a clickable card or row still opens or toggles it.
 */
fun Modifier.copyOnLongPress(value: String?, sensitive: Boolean = false): Modifier = composed {
    if (value.isNullOrBlank()) return@composed this
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val copy = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        copyToClipboard(context, "Info Opérateur", value, sensitive)
    }
    this
        .semantics { onLongClick(label = "Copier") { copy(); true } }
        .pointerInput(value, sensitive) { detectLongPress(copy) }
}

/**
 * Like detectTapGestures(onLongPress = ...), without taking the taps: a release before the long press timeout,
 * a move (a scroll) or a gesture another node takes is not this one's. After a long press, the release is
 * consumed, so the card around the text does not take it for a tap.
 */
private suspend fun PointerInputScope.detectLongPress(onLongPress: () -> Unit) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || change.isConsumed) break
            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) break
            if (awaitPointerEvent(PointerEventPass.Final).changes.any { it.isConsumed }) break
        }
        true
    }
    if (ended != null) return@awaitEachGesture
    onLongPress()
    do {
        val event = awaitPointerEvent()
        event.changes.forEach { it.consume() }
    } while (event.changes.any { it.pressed })
}
