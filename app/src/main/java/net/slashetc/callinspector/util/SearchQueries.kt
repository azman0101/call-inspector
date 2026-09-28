package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry

object SearchQueries {

    fun matchesCall(call: CallLogEntry, query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        if (call.formattedNumber.lowercase().contains(q) ||
            call.rawNumber.contains(q) ||
            (call.cachedName?.lowercase()?.contains(q) == true) ||
            call.lookupResult.operatorDisplayName.lowercase().contains(q) ||
            call.lookupResult.operatorCode.lowercase().contains(q)
        ) return true
        // Compare digits with the normalized number: "0270", "02 70" and "+33270" all
        // match a call stored as "+33270334455" and displayed as "02 70 33 44 55".
        val digits = PhoneNumberFormatter.normalize(q)
        return digits.isNotEmpty() && digits.all { it.isDigit() } && call.normalizedNumber.contains(digits)
    }

    /**
     * Prepares input for manual prefix or operator database searches.
     * Operator names and codes (containing letters) are searched as typed without length limits
     * (minimum 2 characters), allowing names longer than 6 characters to reach database search.
     * Phone numbers are normalized and restricted to prefixes between 2 and 6 digits
     * (full 10-digit numbers use exact lookup instead).
     */
    fun prefixSearchQuery(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.any { it.isLetter() }) return trimmed.takeIf { it.length >= 2 }
        val digits = PhoneNumberFormatter.normalize(trimmed)
        return digits.takeIf { it.length in 2..6 && it.all { c -> c.isDigit() } }
    }
}
