package net.slashetc.callinspector.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object PhoneNumberFormatter {

    fun normalize(raw: String?): String {
        if (raw == null) return ""
        val clean = raw.trim().replace(" ", "").replace("-", "").replace(".", "").replace("(", "").replace(")", "")

        return when {
            clean.startsWith("+33") -> "0" + clean.removePrefix("+33")
            clean.startsWith("0033") -> "0" + clean.removePrefix("0033")
            clean.startsWith("+590") -> "0" + clean.removePrefix("+590")
            clean.startsWith("+594") -> "0" + clean.removePrefix("+594")
            clean.startsWith("+596") -> "0" + clean.removePrefix("+596")
            clean.startsWith("+262") -> "0" + clean.removePrefix("+262")
            else -> clean
        }
    }

    fun format(number: String?): String {
        if (number.isNullOrBlank()) return "Inconnu"
        val normalized = normalize(number)

        // 10 digits French phone number (e.g. 0123456789 -> 01 23 45 67 89)
        if (normalized.length == 10 && normalized.startsWith("0")) {
            return normalized.chunked(2).joinToString(" ")
        }

        // 4 digits (e.g. 3635 -> 36 35)
        if (normalized.length == 4) {
            return "${normalized.substring(0, 2)} ${normalized.substring(2)}"
        }

        // 6 digits (e.g. 118218 -> 118 218)
        if (normalized.length == 6) {
            return "${normalized.substring(0, 3)} ${normalized.substring(3)}"
        }

        return rawOrOriginal(number)
    }

    private fun rawOrOriginal(orig: String): String {
        return if (orig.length > 8 && orig.all { it.isDigit() }) {
            orig.chunked(2).joinToString(" ")
        } else {
            orig
        }
    }

    fun formatTimestamp(
        epochMillis: Long,
        now: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        // Calendar days in the phone's time zone, not 24-hour periods: a call yesterday at 12:16 is "Hier"
        // even when less than 24 hours have passed.
        val diffDays = calendarDaysBetween(epochMillis, now, timeZone)

        val timeFormat = SimpleDateFormat("HH:mm", Locale.FRENCH).apply { this.timeZone = timeZone }
        val dateFormat = SimpleDateFormat("dd MMM", Locale.FRENCH).apply { this.timeZone = timeZone }
        val fullDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRENCH).apply { this.timeZone = timeZone }

        val callDate = Date(epochMillis)

        return when {
            diffDays == 0L -> "Aujourd'hui à ${timeFormat.format(callDate)}"
            diffDays == 1L -> "Hier à ${timeFormat.format(callDate)}"
            diffDays < 7L -> "${dateFormat.format(callDate)} à ${timeFormat.format(callDate)}"
            else -> fullDateFormat.format(callDate)
        }
    }

    // Days between the two dates' midnights, so daylight saving changes (23- or 25-hour days) do not count.
    private fun calendarDaysBetween(from: Long, to: Long, timeZone: TimeZone): Long {
        fun dayNumber(millis: Long): Long {
            val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
            return Math.floorDiv(millis + calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET), 86_400_000L)
        }
        return dayNumber(to) - dayNumber(from)
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "0 s"
        val mins = seconds / 60
        val remainingSecs = seconds % 60
        return when {
            mins > 0 && remainingSecs > 0 -> "${mins}m ${remainingSecs}s"
            mins > 0 -> "${mins} min"
            else -> "${remainingSecs} s"
        }
    }
}
