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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
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
 * What a long press copies of [value]: a number only (phone number, SIRET, SIREN), shown in groups, without
 * its spaces ("01 87 21 77 77" gives "0187217777"); anything else as it is.
 */
internal fun copiedValue(value: String): String {
    val trimmed = value.trim()
    val digits = trimmed.removePrefix("+")
    val numberOnly = digits.any(Char::isDigit) && digits.all { it.isDigit() || it.isWhitespace() }
    return if (numberOnly) trimmed.filterNot(Char::isWhitespace) else value
}

/**
 * The copy actions of the values inside a clickable card or row. Such a container merges its texts into one
 * accessibility node, where each text's own long-click action would collapse into a single one: it offers
 * them all instead, one "Copier : …" action per value (see [copyActions] and [ProvideCopyActions]).
 */
class CopyActions internal constructor() {
    internal val entries = mutableStateListOf<Pair<Any, CustomAccessibilityAction>>()
}

private val LocalCopyActions = staticCompositionLocalOf<CopyActions?> { null }

@Composable
fun rememberCopyActions(): CopyActions = remember { CopyActions() }

/** On the container: the copy actions of the values it holds (given to them with [ProvideCopyActions]). */
fun Modifier.copyActions(copyActions: CopyActions): Modifier = composed {
    val actions = copyActions.entries.map { it.second }
    if (actions.isEmpty()) this else semantics { customActions = actions }
}

@Composable
fun ProvideCopyActions(copyActions: CopyActions, content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalCopyActions provides copyActions, content = content)

/**
 * A long press on this text copies [value]: the information itself, not the label shown next to it, unless
 * the value means nothing without it (a count, a percentage), which the caller then includes. Null or blank:
 * nothing to copy.
 *
 * A tap is left alone, so a text inside a clickable card or row still opens or toggles it. For TalkBack, the
 * text has its own long-click action, or, inside a container that collects them, a "Copier : …" action there.
 */
fun Modifier.copyOnLongPress(value: String?, sensitive: Boolean = false): Modifier = composed {
    if (value.isNullOrBlank()) return@composed this
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val copy = {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        copyToClipboard(context, "Info Opérateur", copiedValue(value), sensitive)
    }
    val container = LocalCopyActions.current
    val accessible = if (container != null) {
        val label = "Copier : " + if (value.length <= 40) value else value.take(39).trimEnd() + "…"
        DisposableEffect(container, label, value, sensitive) {
            val entry = Any() to CustomAccessibilityAction(label) { copy(); true }
            container.entries.add(entry)
            onDispose { container.entries.remove(entry) }
        }
        this
    } else {
        semantics { onLongClick(label = "Copier") { copy(); true } }
    }
    accessible.pointerInput(value, sensitive) { detectLongPress(copy) }
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
