package net.slashetc.callinspector.data.model

data class ArcepLookupResult(
    val queryNumber: String,
    val normalizedNumber: String,
    val formattedNumber: String,
    val operator: ArcepOperator?,
    val range: ArcepNumberRange?,
    val numberType: PhoneNumberType,
    val isFound: Boolean,
    val officialArcepUrl: String = "https://www.arcep.fr/mes-demarches-et-services/entreprises/fiches-pratiques/identifier-un-operateur-par-un-numero.html"
) {
    val operatorDisplayName: String
        get() = operator?.name ?: range?.operatorName ?: "Opérateur non identifié"

    val operatorCode: String
        get() = operator?.code ?: range?.operatorCode ?: "—"

    val territory: String
        get() = range?.territory ?: "France (Métropole ou Outre-Mer)"

    val attributionDate: String?
        get() = range?.attributionDate

    val blockDisplay: String
        get() = if (range != null) {
            "${range.trancheDebut} à ${range.trancheFin}"
        } else {
            "Tranche non déterminée"
        }
}

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED,
    UNKNOWN
}

data class CallLogEntry(
    val id: Long,
    val rawNumber: String,
    val normalizedNumber: String,
    val formattedNumber: String,
    val cachedName: String?,
    val timestamp: Long,
    val durationSeconds: Long,
    val callType: CallType,
    val lookupResult: ArcepLookupResult,
    val isSpamFlagged: Boolean = false,
    val isFavorite: Boolean = false,
    val userNote: String? = null
)
