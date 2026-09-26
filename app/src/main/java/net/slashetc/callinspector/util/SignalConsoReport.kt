package net.slashetc.callinspector.util

import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** SignalConso "Démarchage abusif > Problème de démarchage téléphonique" sub-options, by their exact label. */
enum class DemarchageCase(val label: String) {
    ADMINISTRATION("Je suis démarché par un opérateur se faisant passer pour l'administration"),
    RENOVATION("Je reçois des appels pour effectuer des travaux ou de la rénovation énergétique"),
    CPF("Je reçois des appels pour effectuer des formations en utilisant mon CPF"),
    REPEATED_CALLS("J'ai reçu au moins 5 appels de la même entreprise sur les 30 derniers jours"),
    WEEKEND_OR_HOLIDAY("J'ai reçu un appel commercial pendant un week-end ou un jour férié ?"),
    OUTSIDE_WEEKDAY_HOURS("J'ai reçu un appel commercial pendant la semaine en dehors des heures autorisées"),
    REFUSED_WITHIN_60_DAYS("J'ai reçu un appel d'une entreprise à qui j'avais demandé de ne pas être démarché moins de 60 jours après mon refus"),
    MOBILE_NUMBER("Je suis démarché par un opérateur utilisant un numéro commençant par 06 ou 07"),

    /** Not a SignalConso report: the form only points to bloctel.gouv.fr. Last resort, see [SignalConsoPlan]. */
    BLOCTEL("Je reçois des appels indésirables alors que je suis inscrit sur Bloctel (ces appels ne concernent ni la rénovation énergétique ni le CPF)"),
}

/** The company named in the report: the caller when known, otherwise the operator holding the number. */
data class ReportedCompany(
    val source: Source,
    val name: String,
    val siret: String?,
) {
    enum class Source { CALLER_NAME, OPERATOR }
}

/**
 * What the prefill script selects and fills; the user reviews and submits every step. When no case applies,
 * [bloctelSubcategory] is selected instead if the user said they are registered on Bloctel (a setting
 * of their locally stored profile, which the plan is built without).
 */
data class SignalConsoPlan(
    val subcategory: String?,
    val bloctelSubcategory: String?,
    val phone: String?,
    val dates: List<String>,
    val company: ReportedCompany?,
    val description: String,
)

object SignalConsoReport {
    const val URL = "https://signal.conso.gouv.fr/fr/demarchage-abusif/faire-un-signalement"
    const val PROBLEM = "Problème de démarchage téléphonique"
    const val HOST = "signal.conso.gouv.fr"

    private const val REPEATED_CALLS_THRESHOLD = 5
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

    fun isReportable(call: CallLogEntry): Boolean = call.callType != CallType.OUTGOING

    /** Calls received from the same number in the 30 days before [now], oldest first. */
    fun recentCallsFromSameNumber(call: CallLogEntry, history: List<CallLogEntry>, now: Long): List<CallLogEntry> =
        (history + call)
            .distinctBy { it.id }
            .filter {
                isReportable(it) &&
                    it.normalizedNumber == call.normalizedNumber &&
                    it.timestamp in (now - THIRTY_DAYS_MS)..now
            }
            .sortedBy { it.timestamp }

    // Subjects of the call, as the user describes them in their note or contact name (accents and case ignored).
    private val topicKeywords = listOf(
        DemarchageCase.ADMINISTRATION to Regex(
            "\\b(administration|gouvernement|ministere|caf|impots?|urssaf|ameli|cpam|prefecture|mairie|service public|" +
                "france travail|pole emploi|se fai(t|sant) passer)\\b"
        ),
        DemarchageCase.RENOVATION to Regex(
            "\\b(renovation|isolation|isoler|pompes? a chaleur|pac|panneaux? solaires?|photovoltaique|travaux|fenetres?|" +
                "chaudieres?|combles|maprimerenov|anah|france renov|dpe|audit energetique|bilan energetique|vmc)\\b"
        ),
        DemarchageCase.CPF to Regex("\\b(cpf|compte personnel de formation|formations?)\\b"),
        DemarchageCase.REFUSED_WITHIN_60_DAYS to Regex(
            "\\b(refus|refuse|deja dit non|ne plus m'appeler|ne plus appeler|opposition)\\b"
        ),
    )

