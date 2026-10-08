package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.PhoneNumberType
import java.net.URI
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * What the app prefills in J'alerte l'Arcep (jalerte.arcep.fr), the ARCEP's reporting platform, for
 * telemarketing calls. The user checks every step and sends the alert; the app never does.
 *
 * - [numberTypes]: labels of the step 2 checkboxes ("Le type de numéro utilisé pour vous appeler était").
 * - [operatorName]: the operator the ARCEP assigned the number(s) to; [jalerteOperator] is the same operator
 *   as J'alerte l'Arcep's list names it, when found, otherwise the name goes into its "Autre" field.
 */
data class ArcepAlertPlan(
    val numberTypes: List<String>,
    val operatorName: String?,
    val jalerteOperator: String?,
    val description: String,
    val callCount: Int,
)

object ArcepAlert {
    const val URL = "https://jalerte.arcep.fr/jalerte/"
    const val HOST = "jalerte.arcep.fr"
    const val FORM_PATH = "/jalerte/"

    /** Any HTTPS page of J'alerte l'Arcep: where the "alert sent" message may be looked for (no user data). */
    fun isJalerteUrl(url: String?): Boolean {
        val uri = url?.let { runCatching { URI(it).normalize() }.getOrNull() } ?: return false
        return uri.scheme == "https" && uri.host == HOST && (uri.port == -1 || uri.port == 443)
    }

    /** What J'alerte l'Arcep shows once an alert is sent. */
    const val SENT_TEXT = "Votre alerte a été soumise"

    /**
     * Whether the page shows [SENT_TEXT]: its rendered text only (innerText, not scripts), spaces, case and
     * apostrophes ignored. Read by the app itself, so it works on whatever page the confirmation is on.
     */
    val SENT_QUERY = "(function () { var b = document.body; if (!b) return false; " +
        "var t = (b.innerText || b.textContent || '').replace(/[’]/g, \"'\").replace(/\\s+/g, ' ').toLowerCase(); " +
        "return t.indexOf('" + SENT_TEXT.lowercase(Locale.FRANCE) + "') !== -1; })()"

    /** "Alerté à l'Arcep 2 fois · dernière le 08/10/2026", or null when no alert covered the number. */
    fun alertSummary(alertCount: Int, lastAlertAt: Long?, timeZone: TimeZone = TimeZone.getDefault()): String? {
        if (alertCount <= 0 || lastAlertAt == null) return null
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply { this.timeZone = timeZone }.format(lastAlertAt)
        return "Alerté à l'Arcep $alertCount fois · dernière le $date"
    }

    /** The alert form only: HTTPS, exact host and path (its steps are "?0", "?1"… of the same page). */
    fun isFormUrl(url: String?): Boolean {
        val uri = url?.let { runCatching { URI(it).normalize() }.getOrNull() } ?: return false
        if (uri.scheme != "https" || uri.host != HOST || (uri.port != -1 && uri.port != 443)) return false
        val path = uri.rawPath ?: return false
        return path == FORM_PATH || path == FORM_PATH.removeSuffix("/")
    }

    // The step 2 checkboxes, as J'alerte l'Arcep words them.
    const val TYPE_HIDDEN = "un numéro masqué"
    const val TYPE_MOBILE = "un numéro mobile"
    const val TYPE_FOREIGN = "un numéro étranger"
    const val TYPE_FIXED = "un numéro fixe"
    const val TYPE_CALL_CENTER = "un numéro fixe provenant d'un centre d'appel"

    /** The checkbox for the number of [call]; null when none fits (special numbers, short numbers). */
    fun numberType(call: CallLogEntry): String? {
        if (call.normalizedNumber.isBlank()) return TYPE_HIDDEN
        return when (call.lookupResult.numberType) {
            PhoneNumberType.DEMARCHAGE_COMMERCIAL -> TYPE_CALL_CENTER
            PhoneNumberType.MOBILE -> TYPE_MOBILE
            PhoneNumberType.INTERNATIONAL -> TYPE_FOREIGN
            PhoneNumberType.FIXE_ILE_DE_FRANCE, PhoneNumberType.FIXE_NORD_OUEST, PhoneNumberType.FIXE_NORD_EST,
            PhoneNumberType.FIXE_SUD_EST, PhoneNumberType.FIXE_SUD_OUEST, PhoneNumberType.FIXE_OUTRE_MER,
            PhoneNumberType.POLYVALENT_VOIP -> TYPE_FIXED
            else -> null
        }
    }

