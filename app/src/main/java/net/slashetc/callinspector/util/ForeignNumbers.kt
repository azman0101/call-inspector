package net.slashetc.callinspector.util

import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberToCarrierMapper
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import java.util.Locale

/** A number outside the numbering plan the ARCEP publishes, as libphonenumber reads it, on the phone. */
data class ForeignNumber(
    /** "+491734119637" */
    val e164: String,
    /** "+49 173 4119637" */
    val formatted: String,
    val countryCode: Int,
    /** ISO 3166 region ("DE"); null for a code shared by no country (+800, +882...) or unknown. */
    val regionCode: String?,
    /** "Allemagne"; "Réseau international" for a non-geographic code; "Indicatif +999" when unknown. */
    val countryName: String,
    /** "Mobile", "Fixe", "Numéro surtaxé"... */
    val lineType: String,
    /**
     * The mobile operator the number's range was given to ("Vodafone"), as for the ARCEP's ranges: the number
     * may have been ported to another one since. Null for fixed lines and unknown ranges.
     */
    val originalCarrier: String?,
    /** False when the number fits no range of its country's plan: unassigned, too short or too long, fictional. */
    val isValid: Boolean,
) {
    /** "Vodafone · Allemagne", or the country alone. */
    val summary: String get() = listOfNotNull(originalCarrier, countryName).joinToString(" · ")
}

/**
 * Numbers from abroad. The app's lookups (ARCEP ranges, SignalConso, J'alerte l'Arcep) only cover the French
 * plan; for any other number, libphonenumber and its carrier data, bundled in the app, give the country, the
 * kind of line and the original mobile operator, without any network request.
 */
object ForeignNumbers {

    /**
     * Country codes whose numbers the ARCEP publishes, and that stay French numbers here, with what replaces
     * "+code" to write them as the ARCEP does ([PhoneNumberFormatter.normalize]): metropolitan France (+33),
     * La Réunion and Mayotte (+262), Guadeloupe, Saint-Martin and Saint-Barthélemy (+590), Guyane (+594) and
     * Martinique (+596) become 0 then their 9 digits (+262 692... is 0692...). Saint-Pierre-et-Miquelon's
     * numbers have 6 digits, which the ARCEP writes after 0508 (+508 41 23 45 is 05 08 41 23 45).
     * New Caledonia (+687), French Polynesia (+689) and Wallis-and-Futuna (+681) have their own regulators,
     * and +269 is the Comoros (Mayotte uses +262): they are foreign numbers.
     */
    val ARCEP_COUNTRY_CODES = mapOf("33" to "0", "262" to "0", "508" to "0508", "590" to "0", "594" to "0", "596" to "0")

    /** Whether a number written by [PhoneNumberFormatter.normalize] is outside the French plan. */
    fun isForeign(normalizedNumber: String): Boolean = normalizedNumber.startsWith("+")

    /** What libphonenumber knows of a foreign number; null for a French number or one it cannot read. */
    fun describe(normalizedNumber: String): ForeignNumber? {
        if (!isForeign(normalizedNumber)) return null
        val util = PhoneNumberUtil.getInstance()
        val number = try {
            util.parse(normalizedNumber, "ZZ")
        } catch (e: NumberParseException) {
            return null
        }
        val region = (util.getRegionCodeForNumber(number) ?: util.getRegionCodeForCountryCode(number.countryCode))
            .takeIf { it != "ZZ" }
        return ForeignNumber(
            e164 = util.format(number, PhoneNumberFormat.E164),
            formatted = util.format(number, PhoneNumberFormat.INTERNATIONAL),
            countryCode = number.countryCode,
            regionCode = region?.takeIf { it != NON_GEOGRAPHIC },
            countryName = when (region) {
                null -> "Indicatif +${number.countryCode}"
                NON_GEOGRAPHIC -> "Réseau international"
                else -> Locale.Builder().setRegion(region).build().getDisplayCountry(Locale.FRENCH)
                    .ifBlank { "Indicatif +${number.countryCode}" }
            },
            lineType = lineTypeLabel(util.getNumberType(number)),
            originalCarrier = PhoneNumberToCarrierMapper.getInstance().getNameForNumber(number, Locale.FRENCH)
                .takeIf { it.isNotBlank() },
            isValid = util.isValidNumber(number),
        )
    }

    /** "+49 173 4119637" for a foreign number; null for a French number or one libphonenumber cannot read. */
    fun format(normalizedNumber: String): String? = describe(normalizedNumber)?.formatted

    // libphonenumber's region for the codes shared by no country (+800 freephone, +882 networks...).
    private const val NON_GEOGRAPHIC = "001"

    private fun lineTypeLabel(type: PhoneNumberType): String = when (type) {
        PhoneNumberType.MOBILE -> "Mobile"
        PhoneNumberType.FIXED_LINE -> "Fixe"
        PhoneNumberType.FIXED_LINE_OR_MOBILE -> "Fixe ou mobile"
        PhoneNumberType.TOLL_FREE -> "Numéro gratuit"
        PhoneNumberType.PREMIUM_RATE -> "Numéro surtaxé"
        PhoneNumberType.SHARED_COST -> "Numéro à coût partagé"
        PhoneNumberType.VOIP -> "Téléphonie par internet (VoIP)"
        PhoneNumberType.PERSONAL_NUMBER -> "Numéro personnel (renvoi)"
        PhoneNumberType.PAGER -> "Messageur (pager)"
        PhoneNumberType.UAN -> "Numéro d'entreprise"
        PhoneNumberType.VOICEMAIL -> "Messagerie vocale"
        PhoneNumberType.UNKNOWN -> "Type inconnu"
    }
}
