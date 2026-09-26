package net.slashetc.callinspector.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArcepLookupResultTest {

    private val sampleOperator = ArcepOperator(
        code = "ORANG",
        name = "Orange",
        siret = "12345678900014",
        rcs = "RCS Paris B 123 456 789",
        address = "111 rue de de Paris, 75000 Paris",
        declarationDate = "2000-01-01"
    )

    private val sampleRange = ArcepNumberRange(
        id = 1L,
        ezabpqm = "0162000",
        trancheDebut = "0162000000",
        trancheFin = "0162009999",
        operatorCode = "ORANG",
        operatorName = "Orange",
        territory = "France Métropolitaine",
        attributionDate = "2022-01-01"
    )

    @Test
    fun `blockDisplay returns formatted range when range is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = sampleOperator,
            range = sampleRange,
            numberType = PhoneNumberType.DEMARCHAGE_COMMERCIAL,
            isFound = true
        )

        assertEquals("0162000000 à 0162009999", lookupResult.blockDisplay)
    }

    @Test
    fun `blockDisplay returns fallback string when range is null`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = null,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = false
        )

        assertEquals("Tranche non déterminée", lookupResult.blockDisplay)
    }

    @Test
    fun `blockDisplay returns formatted range with custom tranche values`() {
        val customRange = ArcepNumberRange(
            id = 2L,
            ezabpqm = "0937000",
            trancheDebut = "0937000000",
            trancheFin = "0937999999",
            operatorCode = "SFR",
            operatorName = "SFR"
        )
        val lookupResult = ArcepLookupResult(
            queryNumber = "0937000000",
            normalizedNumber = "0937000000",
            formattedNumber = "09 37 00 00 00",
            operator = null,
            range = customRange,
            numberType = PhoneNumberType.POLYVALENT_VOIP,
            isFound = true
        )

        assertEquals("0937000000 à 0937999999", lookupResult.blockDisplay)
    }

    @Test
    fun `officialArcepUrl defaults to official ARCEP portal URL`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = sampleOperator,
            range = sampleRange,
            numberType = PhoneNumberType.DEMARCHAGE_COMMERCIAL,
            isFound = true
        )

        assertEquals(
            "https://www.arcep.fr/mes-demarches-et-services/entreprises/fiches-pratiques/identifier-un-operateur-par-un-numero.html",
            lookupResult.officialArcepUrl
        )
    }

    @Test
    fun `operatorDisplayName returns operator name when operator is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = sampleOperator,
            range = null,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = true
        )

        assertEquals("Orange", lookupResult.operatorDisplayName)
    }

    @Test
    fun `operatorDisplayName returns range operator name when operator is null but range is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = sampleRange,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = true
        )

        assertEquals("Orange", lookupResult.operatorDisplayName)
    }

    @Test
    fun `operatorDisplayName returns default fallback string when operator and range are null`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = null,
            numberType = PhoneNumberType.INCONNU,
            isFound = false
        )

        assertEquals("Opérateur non identifié", lookupResult.operatorDisplayName)
    }

    @Test
    fun `operatorCode returns operator code when operator is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = sampleOperator,
            range = null,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = true
        )

        assertEquals("ORANG", lookupResult.operatorCode)
    }

    @Test
    fun `operatorCode returns range operator code when operator is null but range is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = sampleRange,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = true
        )

        assertEquals("ORANG", lookupResult.operatorCode)
    }

    @Test
    fun `operatorCode returns dash fallback when operator and range are null`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = null,
            numberType = PhoneNumberType.INCONNU,
            isFound = false
        )

        assertEquals("—", lookupResult.operatorCode)
    }

    @Test
    fun `territory returns range territory when range is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = sampleOperator,
            range = sampleRange,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = true
        )

        assertEquals("France Métropolitaine", lookupResult.territory)
    }

    @Test
    fun `territory returns fallback string when range is null`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = null,
            numberType = PhoneNumberType.INCONNU,
            isFound = false
        )

        assertEquals("France (Métropole ou Outre-Mer)", lookupResult.territory)
    }

    @Test
    fun `attributionDate returns range attribution date when range is present`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = sampleOperator,
            range = sampleRange,
            numberType = PhoneNumberType.FIXE_ILE_DE_FRANCE,
            isFound = true
        )

        assertEquals("2022-01-01", lookupResult.attributionDate)
    }

    @Test
    fun `attributionDate returns null when range is null`() {
        val lookupResult = ArcepLookupResult(
            queryNumber = "0162000000",
            normalizedNumber = "0162000000",
            formattedNumber = "01 62 00 00 00",
            operator = null,
            range = null,
            numberType = PhoneNumberType.INCONNU,
            isFound = false
        )

        assertNull(lookupResult.attributionDate)
    }
}
