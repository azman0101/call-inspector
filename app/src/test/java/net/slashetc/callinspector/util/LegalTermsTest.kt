package net.slashetc.callinspector.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegalTermsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("legal_terms", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `the app shows legal-CGU-md itself`() {
        // Single source of truth: the asset is the repository file, bundled via assets.srcDir("../legal").
        assertEquals(File("../legal/CGU.md").readText(), LegalTerms.load(context))
    }

    @Test
    fun `the version is the last update date`() {
        assertEquals("26 septembre 2026", LegalTerms.versionOf("*Dernière mise à jour : 26 septembre 2026*\n\nTexte"))
        val withoutDate = LegalTerms.versionOf("Texte sans date")
        assertTrue(withoutDate.startsWith("sha:"))
        assertNotEquals(withoutDate, LegalTerms.versionOf("Autre texte sans date"))
    }

    @Test
    fun `terms must be accepted again when their version changes`() {
        val terms = LegalTerms.load(context)
        assertTrue(LegalTerms.needsAcceptance(context, terms))
        LegalTerms.accept(context, terms)
        assertFalse(LegalTerms.needsAcceptance(context, terms))
        val updated = terms.replace(LegalTerms.versionOf(terms), "1er janvier 2027")
        assertTrue(LegalTerms.needsAcceptance(context, updated))
    }
}