    /**
     * The telemarketing calls from the operator of [call]'s number: received calls (not outgoing) from a
     * number the ARCEP assigned to the same operator, in a telemarketing range or flagged as spam, plus
     * [call] itself. Contacts' ordinary calls on the same big operator are not swept in. Oldest first.
     */
    fun callsFromSameOperator(call: CallLogEntry, history: List<CallLogEntry>): List<CallLogEntry> {
        val code = call.lookupResult.operatorCode.takeIf { call.lookupResult.isFound } ?: return listOf(call)
        return (history + call)
            .distinctBy { it.id }
            .filter {
                it.id == call.id || (
                    SignalConsoReport.isReportable(it) &&
                        it.lookupResult.isFound && it.lookupResult.operatorCode == code &&
                        (it.isSpamFlagged || it.lookupResult.numberType.isDemarchage || it.normalizedNumber == call.normalizedNumber)
                    )
            }
            .sortedBy { it.timestamp }
    }

    fun buildPlan(calls: List<CallLogEntry>, jalerteOperators: List<String>, timeZone: TimeZone): ArcepAlertPlan {
        require(calls.isNotEmpty())
        val first = calls.first()
        val operatorName = first.lookupResult.takeIf { it.isFound }?.operatorDisplayName
        return ArcepAlertPlan(
            numberTypes = calls.mapNotNull(::numberType).distinct(),
            operatorName = operatorName,
            jalerteOperator = operatorName?.let { matchOperator(it, jalerteOperators) },
            description = buildDescription(calls, timeZone),
            callCount = calls.size,
        )
    }

    private const val MAX_NUMBERS_LISTED = 30
    private const val MAX_DATES_PER_NUMBER = 5

