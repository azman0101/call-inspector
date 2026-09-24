package com.example.data.model

enum class PhoneNumberType(
    val label: String,
    val description: String,
    val isDemarchage: Boolean = false,
    val isTollFreeOrSva: Boolean = false
) {
    MOBILE(
        label = "Mobile",
        description = "Numéro mobile personnel ou professionnel (06/07)"
    ),
    DEMARCHAGE_COMMERCIAL(
        label = "Démarchage Réglementé",
        description = "Réservé exclusivement aux plateformes de télémarketing & démarchage (ARCEP)",
        isDemarchage = true
    ),
    FIXE_ILE_DE_FRANCE(
        label = "Fixe - Île-de-France",
        description = "Zone géographique Région Parisienne (01)"
    ),
    FIXE_NORD_OUEST(
        label = "Fixe - Nord-Ouest",
        description = "Bretagne, Normandie, Pays de la Loire, Centre-Val de Loire (02)"
    ),
    FIXE_NORD_EST(
        label = "Fixe - Nord-Est",
        description = "Hauts-de-France, Grand Est, Bourgogne-Franche-Comté (03)"
    ),
    FIXE_SUD_EST(
        label = "Fixe - Sud-Est",
        description = "Auvergne-Rhône-Alpes, PACA, Corse (04)"
    ),
    FIXE_SUD_OUEST(
        label = "Fixe - Sud-Ouest",
        description = "Nouvelle-Aquitaine, Occitanie (05)"
    ),
    FIXE_OUTRE_MER(
        label = "Outre-Mer (DOM)",
        description = "Guadeloupe, Martinique, Guyane, Réunion, Mayotte, etc."
    ),
    SVA_SPECIAL(
        label = "Numéro Spécial / SVA",
        description = "Numéro d'entreprise ou service à valeur ajoutée (08)"
    ),
    POLYVALENT_VOIP(
        label = "Numéro Polyvalent / Box",
        description = "Numéro fixe national indépendant de la localisation (09)"
    ),
    NUMERO_COURT(
        label = "Numéro Court / Urgence",
        description = "Services d'urgence ou numéros courts à 4 ou 6 chiffres"
    ),
    INTERNATIONAL(
        label = "International",
        description = "Numéro situé hors du plan de numérotation français"
    ),
    INCONNU(
        label = "Non identifié",
        description = "Numéro sans tranche répertoriée"
    );

    companion object {
        // Official French regulatory prefixes for telemarketing/cold calling
        // Implemented by ARCEP Decision 2022-1583 & Decree 2022-1313 (in effect since Jan 1, 2023)
        private val DEMARCHAGE_PREFIXES = listOf(
            "0162", "0163",
            "0270", "0271",
            "0377", "0378",
            "0424", "0425",
            "0568", "0569",
            "0948", "0949",
            "09475", "09476", "09477", "09478", "09479", "09480", "09481", "09482"
        )

        fun classify(normalizedNumber: String): PhoneNumberType {
            if (normalizedNumber.isBlank()) return INCONNU

            // Check demarchage first as it takes precedence
            for (prefix in DEMARCHAGE_PREFIXES) {
                if (normalizedNumber.startsWith(prefix)) {
                    return DEMARCHAGE_COMMERCIAL
                }
            }

            if (normalizedNumber.length in 3..6 && !normalizedNumber.startsWith("0")) {
                return NUMERO_COURT
            }

            return when {
                normalizedNumber.startsWith("059") || normalizedNumber.startsWith("026") || normalizedNumber.startsWith("069") -> FIXE_OUTRE_MER
                normalizedNumber.startsWith("06") || normalizedNumber.startsWith("07") -> MOBILE
                normalizedNumber.startsWith("01") -> FIXE_ILE_DE_FRANCE
                normalizedNumber.startsWith("02") -> FIXE_NORD_OUEST
                normalizedNumber.startsWith("03") -> FIXE_NORD_EST
                normalizedNumber.startsWith("04") -> FIXE_SUD_EST
                normalizedNumber.startsWith("05") -> FIXE_SUD_OUEST
                normalizedNumber.startsWith("08") -> SVA_SPECIAL
                normalizedNumber.startsWith("09") -> POLYVALENT_VOIP
                normalizedNumber.startsWith("+") && !normalizedNumber.startsWith("+33") -> INTERNATIONAL
                else -> INCONNU
            }
        }
    }
}
