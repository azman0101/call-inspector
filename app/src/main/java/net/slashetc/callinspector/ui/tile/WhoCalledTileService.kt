package net.slashetc.callinspector.ui.tile

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.slashetc.callinspector.MainActivity
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.repository.CallLogRepository
import net.slashetc.callinspector.ui.components.copyToClipboard
import net.slashetc.callinspector.util.WhoCalled

/**
 * Quick settings tile "Qui m'a appelé ?": one tap shows the operator of the last call the user did not
 * take (missed, rejected or blocked), from the call log and the ARCEP database, without opening the app.
 * On a locked phone the answer only appears after unlocking: the call log stays private.
 */
class WhoCalledTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.state = Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || isLocked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = null
            tile.updateTile()
            return
        }
        scope.launch {
            tile.subtitle = WhoCalled.subtitle(CallLogRepository(applicationContext).lastUnansweredCall())
            tile.updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) unlockAndRun { answer() } else answer()
    }

    private fun answer() {
        scope.launch {
            val repository = CallLogRepository(applicationContext)
            val hasPermission = repository.hasPermission()
            val call = if (hasPermission) repository.lastUnansweredCall() else null
            val answer = WhoCalled.answer(call, hasPermission)
            val builder = AlertDialog.Builder(this@WhoCalledTileService, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            showDialog(
                builder
                    .setCustomTitle(titleView(builder.context, answer))
                    .setView(linesView(builder.context, answer))
                    .setPositiveButton("Fermer", null)
                    .setNeutralButton(if (call != null) "Voir dans l'app" else "Ouvrir l'app") { _, _ -> openApp(call) }
                    .create()
            )
        }
    }

    // The title and the lines as the dialog would show them, each copying its value on a long press, as in the app.
    private fun titleView(context: Context, answer: WhoCalled.Answer) = TextView(context).apply {
        val padding = dp(context, 24)
        setTextAppearance(android.R.style.TextAppearance_DeviceDefault_DialogWindowTitle)
        setPadding(padding, padding, padding, 0)
        text = answer.title
        answer.titleCopy?.let { copyOnLongClick(it, sensitive = true) }
    }

    private fun linesView(context: Context, answer: WhoCalled.Answer) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, 24), dp(context, 12), dp(context, 24), 0)
        val attrs = context.obtainStyledAttributes(intArrayOf(android.R.attr.textColorSecondary))
        val color = attrs.getColorStateList(0)
        attrs.recycle()
        answer.lines.forEach { line ->
            addView(
                TextView(context).apply {
                    setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium)
                    color?.let { setTextColor(it) }
                    text = line.text
                    line.copy?.let { copyOnLongClick(it, line.sensitive) }
                }
            )
        }
    }

    private fun TextView.copyOnLongClick(value: String, sensitive: Boolean) = setOnLongClickListener { view ->
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        copyToClipboard(view.context, "Info Opérateur", value, sensitive)
        true
    }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    // The history, searched on the caller's number.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp(call: CallLogEntry?) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        call?.let { intent.putExtra(MainActivity.EXTRA_SEARCH_NUMBER, it.normalizedNumber) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