    internal fun topicCases(text: String?): Set<DemarchageCase> {
        if (text.isNullOrBlank()) return emptySet()
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace('’', '\'')
            .lowercase(Locale.FRANCE)
        return topicKeywords.filter { (_, regex) -> regex.containsMatchIn(normalized) }.map { it.first }.toSet()
    }

    /**
     * Applicable SignalConso cases for [call], most useful first: what the call was about (from the user's
     * note or the caller's name), then what its date, time, number and history show.
     */
    fun applicableCases(
        call: CallLogEntry,
        history: List<CallLogEntry>,
        now: Long,
        timeZone: TimeZone,
    ): List<DemarchageCase> {
        val cal = Calendar.getInstance(timeZone).apply { timeInMillis = call.timestamp }
        val topics = topicCases(call.userNote) + topicCases(call.cachedName)
        return buildList {
            if (DemarchageCase.ADMINISTRATION in topics) add(DemarchageCase.ADMINISTRATION)
            if (DemarchageCase.RENOVATION in topics) add(DemarchageCase.RENOVATION)
            if (DemarchageCase.CPF in topics) add(DemarchageCase.CPF)
            if (recentCallsFromSameNumber(call, history, now).size >= REPEATED_CALLS_THRESHOLD) add(DemarchageCase.REPEATED_CALLS)
            if (isWeekendOrHoliday(cal)) add(DemarchageCase.WEEKEND_OR_HOLIDAY)
            else if (isOutsideWeekdayHours(cal)) add(DemarchageCase.OUTSIDE_WEEKDAY_HOURS)
            if (DemarchageCase.REFUSED_WITHIN_60_DAYS in topics) add(DemarchageCase.REFUSED_WITHIN_60_DAYS)
            if (call.normalizedNumber.startsWith("06") || call.normalizedNumber.startsWith("07")) add(DemarchageCase.MOBILE_NUMBER)
        }
    }

