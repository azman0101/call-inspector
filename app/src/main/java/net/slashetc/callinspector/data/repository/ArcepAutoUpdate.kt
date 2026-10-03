package net.slashetc.callinspector.data.repository

import android.content.Context

/**
 * When the app checks ARCEP's files by itself: at its first launch, then at most once every
 * [CHECK_INTERVAL_MS]. The check only asks for the files' dates; they are downloaded when they changed
 * ([ArcepUpdateManager.checkAndDownloadUpdate] with `onlyIfChanged`). The bundled database is no longer
 * refreshed by CI, so this is what keeps installed apps up to date (CGU article 4).
 */
class ArcepAutoUpdate internal constructor(
    context: Context,
    private val now: () -> Long,
) {
    constructor(context: Context) : this(context, System::currentTimeMillis)

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** True at first launch, and once the last completed check is [CHECK_INTERVAL_MS] old. */
    fun isDue(): Boolean = now() - prefs.getLong(KEY_LAST_CHECK, 0L) >= CHECK_INTERVAL_MS

    /**
     * Records a check. A failed one (ARCEP unreachable) is not recorded, so the next launch tries again:
     * a phone offline at its first launch still gets the data.
     */
    fun record(result: ArcepUpdateResult) {
        if (result != ArcepUpdateResult.FAILED) prefs.edit().putLong(KEY_LAST_CHECK, now()).apply()
    }

    companion object {
        private const val PREFS_NAME = "arcep_updates"
        private const val KEY_LAST_CHECK = "last_check_at"

        internal const val CHECK_INTERVAL_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
