package net.slashetc.callinspector.util

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// org.json is Android's: Robolectric provides the real implementation.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdatesTest {

    // Shaped like the notes tools/publish_release.sh publishes (release v1.0.174).
    private val notes = """
        ## Installer

        Téléchargez `info-operateur-1.0.174.apk` dans les *Assets* ci-dessous.

        ## Vérifier

        - SHA-256 de l'APK : `b1af1120`

        ## What's Changed
        * feat: export a search's calls to the clipboard as Markdown or CSV by @azman0101 in https://github.com/azman0101/call-inspector/pull/50
        * ci: publish every main build as a GitHub release with its signed APK by @azman0101 in https://github.com/azman0101/call-inspector/pull/51
        * docs: a change without a pull request link

        ## New Contributors
        * @azman0101 made their first contribution in https://github.com/azman0101/call-inspector/pull/1

        **Full Changelog**: https://github.com/azman0101/call-inspector/commits/v1.0.174
    """.trimIndent()

    private fun releaseJson(
        tag: String = "v1.0.174",
        draft: Boolean = false,
        prerelease: Boolean = false,
        assets: List<Pair<String, String>> = listOf(
            "info-operateur-1.0.174.apk" to "https://github.com/azman0101/call-inspector/releases/download/v1.0.174/info-operateur-1.0.174.apk",
            "info-operateur-1.0.174.apk.sha256" to "https://github.com/azman0101/call-inspector/releases/download/v1.0.174/info-operateur-1.0.174.apk.sha256",
        ),
    ) = JSONObject()
        .put("tag_name", tag)
        .put("draft", draft)
        .put("prerelease", prerelease)
        .put("html_url", "https://github.com/azman0101/call-inspector/releases/tag/$tag")
        .put("body", notes)
        .put("assets", JSONArray(assets.map { (name, url) -> JSONObject().put("name", name).put("browser_download_url", url) }))
        .toString()

    @Test
    fun `a release gives its build number, version name, page, apk and notes`() {
        val release = AppUpdates.parseRelease(releaseJson())!!

        assertEquals(174, release.versionCode)
        assertEquals("1.0.174", release.versionName)
        assertEquals("https://github.com/azman0101/call-inspector/releases/tag/v1.0.174", release.pageUrl)
        assertEquals(
            "https://github.com/azman0101/call-inspector/releases/download/v1.0.174/info-operateur-1.0.174.apk",
            release.apkUrl
        )
        assertEquals(notes, release.notes)
    }

    @Test
    fun `drafts, pre-releases, other tags and broken JSON are ignored`() {
        assertNull(AppUpdates.parseRelease(releaseJson(draft = true)))
        assertNull(AppUpdates.parseRelease(releaseJson(prerelease = true)))
        assertNull(AppUpdates.parseRelease(releaseJson(tag = "nightly")))
        assertNull(AppUpdates.parseRelease(releaseJson(tag = "v1.0.174-beta")))
        assertNull(AppUpdates.parseRelease("{not json"))
        assertNull(AppUpdates.parseRelease("""{"html_url": "https://example.invalid"}"""))
    }

    @Test
    fun `without an apk asset the release page is the download`() {
        val release = AppUpdates.parseRelease(releaseJson(assets = listOf("notes.txt" to "https://example.invalid/notes.txt")))!!

        assertNull(release.apkUrl)
        assertEquals(174, release.versionCode)
    }

    @Test
    fun `a release is newer only with a higher build number`() {
        val release = AppUpdates.parseRelease(releaseJson())!!

        assertTrue(AppUpdates.isNewer(release, 173))
        assertFalse(AppUpdates.isNewer(release, 174))
        assertFalse(AppUpdates.isNewer(release, 180))
    }

    @Test
    fun `changes lists the pull requests, without authors, links or the other sections`() {
        assertEquals(
            listOf(
                "feat: export a search's calls to the clipboard as Markdown or CSV (#50)",
                "ci: publish every main build as a GitHub release with its signed APK (#51)",
                "docs: a change without a pull request link",
            ),
            AppUpdates.changes(notes)
        )
        assertTrue(AppUpdates.changes("## Installer\n\nRien d'autre.").isEmpty())
    }

    @Test
    fun `the headings that sort the changes of a release are skipped`() {
        // Notes sorted by tools/group_release_notes.py.
        val sorted = """
            ## What's Changed

            ### Nouveautés
            * feat: a tile by @azman0101 in https://github.com/azman0101/call-inspector/pull/54

            ### Correctifs
            * fix: date labels by @azman0101 in https://github.com/azman0101/call-inspector/pull/55

            **Full Changelog**: https://github.com/azman0101/call-inspector/compare/v1.0.174...v1.0.180
        """.trimIndent()

        assertEquals(listOf("feat: a tile (#54)", "fix: date labels (#55)"), AppUpdates.changes(sorted))
    }

    @Test
    fun `a releases list is read back from its stored copy`() {
        val json = "[" + releaseJson("v1.0.180") + "," + releaseJson("v1.0.179", draft = true) + "," + releaseJson("v1.0.174") + "]"
        val releases = AppUpdates.parseReleases(json)!!

        assertEquals(listOf(180, 174), releases.map { it.versionCode })
        val stored = AppUpdates.parseReleases(AppUpdates.toJson(releases))!!
        assertEquals(releases.map { it.copy(notes = "") }, stored.map { it.copy(notes = "") })
        // Only what the app reads is stored: the changes, not the install and checksum header.
        assertEquals(releases.map { it.changes }, stored.map { it.changes })
        assertFalse(stored[0].notes.contains("SHA-256"))
        assertEquals(emptyList<AppRelease>(), AppUpdates.parseReleases("[]"))
        assertNull(AppUpdates.parseReleases(releaseJson()))
    }

    private fun release(build: Int, vararg changes: String) = AppRelease(
        versionCode = build,
        versionName = "1.0.$build",
        pageUrl = "https://github.com/azman0101/call-inspector/releases/tag/v1.0.$build",
        apkUrl = null,
        notes = "## What's Changed\n" + changes.joinToString("\n") { "* $it" },
    )

    @Test
    fun `a version several releases ahead brings all their changes, oldest first, each once`() {
        val releases = listOf(
            release(182, "fix: third", "feat: second"),
            release(181, "feat: second"),
            release(180, "feat: first"),
            release(174, "feat: installed"),
        )

        val update = AppUpdates.since(releases, releases[0], afterCode = 174, afterName = "1.0.174")
        assertEquals(listOf("feat: first", "feat: second", "fix: third"), update.changes)
        assertEquals("1.0.174", update.changesSince)
        assertEquals(182, update.versionCode)

        val next = AppUpdates.since(releases, releases[0], afterCode = 181, afterName = "1.0.181")
        assertEquals(releases[0], next)
        assertNull(next.changesSince)
    }

    @Test
    fun `changes are sorted into features, fixes and the rest, without their prefix`() {
        val grouped = AppUpdates.grouped(
            listOf(
                "ci: one release per wave of merges (#66)",
                "feat(tile): \"Qui m'a appelé ?\" quick settings tile (#54)",
                "fix: date labels count calendar days (#55)",
                "security(SR-01): keep the reporter's data out of page globals (#58)",
                "Bump okhttp from 4 to 5 (#70)",
                "perf!: faster lookups (#49)",
                "feat: a long press copies a value (#63)",
            )
        )

        assertEquals(listOf(ChangeKind.FEATURE, ChangeKind.FIX, ChangeKind.OTHER), grouped.keys.toList())
        assertEquals(listOf("\"Qui m'a appelé ?\" quick settings tile (#54)", "A long press copies a value (#63)"), grouped[ChangeKind.FEATURE])
        assertEquals(
            listOf("Date labels count calendar days (#55)", "Keep the reporter's data out of page globals (#58)", "Faster lookups (#49)"),
            grouped[ChangeKind.FIX]
        )
        assertEquals(listOf("One release per wave of merges (#66)", "Bump okhttp from 4 to 5 (#70)"), grouped[ChangeKind.OTHER])
        assertEquals(listOf(ChangeKind.FIX), AppUpdates.grouped(listOf("fix: only a fix")).keys.toList())
        assertTrue(AppUpdates.grouped(emptyList()).isEmpty())
    }
}
