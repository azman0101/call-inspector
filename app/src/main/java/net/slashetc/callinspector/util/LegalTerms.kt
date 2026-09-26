package net.slashetc.callinspector.util

import android.content.Context

/**
 * The terms of use (CGU). legal/CGU.md at the repository root is the only source: Gradle bundles that
 * folder as assets, so the app shows exactly the file the repository publishes.
 * The version the user accepted is kept so a changed text is shown again for acceptance.
 */
object LegalTerms {
    const val ASSET_NAME = "CGU.md"

    private const val PREFS_NAME = "legal_terms"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"
    private val lastUpdatedRegex = Regex("Dernière mise à jour\\s*:\\s*([^*\\n]+)")

    fun load(context: Context): String =
        context.assets.open(ASSET_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** The "Dernière mise à jour" date, or a hash of the text when the date line is missing. */
    fun versionOf(markdown: String): String =
        lastUpdatedRegex.find(markdown)?.groupValues?.get(1)?.trim()
            ?: "sha:${markdown.hashCode().toUInt().toString(16)}"

    fun acceptedVersion(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_ACCEPTED_VERSION, null)

    fun needsAcceptance(context: Context, markdown: String): Boolean =
        acceptedVersion(context) != versionOf(markdown)

    fun accept(context: Context, markdown: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCEPTED_VERSION, versionOf(markdown))
            .apply()
    }
}
