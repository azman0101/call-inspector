package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry

object SearchQueries {

    fun matchesCall(call: CallLogEntry, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return call.formattedNumber.lowercase().contains(q) ||
            call.rawNumber.contains(q) ||
            (call.cachedName?.lowercase()?.contains(q) == true) ||
            call.lookupResult.operatorDisplayName.lowercase().contains(q) ||
            call.lookupResult.operatorCode.lowercase().contains(q)
    }

    fun prefixSearchQuery(input: String): String? =
        input.takeIf { it.length in 2..6 }
}