    private fun buildDescription(calls: List<CallLogEntry>, timeZone: TimeZone): String {
        val dateTime = SimpleDateFormat("dd/MM/yyyy 'à' HH'h'mm", Locale.FRANCE).apply { this.timeZone = timeZone }
        val date = SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).apply { this.timeZone = timeZone }
        val lookup = calls.first().lookupResult
        val operator = lookup.operator
        val operatorLine = if (lookup.isFound) {
            buildString {
                append("${lookup.operatorDisplayName} (code ARCEP ${lookup.operatorCode}")
                operator?.siret?.takeIf { it.isNotBlank() }?.let { append(", SIRET ${it.trim()}") }
                append(")")
            }
        } else {
            null
        }
        return buildString {
            if (calls.size == 1) {
                val call = calls.single()
                append("Appel de démarchage commercial non sollicité reçu le ${dateTime.format(call.timestamp)} ")
                append("depuis le ${call.formattedNumber}.")
                if (operatorLine != null) {
                    if (lookup.range != null) {
                        append("\nCe numéro appartient à la tranche ${lookup.blockDisplay}, attribuée par l'Arcep à $operatorLine.")
                    } else {
                        append("\nCe numéro est attribué par l'Arcep à $operatorLine.")
                    }
                }
            } else {
                val byNumber = calls.groupBy { it.normalizedNumber }
                append("${calls.size} appels de démarchage commercial non sollicités reçus du ")
                append("${date.format(calls.first().timestamp)} au ${date.format(calls.last().timestamp)}")
                append(" depuis ${byNumber.size} numéro${if (byNumber.size > 1) "s" else ""}")
                if (operatorLine != null) append(" attribués par l'Arcep à $operatorLine")
                append(" :")
                byNumber.entries.sortedByDescending { it.value.size }.take(MAX_NUMBERS_LISTED).forEach { (_, numberCalls) ->
                    val shown = numberCalls.takeLast(MAX_DATES_PER_NUMBER).joinToString(", ") { dateTime.format(it.timestamp) }
                    val more = if (numberCalls.size > MAX_DATES_PER_NUMBER) ", …" else ""
                    append("\n- ${numberCalls.first().formattedNumber} : ${numberCalls.size} appel${if (numberCalls.size > 1) "s" else ""} ($shown$more)")
                }
                if (byNumber.size > MAX_NUMBERS_LISTED) append("\n- et ${byNumber.size - MAX_NUMBERS_LISTED} autres numéros.")
            }
            if (operatorLine != null) {
                append("\nL'appelant n'est pas identifiable depuis le numéro : je signale l'opérateur qui le lui fournit ")
                append("et qui doit veiller à l'usage que ses clients font de ses numéros.")
            }
            calls.mapNotNull { it.userNote?.trim()?.takeIf(String::isNotEmpty) }.distinct().take(3).forEach {
                append("\nMa note : $it")
            }
        }
    }

    // --- The operator as J'alerte l'Arcep's list names it ---

    // Legal names the ARCEP uses for operators the list knows by their brand.
    private val aliases = mapOf(
        "societe francaise du radiotelephone" to "sfr",
        "sfr fibre" to "sfr",
    )

    private val legalSuffixes = setOf("sas", "sa", "sarl", "sasu", "ltd", "limited", "eurl", "sl", "bv", "ab", "spa", "gmbh", "inc", "plc")

    /** Accents, case, punctuation and trailing legal forms ignored; "+" read as "plus" (Canal+). */
    internal fun normalizeName(name: String): String {
        val ascii = Normalizer.normalize(name.replace("+", " plus "), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
        val tokens = ascii.split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.toMutableList()
        while (tokens.isNotEmpty() && tokens.last() in legalSuffixes) tokens.removeAt(tokens.lastIndex)
        return tokens.joinToString(" ")
    }

    /**
     * The names one entry of the list goes by: "Kavokom / Kav El International" is either; in "Sosh · Orange"
     * the part after "·" is the host network, not a name; "BT Blue (ex Bretagne Telecom)" is also the latter.
     */
    internal fun entryNames(entry: String): Set<String> {
        val brand = entry.substringBefore(" · ")
        val parts = Regex("^(.*?)\\s*\\((.*)\\)\\s*$").find(brand)?.let { match ->
            listOf(match.groupValues[1]) + match.groupValues[2].split(",").map {
                it.trim().replace(Regex("^(ex|anciennement)\\s+", RegexOption.IGNORE_CASE), "")
            }
        } ?: listOf(brand)
        return (parts.flatMap { it.split(" / ") } + brand).map(::normalizeName).filter { it.isNotEmpty() }.toSet()
    }

    /**
     * The entry of J'alerte l'Arcep's operator list for the ARCEP's [arcepName], or null (the name then goes
     * into "Autre"). An entry named exactly so comes first ("Unixo" rather than "EINOVA / UNIXO"); else the
     * one entry known under that name; else the one entry whose name starts the ARCEP name ("Legos" for
     * "Legos-Local exchange global operation services"), or that the ARCEP name starts, with at least 5
     * letters so that "Free dial" is not taken for "Free".
     */
    fun matchOperator(arcepName: String, entries: List<String>): String? {
        val name = normalizeName(arcepName)
        if (name.isEmpty()) return null
        val wanted = listOfNotNull(name, aliases[name])
        val names = entries.associateWith(::entryNames)
        for (candidate in wanted) {
            entries.firstOrNull { normalizeName(it.substringBefore(" · ")) == candidate }?.let { return it }
            entries.filter { candidate in names.getValue(it) }.singleOrNull()?.let { return it }
        }
        val prefixes = entries.mapNotNull { entry ->
            names.getValue(entry).filter { it.length >= 5 && name.startsWith("$it ") }.maxOfOrNull { it.length }?.let { entry to it }
        }
        prefixes.maxOfOrNull { it.second }?.let { longest ->
            prefixes.filter { it.second == longest }.singleOrNull()?.let { return it.first }
        }
        if (name.length >= 5) {
            entries.filter { entry -> names.getValue(entry).any { it.startsWith("$name ") } }.singleOrNull()?.let { return it }
        }
        return null
    }
}
