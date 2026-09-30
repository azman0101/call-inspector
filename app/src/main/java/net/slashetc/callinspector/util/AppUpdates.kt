package net.slashetc.callinspector.util

import org.json.JSONObject

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
)

/**
 * Reads the app's GitHub releases: each build on main is published as tag v1.0.<build number> with its
 * signed APK (tools/publish_release.sh), and the build number is also the APK's versionCode, so comparing
 * it with the installed versionCode tells whether a newer version exists.
 */
object AppUpdates {

    private val tagRegex = Regex("""^v?(\d+(?:\.\d+)*)$""")
    private val pullRequestLine = Regex("""^\* (.+?) by @\S+ in https://github\.com/\S+/pull/(\d+)\s*$""")

    /** A release from the JSON of GET /repos/{owner}/{repo}/releases/latest (or /tags/{tag}); null if unusable. */
    fun parseRelease(json: String): AppRelease? = runCatching {
        val release = JSONObject(json)
        if (release.optBoolean("draft") || release.optBoolean("prerelease")) return null
        val versionName = tagRegex.find(release.getString("tag_name").trim())?.groupValues?.get(1) ?: return null
        val versionCode = versionName.substringAfterLast('.').toIntOrNull() ?: return null
        val assets = release.optJSONArray("assets")
        val apkUrl = (0 until (assets?.length() ?: 0))
            .map { assets!!.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
            ?.optString("browser_download_url")
            ?.takeIf { it.startsWith("https://") }
        AppRelease(
            versionCode = versionCode,
            versionName = versionName,
            pageUrl = release.getString("html_url"),
            apkUrl = apkUrl,
            notes = release.optString("body"),
        )
    }.getOrNull()

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
}