    fun buildPlan(call: CallLogEntry, history: List<CallLogEntry>, now: Long, timeZone: TimeZone): SignalConsoPlan {
        val cases = applicableCases(call, history, now, timeZone)
        val recent = recentCallsFromSameNumber(call, history, now)
        val isoDate = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).apply { this.timeZone = timeZone }
        val dates = if (cases.firstOrNull() == DemarchageCase.REPEATED_CALLS) {
            recent.takeLast(REPEATED_CALLS_THRESHOLD).map { isoDate.format(it.timestamp) }
        } else {
            listOf(isoDate.format(call.timestamp))
        }
        val company = reportedCompany(call)
        return SignalConsoPlan(
            subcategory = cases.firstOrNull()?.label,
            bloctelSubcategory = DemarchageCase.BLOCTEL.label.takeIf { cases.isEmpty() },
            // The form only accepts French numbers: an international number is left for the user to type.
            phone = call.normalizedNumber.takeIf { it.length == 10 && it.startsWith("0") && it.all(Char::isDigit) },
            dates = dates,
            company = company,
            description = buildDescription(call, cases, recent.size, company, timeZone),
        )
    }

    /**
     * The caller's name when the call log has one; otherwise the operator the ARCEP assigned the number to,
     * which rents it to the caller and is reported in its place.
     */
    fun reportedCompany(call: CallLogEntry): ReportedCompany? {
        call.cachedName?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return ReportedCompany(ReportedCompany.Source.CALLER_NAME, it, siret = null)
        }
        val lookup = call.lookupResult
        if (!lookup.isFound) return null
        val name = (lookup.operator?.name ?: lookup.range?.operatorName)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        // SignalConso searches by SIRET (14 digits) or SIREN (9 digits).
        val siret = lookup.operator?.siret?.filter { !it.isWhitespace() }?.takeIf { id -> (id.length == 14 || id.length == 9) && id.all(Char::isDigit) }
        return ReportedCompany(ReportedCompany.Source.OPERATOR, name, siret)
    }

    private fun buildDescription(
        call: CallLogEntry,
        cases: List<DemarchageCase>,
        recentCount: Int,
        company: ReportedCompany?,
        timeZone: TimeZone,
    ): String {
        val dateTime = SimpleDateFormat("dd/MM/yyyy 'à' HH'h'mm", Locale.FRANCE).apply { this.timeZone = timeZone }
        return buildString {
            append("Appel de démarchage reçu le ${dateTime.format(call.timestamp)} depuis le ${call.formattedNumber}.")
            if (DemarchageCase.ADMINISTRATION in cases) append("\nL'appelant se faisait passer pour l'administration.")
            if (DemarchageCase.RENOVATION in cases) append("\nL'appel portait sur des travaux ou de la rénovation énergétique.")
            if (DemarchageCase.CPF in cases) append("\nL'appel portait sur des formations financées par le CPF.")
            if (DemarchageCase.WEEKEND_OR_HOLIDAY in cases) append("\nL'appel a eu lieu un samedi, un dimanche ou un jour férié.")
            if (DemarchageCase.OUTSIDE_WEEKDAY_HOURS in cases) append("\nL'appel a eu lieu en dehors des horaires autorisés (10h-13h et 14h-20h en semaine).")
            if (DemarchageCase.REFUSED_WITHIN_60_DAYS in cases) append("\nJ'avais déjà refusé d'être démarché par cette entreprise.")
            if (recentCount > 1) append("\n$recentCount appels reçus de ce numéro sur les 30 derniers jours.")
            if (DemarchageCase.MOBILE_NUMBER in cases) append("\nLe démarcheur utilise un numéro mobile (06/07).")
            val lookup = call.lookupResult
            if (lookup.isFound) append("\nNuméro attribué par l'ARCEP à l'opérateur ${lookup.operatorDisplayName}.")
            if (company?.source == ReportedCompany.Source.OPERATOR) {
                append("\nL'entreprise à l'origine de l'appel n'ayant pas pu être identifiée, je signale l'opérateur qui lui fournit ce numéro.")
            }
            call.userNote?.takeIf { it.isNotBlank() }?.let { append("\nMa note : ${it.trim()}") }
        }
    }

    // Commercial calls are allowed on weekdays 10h-13h and 14h-20h (décret n° 2022-1313).
    internal fun isOutsideWeekdayHours(cal: Calendar): Boolean {
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour < 10 || hour == 13 || hour >= 20
    }

    internal fun isWeekendOrHoliday(cal: Calendar): Boolean {
        val day = cal.get(Calendar.DAY_OF_WEEK)
        return day == Calendar.SATURDAY || day == Calendar.SUNDAY || isFrenchPublicHoliday(cal)
    }

    // Metropolitan public holidays (Code du travail, art. L3133-1).
    internal fun isFrenchPublicHoliday(cal: Calendar): Boolean {
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH) + 1
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val fixed = setOf(1 to 1, 5 to 1, 5 to 8, 7 to 14, 8 to 15, 11 to 1, 11 to 11, 12 to 25)
        if ((month to day) in fixed) return true
        val easter = easterSunday(year, cal.timeZone)
        return listOf(1, 39, 50).any { offset ->
            val holiday = (easter.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, offset) }
            holiday.get(Calendar.MONTH) + 1 == month && holiday.get(Calendar.DAY_OF_MONTH) == day
        }
    }

    // Anonymous Gregorian algorithm (Meeus/Jones/Butcher).
    internal fun easterSunday(year: Int, timeZone: TimeZone): Calendar {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(year, month - 1, day)
        }
    }
}
