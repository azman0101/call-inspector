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
     * The newer release to offer, or null. GitHub is asked at most once every [CHECK_INTERVAL_MS] unless
     * [force]; in between, the last answer is reused. A release the user dismissed is not offered again.
     */
    suspend fun availableUpdate(force: Boolean = false): AppRelease? {
        if (!isEnabled) return null
        val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (force || now() - lastCheck >= CHECK_INTERVAL_MS) {
            fetch(LATEST_URL)?.let { json ->
                prefs.edit().putString(KEY_LATEST_JSON, json).putLong(KEY_LAST_CHECK, now()).apply()
            }
        }
        val latest = prefs.getString(KEY_LATEST_JSON, null)?.let { AppUpdates.parseRelease(it) } ?: return null
        return latest.takeIf {
            AppUpdates.isNewer(it, installedVersionCode) && it.versionCode != prefs.getInt(KEY_DISMISSED_CODE, 0)
        }
    }

    fun dismiss(release: AppRelease) {
        prefs.edit().putInt(KEY_DISMISSED_CODE, release.versionCode).apply()
    }

    /**
     * The installed version's release, the first time it runs after an update; null on a fresh install,
     * once shown, or when GitHub cannot be reached (it is then tried again at the next launch).
     */
    suspend fun whatsNewAfterUpdate(): AppRelease? {
        val lastSeen = prefs.getInt(KEY_LAST_SEEN_CODE, 0)
        if (lastSeen == 0 || lastSeen > installedVersionCode) {
            prefs.edit().putInt(KEY_LAST_SEEN_CODE, installedVersionCode).apply()
            return null
        }
        if (lastSeen == installedVersionCode || !isEnabled) return null
        val release = installedRelease() ?: return null
        prefs.edit().putInt(KEY_LAST_SEEN_CODE, installedVersionCode).apply()
        return release
    }

    /** The installed version's release notes, on demand. */
    suspend fun installedRelease(): AppRelease? =
        fetch(TAG_URL + "v" + installedVersionName)?.let { AppUpdates.parseRelease(it) }

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
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_LATEST_JSON = "latest_release_json"
        private const val KEY_DISMISSED_CODE = "dismissed_version_code"
        private const val KEY_LAST_SEEN_CODE = "last_seen_version_code"

        internal const val CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
        private const val MAX_RESPONSE_BYTES = 1_000_000L
        internal const val LATEST_URL = "https://api.github.com/repos/azman0101/call-inspector/releases/latest"
        internal const val TAG_URL = "https://api.github.com/repos/azman0101/call-inspector/releases/tags/"

        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()
        }
    }
}
