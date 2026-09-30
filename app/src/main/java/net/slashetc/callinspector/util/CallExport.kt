package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class CallExportFormat { CSV, MARKDOWN }

/** One caller number of a search result, with its calls (most recent first, as listed). */
data class ExportableNumber(val normalizedNumber: String, val calls: List<CallLogEntry>) {
    val formattedNumber: String get() = calls.first().formattedNumber
    val operatorName: String get() = calls.first().lookupResult.operatorDisplayName
}

/**
 * Formats calls for the clipboard, to answer an operator that asks which calls to look for in its
 * traffic data (e.g. after a SignalConso report): the calling number, the exact date and time with its
 * UTC offset, and the line that received them.
 */
object CallExport {

    data class Options(
        val format: CallExportFormat,
        val includeNotes: Boolean = false,
        /** The user's own line, on which the calls were received: operators need it to find them. */
        val receivingNumber: String? = null,
        /** What the calls were selected with (the search), shown in the Markdown heading. */
        val searchLabel: String? = null,
    )

    /** Groups calls by caller number, in the order of their first call in [calls]. */
    fun numbersIn(calls: List<CallLogEntry>): List<ExportableNumber> =
        calls.groupBy { it.normalizedNumber }.map { (number, numberCalls) -> ExportableNumber(number, numberCalls) }

    /** The calls of [selectedNumbers], oldest first. */
    fun selectedCalls(calls: List<CallLogEntry>, selectedNumbers: Set<String>): List<CallLogEntry> =
        calls.filter { it.normalizedNumber in selectedNumbers }.sortedBy { it.timestamp }

    fun format(calls: List<CallLogEntry>, options: Options, timeZone: TimeZone = TimeZone.getDefault()): String {
        val ordered = calls.sortedBy { it.timestamp }
        return when (options.format) {
            CallExportFormat.CSV -> csv(ordered, options, timeZone)
            CallExportFormat.MARKDOWN -> markdown(ordered, options, timeZone)
        }
    }

    private fun csv(calls: List<CallLogEntry>, options: Options, timeZone: TimeZone): String {
        val receiving = options.receivingNumber?.takeIf { it.isNotBlank() }?.trim()
        val header = buildList {
            add("Numéro appelant")
            add("Date")
            add("Heure")
            add("Fuseau")
            add("Type")
            add("Durée (s)")
            add("Opérateur")
            if (receiving != null) add("Ligne appelée")
            if (options.includeNotes) add("Note")
        }
        val rows = calls.map { call ->
            buildList {
                add(call.formattedNumber)
                add(date(call.timestamp, timeZone))
                add(time(call.timestamp, timeZone))
                add(utcOffset(call.timestamp, timeZone))
                add(typeLabel(call.callType))
                add(call.durationSeconds.coerceAtLeast(0).toString())
                add(call.lookupResult.operatorDisplayName)
                if (receiving != null) add(receiving)
                if (options.includeNotes) add(call.userNote.orEmpty())
            }
        }
        return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } } + "\n"
    }

    private fun markdown(calls: List<CallLogEntry>, options: Options, timeZone: TimeZone): String {
        val receiving = options.receivingNumber?.takeIf { it.isNotBlank() }?.trim()
        val numberCount = calls.map { it.normalizedNumber }.distinct().size
        val header = buildList {
            add("Numéro appelant")
            add("Date")
            add("Heure")
            add("Fuseau")
            add("Type")
            add("Durée")
            add("Opérateur")
            if (options.includeNotes) add("Note")
        }
        return buildString {
            val subject = options.searchLabel?.takeIf { it.isNotBlank() }?.let { " correspondant à « ${it.trim()} »" }.orEmpty()
            append("**Appels$subject** : ${plural(calls.size, "appel")} de ${plural(numberCount, "numéro")}\n")
            if (receiving != null) append("\nLigne ayant reçu les appels : $receiving\n")
            append("\n")
            append(header.joinToString(" | ", "| ", " |")).append("\n")
            append(header.joinToString("|", "|", "|") { "---" }).append("\n")
            calls.forEach { call ->
                val cells = buildList {
                    add(call.formattedNumber)
                    add(date(call.timestamp, timeZone))
                    add(time(call.timestamp, timeZone))
                    add(utcOffset(call.timestamp, timeZone))
                    add(typeLabel(call.callType))
                    add(PhoneNumberFormatter.formatDuration(call.durationSeconds))
                    add(call.lookupResult.operatorDisplayName)
                    if (options.includeNotes) add(call.userNote.orEmpty())
                }
                append(cells.joinToString(" | ", "| ", " |") { markdownCell(it) }).append("\n")
            }
        }
    }

    fun typeLabel(type: CallType): String = when (type) {
        CallType.INCOMING -> "Reçu"
        CallType.OUTGOING -> "Émis"
        CallType.MISSED -> "Manqué"
        CallType.REJECTED -> "Rejeté"
        CallType.BLOCKED -> "Bloqué"
        CallType.UNKNOWN -> "Inconnu"
    }

    private fun date(epochMillis: Long, timeZone: TimeZone) =
        SimpleDateFormat("dd/MM/yyyy", Locale.FRENCH).apply { this.timeZone = timeZone }.format(Date(epochMillis))

    private fun time(epochMillis: Long, timeZone: TimeZone) =
        SimpleDateFormat("HH:mm:ss", Locale.FRENCH).apply { this.timeZone = timeZone }.format(Date(epochMillis))

    // Per call: a period of calls can span a daylight saving time change.
    internal fun utcOffset(epochMillis: Long, timeZone: TimeZone): String {
        val offsetMinutes = timeZone.getOffset(epochMillis) / 60_000
        val sign = if (offsetMinutes < 0) "-" else "+"
        return "UTC%s%02d:%02d".format(Locale.ROOT, sign, abs(offsetMinutes) / 60, abs(offsetMinutes) % 60)
    }

    private fun plural(count: Int, word: String) = "$count $word" + if (count > 1) "s" else ""

    // RFC 4180: quote fields holding a separator, a quote or a line break, doubling inner quotes.
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // A note may hold a pipe or a line break, which would split the table row.
    private fun markdownCell(value: String): String =
        value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').replace("|", "\\|").trim()
}
