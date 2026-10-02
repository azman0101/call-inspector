package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType

/** The texts of the "Qui m'a appelé ?" quick settings tile, for the last call the user did not take. */
object WhoCalled {

    /** Calls the user did not take: missed, rejected or blocked. */
    val unansweredTypes = setOf(CallType.MISSED, CallType.REJECTED, CallType.BLOCKED)

    data class Answer(val title: String, val message: String)

    /** The tile's subtitle (Android 10+): the operator of the last unanswered call, short. */
    fun subtitle(call: CallLogEntry?): String? = call?.lookupResult?.operatorDisplayName?.let { shorten(it, 20) }

    fun answer(call: CallLogEntry?, hasPermission: Boolean): Answer {
        if (!hasPermission) {
            return Answer(
                "Qui m'a appelé ?",
                "Autorisez l'accès au journal d'appels dans l'application pour identifier vos appels manqués."
            )
        }
        if (call == null) return Answer("Qui m'a appelé ?", "Aucun appel manqué dans le journal.")
        val lookup = call.lookupResult
        val type = CallExport.typeLabel(call.callType).lowercase()
        val lines = buildList {
            val time = PhoneNumberFormatter.formatTimestamp(call.timestamp)
            add("Appel $type " + if (time.first().isDigit()) "le $time" else time.replaceFirstChar { it.lowercase() })
            call.cachedName?.takeIf { it.isNotBlank() }?.let { add("Contact : $it") }
            add("Opérateur : ${lookup.operatorDisplayName}" + if (lookup.operatorCode != "—") " (${lookup.operatorCode})" else "")
            add(lookup.numberType.label)
            if (DemarchageStats.isDemarchage(call)) add("⚠️ Numéro de démarchage ou signalé comme spam")
        }
        return Answer(call.formattedNumber, lines.joinToString("\n"))
    }

    private fun shorten(text: String, max: Int) = if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"
}
