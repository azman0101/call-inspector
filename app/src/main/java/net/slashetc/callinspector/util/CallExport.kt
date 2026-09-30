package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class CallExportFormat { CSV, MARKDOWN }

/** One caller number of a search result, with its calls (most recent first, as listed). */
data class ExportableNumber(val normalizedNumber: String, val calls: List<CallLogEntry>) {
    val formattedNumber: String get() = calls.first().formattedNumber
    val operatorName: String get() = calls.first().lookupResult.operatorDisplayName
}

/**
 * Formats calls for the clipboard, to answer an operator that asks which calls to look for in its
 * traffic data (e.g. after a SignalConso report): the calling number, the exact date and time in the
 * phone's local time, and the line that received them. The Markdown version is meant to be pasted into
 * an AI assistant along with the operator's mail, so it starts with instructions for writing the reply.
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
                add(typeLabel(call.callType))
                add(call.durationSeconds.coerceAtLeast(0).toString())
                add(call.lookupResult.operatorDisplayName)
                if (receiving != null) add(receiving)
                if (options.includeNotes) add(call.userNote.orEmpty())
            }
        }
        return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } } + "\n"
    }

    // A plain list rather than a Markdown table: the text is meant for an AI assistant, and a table's
    // pipes and "---" separators cost tokens without adding information.
    private fun markdown(calls: List<CallLogEntry>, options: Options, timeZone: TimeZone): String {
        val receiving = options.receivingNumber?.takeIf { it.isNotBlank() }?.trim()
        val numberCount = calls.map { it.normalizedNumber }.distinct().size
        // One operator for all calls (the usual case after searching its name): stated once, not per call.
        val sharedOperator = calls.map { it.lookupResult.operatorDisplayName }.distinct().singleOrNull()
        val fields = buildList {
            add("date heure")
            add("numéro appelant")
            add("type")
            add("durée")
            if (sharedOperator == null) add("opérateur")
            if (options.includeNotes) add("note")
        }
        return buildString {
            append(AI_INSTRUCTIONS).append("\n\n")
            val subject = options.searchLabel?.takeIf { it.isNotBlank() }?.let { " correspondant à « ${it.trim()} »" }.orEmpty()
            append("**Appels$subject** : ${plural(calls.size, "appel")} de ${plural(numberCount, "numéro")}")
            if (sharedOperator != null) append(", opérateur $sharedOperator")
            append("\n")
            if (receiving != null) append("Ligne ayant reçu les appels : $receiving\n")
            append("Heures locales du téléphone (${timeZone.id}). Chaque appel : ${fields.joinToString(", ")}.\n\n")
            calls.forEach { call ->
                val values = buildList {
                    add("${date(call.timestamp, timeZone)} ${time(call.timestamp, timeZone)}")
                    add(call.formattedNumber)
                    add(typeLabel(call.callType))
                    add(PhoneNumberFormatter.formatDuration(call.durationSeconds))
                    if (sharedOperator == null) add(call.lookupResult.operatorDisplayName)
                }
                append("- ").append(values.joinToString(", "))
                val note = call.userNote?.let { singleLine(it) }?.takeIf { it.isNotEmpty() }
                if (options.includeNotes && note != null) append(", note : ").append(note)
                append("\n")
            }
        }
    }

    // Mail clients show Markdown as raw symbols: the assistant must write the reply in plain text.
    internal val AI_INSTRUCTIONS = """
        > **Consignes pour l'assistant IA qui rédige la réponse :**
        > - Écris le mail en texte brut, sans aucune mise en forme Markdown (pas de `**`, de titres `#`, de tableau `|` ni de puces `*`) : les clients mail ne l'affichent pas et le rendent illisible.
        > - Présente chaque appel sur une ligne de phrase simple, par exemple : « Le 28/09/2026 à 14:03:12, appel manqué du 01 59 39 12 34 ».
        > - Donne les dates et heures telles qu'elles figurent ci-dessous (heure locale), sans les convertir.
        > - Indique le numéro de la ligne qui a reçu les appels, et reprends ce que le mail de l'opérateur demande de préciser.
    """.trimIndent()

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

    private fun plural(count: Int, word: String) = "$count $word" + if (count > 1) "s" else ""

    // RFC 4180: quote fields holding a separator, a quote or a line break, doubling inner quotes.
    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' || it == ';' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }

    // A note may hold line breaks, which would split the call's line.
    private fun singleLine(value: String): String =
        value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ').trim()
}
