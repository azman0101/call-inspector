package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry

/** What the system says about one of the user's lines (phone account), when it says anything. */
data class DetectedLine(val label: String? = null, val number: String? = null)

/** One of the user's lines (SIM...) that received some of the listed calls. */
data class ReceivingLine(
    /** [CallLogEntry.lineId], or [PhoneLines.UNKNOWN_LINE] for calls the call log does not attribute. */
    val key: String,
    /** The system's name for the line ("SIM 1", a carrier name...), else "Ligne 1", "Ligne 2"... */
    val label: String,
    /** The line's number, formatted; null until the system reports it or the user gives it once. */
    val number: String?,
    /** Most recent first, as listed. */
    val calls: List<CallLogEntry>,
)

/**
 * The lines that received a list of calls, and each line's number. A dual-SIM phone receives calls on
 * two numbers: an export must speak about one of them only, and name it, since that is what an operator
 * searches its traffic data with.
 */
object PhoneLines {

    const val UNKNOWN_LINE = "unknown"

    /** A call log phone account as one stable id: "<component>|<account id>", null without an account id. */
    fun lineId(accountComponent: String?, accountId: String?): String? =
        accountId?.takeIf { it.isNotBlank() }?.let { "${accountComponent.orEmpty()}|$it" }

    /** The phone account of a [lineId]: its component (flattened ComponentName) and its id. */
    fun accountOf(lineId: String): Pair<String, String>? {
        val separator = lineId.indexOf('|')
        if (separator < 0) return null
        return lineId.substring(0, separator) to lineId.substring(separator + 1)
    }

    fun keyOf(call: CallLogEntry): String = call.lineId ?: UNKNOWN_LINE

    /**
     * Groups [calls] by receiving line, the line with the most calls first. A line's number is, in this
     * order: the one the user gave ([stored], so a correction always wins), the one the call log recorded
     * with the calls (VIA_NUMBER), the one the system reports for the phone account ([detected]).
     */
    fun linesOf(
        calls: List<CallLogEntry>,
        stored: Map<String, String>,
        detected: Map<String, DetectedLine>,
    ): List<ReceivingLine> {
        val byLine = calls.groupBy { keyOf(it) }
        // Fallback names follow a stable order, so "Ligne 1" stays the same line from one export to the next.
        val fallbackNames = byLine.keys.filter { it != UNKNOWN_LINE }.sorted()
            .withIndex().associate { (index, key) -> key to "Ligne ${index + 1}" }
        return byLine.map { (key, lineCalls) ->
            val via = lineCalls.mapNotNull { it.viaNumber?.takeIf { v -> v.isNotBlank() } }
                .groupingBy { PhoneNumberFormatter.normalize(it) }.eachCount()
                .maxByOrNull { it.value }?.key
            val number = stored[key]?.takeIf { it.isNotBlank() }
                ?: via
                ?: detected[key]?.number?.takeIf { it.isNotBlank() }
            ReceivingLine(
                key = key,
                label = detected[key]?.label?.takeIf { it.isNotBlank() }
                    ?: fallbackNames[key]
                    ?: "Ligne non identifiée",
                number = number?.let { PhoneNumberFormatter.format(it) },
                calls = lineCalls,
            )
        }.sortedWith(compareByDescending<ReceivingLine> { it.calls.size }.thenBy { it.key })
    }
}
