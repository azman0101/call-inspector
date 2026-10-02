package net.slashetc.callinspector.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.slashetc.callinspector.MainActivity
import net.slashetc.callinspector.R
import net.slashetc.callinspector.data.repository.CallLogRepository
import net.slashetc.callinspector.util.DemarchageCounts
import net.slashetc.callinspector.util.DemarchageStats

/**
 * Home screen widget: telemarketing calls received today and this week. A tap opens the call history
 * filtered on them. Refreshed every 30 minutes by the system and whenever the app reloads the call log
 * ([requestUpdate]); everything is computed on the phone, from the call log.
 */
class DemarchageWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // Reading the call log and the ARCEP database takes longer than a receiver's main-thread budget.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = views(context, counts(context))
                appWidgetIds.forEach { appWidgetManager.updateAppWidget(it, views) }
            } catch (e: Exception) {
                Log.e(TAG, "Widget update failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DemarchageWidget"

        /** Null without the call log permission: the widget then asks to open the app. */
        suspend fun counts(context: Context): DemarchageCounts? {
            val repository = CallLogRepository(context)
            if (!repository.hasPermission()) return null
            return DemarchageStats.count(repository.getCallLogs(useSampleIfEmpty = false), System.currentTimeMillis())
        }

        fun views(context: Context, counts: DemarchageCounts?): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_demarchage).apply {
                if (counts == null) {
                    setViewVisibility(R.id.widget_counts, View.GONE)
                    setViewVisibility(R.id.widget_message, View.VISIBLE)
                } else {
                    setViewVisibility(R.id.widget_counts, View.VISIBLE)
                    setViewVisibility(R.id.widget_message, View.GONE)
                    setTextViewText(R.id.widget_today_count, counts.today.toString())
                    setTextViewText(R.id.widget_week_count, counts.thisWeek.toString())
                    // Red once there is something to see today.
                    val todayColor = if (counts.today > 0) R.color.widget_alert else R.color.widget_text
                    setTextColor(R.id.widget_today_count, ContextCompat.getColor(context, todayColor))
                }
                setOnClickPendingIntent(
                    R.id.widget_root,
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_SHOW_DEMARCHAGE, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }

        /** Refreshes every widget placed on the home screen; does nothing when there is none. */
        fun requestUpdate(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, DemarchageWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, DemarchageWidgetProvider::class.java)
                    .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }
}
