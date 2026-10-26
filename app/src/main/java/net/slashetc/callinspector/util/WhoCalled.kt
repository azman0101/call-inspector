package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType

/** The texts of the "Qui m'a appelé ?" quick settings tile, for the last call the user did not take. */
object WhoCalled {

    /** Calls the user did not take: missed, rejected or blocked. */
    val unansweredTypes = setOf(CallType.MISSED, CallType.REJECTED, CallType.BLOCKED)

    /** A line of the answer, and what a long press on it copies: the value, without "Contact :" (none: nothing). */
    data class Line(val text: String, val copy: String? = null, val sensitive: Boolean = false)

    /** [titleCopy]: what a long press on the title copies (the caller's number). */
    data class Answer(val title: String, val lines: List<Line>, val titleCopy: String? = null) {
        val message: String get() = lines.joinToString("\n") { it.text }
    }

    /** The tile's subtitle (Android 10+): the operator of the last unanswered call, short. */
    fun subtitle(call: CallLogEntry?): String? = call?.lookupResult?.operatorDisplayName?.let { shorten(it, 20) }

    fun answer(call: CallLogEntry?, hasPermission: Boolean): Answer {
        if (!hasPermission) {
            return Answer(
                "Qui m'a appelé ?",
                listOf(Line("Autorisez l'accès au journal d'appels dans l'application pour identifier vos appels manqués."))
            )
        }
        if (call == null) return Answer("Qui m'a appelé ?", listOf(Line("Aucun appel manqué dans le journal.")))
        val lookup = call.lookupResult
        val type = CallExport.typeLabel(call.callType).lowercase()
        val lines = buildList {
            val time = PhoneNumberFormatter.formatTimestamp(call.timestamp)
            // "Hier" means nothing once pasted: the date and time in full.
            add(
                Line(
                    "Appel $type " + if (time.first().isDigit()) "le $time" else time.replaceFirstChar { it.lowercase() },
                    copy = PhoneNumberFormatter.formatFullTimestamp(call.timestamp)
                )
            )
            call.cachedName?.takeIf { it.isNotBlank() }?.let { add(Line("Contact : $it", copy = it, sensitive = true)) }
            val foreign = lookup.foreign
            if (lookup.isForeign) {
                foreign?.let { add(Line("Pays : ${it.countryName}", copy = it.countryName)) }
                foreign?.originalCarrier?.let { add(Line("Opérateur d'origine : $it", copy = it)) }
                val kind = listOfNotNull("Numéro étranger", foreign?.lineType).joinToString(" · ")
                add(Line(kind, copy = kind))
            } else {
                val operator = lookup.operatorDisplayName + if (lookup.operatorCode != "—") " (${lookup.operatorCode})" else ""
                add(Line("Opérateur : $operator", copy = operator))
                add(Line(lookup.numberType.label, copy = lookup.numberType.label))
            }
            if (call.isSpamFlagged || lookup.numberType.isDemarchage) add(Line("⚠️ Numéro de démarchage ou signalé comme spam"))
            if (lookup.isForeign) add(Line(FOREIGN_CALLBACK_WARNING))
        }
        return Answer(call.formattedNumber, lines, titleCopy = call.formattedNumber)
    }

    /** Unanswered call from abroad: calling back an unknown foreign number can be costly (wangiri). */
    const val FOREIGN_CALLBACK_WARNING = "⚠️ Ne rappelez pas un numéro étranger inconnu : il peut être surtaxé."

    private fun shorten(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
}
