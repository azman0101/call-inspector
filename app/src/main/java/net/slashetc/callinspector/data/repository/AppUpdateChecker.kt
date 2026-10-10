package net.slashetc.callinspector.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.slashetc.callinspector.BuildConfig
import net.slashetc.callinspector.util.AppRelease
import net.slashetc.callinspector.util.AppUpdates
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Tells whether a newer version of the app is published on GitHub, and what the installed version brings.
 * Installed outside the Play Store, the app has no other way to learn about updates.
 *
 * Only reads the public GitHub releases API (api.github.com), at most once a day unless the user asks, and
 * only while the user leaves "Vérifier les mises à jour" on (CGU article 4). Nothing about the user is sent.
 */
class AppUpdateChecker internal constructor(
    context: Context,
    private val client: OkHttpClient,
    private val installedVersionCode: Int,
    private val installedVersionName: String,
    private val now: () -> Long = System::currentTimeMillis,
) {
    constructor(context: Context) : this(context, defaultClient, BuildConfig.VERSION_CODE, BuildConfig.VERSION_NAME)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /**
     * Debug builds only: also offer newer releases automatically. Off by default, since a release APK does
     * not update a debug build (another app, ".debug") but installs next to it.
     */
    var isEnabledInDebug: Boolean
        get() = prefs.getBoolean(KEY_ENABLED_IN_DEBUG, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED_IN_DEBUG, value).apply()

    /**
     * The newest release to offer, or null, with the changes of every release since the installed version.
     * GitHub is asked at most once every [CHECK_INTERVAL_MS] unless [force]; in between, the last answer is
     * reused. A release the user dismissed is not offered again.
     */
    suspend fun availableUpdate(force: Boolean = false): AppRelease? {
        if (!isEnabled) return null
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (force || now() - lastCheck >= CHECK_INTERVAL_MS || !prefs.contains(KEY_NEWER_JSON)) {
            fetchReleases()?.let { releases ->
                // Only the releases after the installed version are kept: the banner needs no other.
                val newer = releases.filter { AppUpdates.isNewer(it, installedVersionCode) }
                prefs.edit()
                    .putString(KEY_NEWER_JSON, AppUpdates.toJson(newer))
                    .putLong(KEY_LAST_CHECK, now())
                    .remove(KEY_LATEST_JSON)
                    .apply()
            }
        }
        val newer = prefs.getString(KEY_NEWER_JSON, null)?.let { AppUpdates.parseReleases(it) }.orEmpty()
        val latest = newer.maxByOrNull { it.versionCode } ?: return null
        if (!AppUpdates.isNewer(latest, installedVersionCode) || latest.versionCode == prefs.getInt(KEY_DISMISSED_CODE, 0)) {
            return null
        }
        return AppUpdates.since(newer, latest, installedVersionCode, installedVersionName)
    }

    fun dismiss(release: AppRelease) {
        prefs.edit().putInt(KEY_DISMISSED_CODE, release.versionCode).apply()
    }

    /**
     * The installed version's release, the first time it runs after an update, with the changes of every
     * release since the version last opened; null on a fresh install, once shown, for a version that is not
     * published, or when GitHub cannot be reached (it is then tried again at the next launch).
     */
    suspend fun whatsNewAfterUpdate(): AppRelease? {
        val lastSeen = prefs.getInt(KEY_LAST_SEEN_CODE, 0)
        if (lastSeen == 0 || lastSeen > installedVersionCode) {
            rememberSeen()
            return null
        }
        if (lastSeen == installedVersionCode || !isEnabled) return null
        val releases = fetchReleases() ?: return null
        val lastSeenName = prefs.getString(KEY_LAST_SEEN_NAME, null)
            ?: releases.firstOrNull { it.versionCode == lastSeen }?.versionName
        rememberSeen()
        val installed = releases.firstOrNull { it.versionCode == installedVersionCode } ?: return null
        return AppUpdates.since(releases, installed, lastSeen, lastSeenName)
    }

    private fun rememberSeen() {
        prefs.edit().putInt(KEY_LAST_SEEN_CODE, installedVersionCode).putString(KEY_LAST_SEEN_NAME, installedVersionName).apply()
    }

    /** The installed version's release notes, on demand. */
    suspend fun installedRelease(): AppRelease? =
        fetch(TAG_URL + "v" + installedVersionName)?.let { AppUpdates.parseRelease(it) }

    /** The latest releases, newest first; null when GitHub cannot be reached or answers something else. */
    private suspend fun fetchReleases(): List<AppRelease>? = fetch(RELEASES_URL)?.let { AppUpdates.parseReleases(it) }

    private suspend fun fetch(url: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "InfoOperateur/$installedVersionName")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val body = response.body ?: return@use null
                if (!response.isSuccessful || body.contentLength() > MAX_RESPONSE_BYTES) return@use null
                String(ArcepUpdateManager.readLimited(body.byteStream(), MAX_RESPONSE_BYTES.toInt()), Charsets.UTF_8)
            }
        } catch (e: Exception) {
            Log.i(TAG, "GitHub releases unavailable", e)
            null
        }
    }

    companion object {
        private const val TAG = "AppUpdateChecker"
        private const val PREFS_NAME = "app_updates"
        private const val KEY_ENABLED = "check_enabled"
        private const val KEY_ENABLED_IN_DEBUG = "check_enabled_in_debug"
        private const val KEY_LAST_CHECK = "last_check_at"
        /** Before the releases list: the latest release only. Removed at the first check. */
        private const val KEY_LATEST_JSON = "latest_release_json"
        private const val KEY_NEWER_JSON = "newer_releases_json"
        private const val KEY_DISMISSED_CODE = "dismissed_version_code"
        private const val KEY_LAST_SEEN_CODE = "last_seen_version_code"
        private const val KEY_LAST_SEEN_NAME = "last_seen_version_name"

        internal const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val MAX_RESPONSE_BYTES = 1_000_000L
        /**
         * The 30 latest releases (about 7 KB each): enough to cover the versions a user skipped, since merges
         * close together share one release.
         */
        internal const val RELEASES_URL = "https://api.github.com/repos/azman0101/call-inspector/releases?per_page=30"
        internal const val TAG_URL = "https://api.github.com/repos/azman0101/call-inspector/releases/tags/"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
