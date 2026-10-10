package net.slashetc.callinspector.util

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A published release of the app, as the GitHub releases API describes it. */
data class AppRelease(
    /** The build number: the last part of the tag (v1.0.174 -> 174), which is the release APK's versionCode. */
    val versionCode: Int,
    /** The tag without its "v" (1.0.174). */
    val versionName: String,
    /** The release page on GitHub. */
    val pageUrl: String,
    /** The signed APK attached to the release, null when there is none (the page then offers it). */
    val apkUrl: String?,
    /** The release notes, Markdown as published by tools/publish_release.sh. */
    val notes: String,
    /**
     * What users read of it ([AppUpdates.changes] of the notes). For a version several releases after the
     * installed or last opened one, the changes of all those releases ([AppUpdates.since]).
     */
    val changes: List<String> = AppUpdates.changes(notes),
    /** When [changes] span several releases, the version they follow (installed, or last opened); else null. */
    val changesSince: String? = null,
)

/** The sections of a release's changes, from the conventional prefix of a pull request title. */
enum class ChangeKind(val title: String) {
    FEATURE("Nouveautés"),
    FIX("Correctifs"),
    OTHER("Autres changements"),
}

/**
 * Reads the app's GitHub releases: the builds on main are published as tag v1.0.<build number> with their
 * signed APK (tools/publish_release.sh; merges close together share one release), and the build number is
 * also the APK's versionCode, so comparing it with the installed versionCode tells whether a newer version
 * exists.
 */
object AppUpdates {

    private val tagRegex = Regex("""^v?(\d+(?:\.\d+)*)$""")
    private val conventionalTitle = Regex("""^(\w+)(?:\([^)]*\))?!?:\s*(.+)$""")
    private val pullRequestLine = Regex("""^\* (.+?) by @\S+ in https://github\.com/\S+/pull/(\d+)\s*$""")

    /** A release from the JSON of GET /repos/{owner}/{repo}/releases/latest (or /tags/{tag}); null if unusable. */
    fun parseRelease(json: String): AppRelease? = runCatching { parseRelease(JSONObject(json)) }.getOrNull()

    /** The usable releases of GET /repos/{owner}/{repo}/releases, or of [toJson]; null if the JSON is not a list. */
    fun parseReleases(json: String): List<AppRelease>? = runCatching {
        val releases = JSONArray(json)
        (0 until releases.length()).mapNotNull { index ->
            releases.optJSONObject(index)?.let { runCatching { parseRelease(it) }.getOrNull() }
        }
    }.getOrNull()

    /** How many entries a releases list holds, usable or not; 0 if the JSON is not a list. */
    fun entryCount(json: String): Int = runCatching { JSONArray(json).length() }.getOrDefault(0)

    /**
     * [releases] as the releases API gives them, for [parseReleases] to read back. The notes keep only what
     * [changes] reads, from "## What's Changed" on: the install and checksum header is left out.
     */
    fun toJson(releases: List<AppRelease>): String = JSONArray(
        releases.map { release ->
            val changesStart = release.notes.lineSequence()
                .indexOfFirst { it.trim().equals("## What's Changed", ignoreCase = true) }
            JSONObject()
                .put("tag_name", "v" + release.versionName)
                .put("html_url", release.pageUrl)
                .put("body", if (changesStart < 0) release.notes else release.notes.lines().drop(changesStart).joinToString("\n"))
                .put("assets", JSONArray(listOfNotNull(release.apkUrl?.let {
                    JSONObject().put("name", it.substringAfterLast('/')).put("browser_download_url", it)
                })))
        }
    ).toString()

    private fun parseRelease(release: JSONObject): AppRelease? {
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val versionName = tagRegex.find(release.getString("tag_name").trim())?.groupValues?.get(1) ?: return null
        val versionCode = versionName.substringAfterLast('.').toIntOrNull() ?: return null
        val assets = release.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
        return AppRelease(
            versionCode = versionCode,
            versionName = versionName,
            pageUrl = release.getString("html_url"),
            apkUrl = apkUrl,
            notes = release.optString("body"),
        )
    }

    fun isNewer(release: AppRelease, installedVersionCode: Int): Boolean = release.versionCode > installedVersionCode

    /**
     * The changes a release brings, for users: the pull request titles of its "What's Changed" section
     * ("feat: export a search's calls (#50)"), without the checksums, signing certificate and build links
     * that come before it, nor the authors and URLs.
     */
    fun changes(notes: String): List<String> {
        val section = notes.lineSequence()
            .dropWhile { !it.trim().equals("## What's Changed", ignoreCase = true) }
            .drop(1)
            .takeWhile { !it.startsWith("## ") && !it.startsWith("**Full Changelog**") }
        return section.mapNotNull { line ->
            val trimmed = line.trim()
            pullRequestLine.find(trimmed)?.let { "${it.groupValues[1].trim()} (#${it.groupValues[2]})" }
                ?: trimmed.removePrefix("* ").trim().takeIf { trimmed.startsWith("* ") && it.isNotEmpty() }
        }.toList()
    }

    /**
     * [target] with the changes of every release after version [afterCode] up to it, oldest first, each once:
     * a user who skipped versions sees all they bring, not only the last one's. [afterName] names the version
     * they follow. [target] as it is when no other release is in between.
     */
    fun since(releases: List<AppRelease>, target: AppRelease, afterCode: Int, afterName: String?): AppRelease {
        val covered = (releases.filter { it.versionCode != target.versionCode } + target)
            .filter { it.versionCode > afterCode && it.versionCode <= target.versionCode }
            .sortedBy { it.versionCode }
        if (covered.size <= 1) return target
        return target.copy(changes = covered.flatMap { it.changes }.distinct(), changesSince = afterName)
    }

    /**
     * [changes] by kind, in the order of [ChangeKind], without the empty ones. The conventional prefix of the
     * pull request title decides, and is dropped: "feat(tile): a tile (#54)" is the feature "A tile (#54)".
     * Other titles are kept as they are, in [ChangeKind.OTHER].
     */
    fun grouped(changes: List<String>): Map<ChangeKind, List<String>> {
        val byKind = changes.map { change ->
            val match = conventionalTitle.find(change) ?: return@map ChangeKind.OTHER to change
            val kind = when (match.groupValues[1].lowercase(Locale.ROOT)) {
                "feat" -> ChangeKind.FEATURE
                "fix", "perf", "security" -> ChangeKind.FIX
                else -> ChangeKind.OTHER
            }
            kind to match.groupValues[2].replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        return ChangeKind.entries
            .associateWith { kind -> byKind.filter { it.first == kind }.map { it.second } }
            .filterValues { it.isNotEmpty() }
    }
}
