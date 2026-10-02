package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import java.util.Calendar
import java.util.TimeZone

/** Telemarketing calls received today and this week (since Monday), for the home screen widget. */
data class DemarchageCounts(val today: Int, val thisWeek: Int)

object DemarchageStats {

    /** A call counted as telemarketing: in a range reserved for it, or flagged as spam by the user. */
    fun isDemarchage(call: CallLogEntry): Boolean = call.isSpamFlagged || call.lookupResult.numberType.isDemarchage

    /** Counts in the phone's local time: "today" from midnight, "this week" from Monday midnight. */
    fun count(calls: List<CallLogEntry>, now: Long, timeZone: TimeZone = TimeZone.getDefault()): DemarchageCounts {
        val startOfDay = startOfDay(now, timeZone)
        val startOfWeek = startOfWeek(now, timeZone)
        val demarchage = calls.filter { isDemarchage(it) && it.timestamp <= now }
        return DemarchageCounts(
            today = demarchage.count { it.timestamp >= startOfDay },
            thisWeek = demarchage.count { it.timestamp >= startOfWeek },
        )
    }

    internal fun startOfDay(now: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    // Monday, as weeks start in France, whatever the device locale says.
    internal fun startOfWeek(now: Long, timeZone: TimeZone): Long =
        Calendar.getInstance(timeZone).apply {
            timeInMillis = startOfDay(now, timeZone)
            val daysSinceMonday = (get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
            add(Calendar.DAY_OF_MONTH, -daysSinceMonday)
        }.timeInMillis
}
