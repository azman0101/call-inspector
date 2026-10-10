package net.slashetc.callinspector.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateCheckerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    /** What the fake GitHub answers, by URL; a missing URL is a 404, [offline] a network error. */
    private val responses = mutableMapOf<String, String>()
    private val requested = mutableListOf<String>()
    private var offline = false
    private var clock = 1_000_000_000L

    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val url = chain.request().url.toString()
        requested += url
        if (offline) throw IOException("offline")
        val body = responses[url]
        Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(if (body != null) 200 else 404)
            .message(if (body != null) "OK" else "Not Found")
            .body((body ?: "{}").toResponseBody("application/json".toMediaType()))
            .build()
    }.build()

    private fun release(build: Int, change: String = "feat: something", prerelease: Boolean = false) = """
        {"tag_name": "v1.0.$build", "draft": false, "prerelease": $prerelease,
         "html_url": "https://github.com/azman0101/call-inspector/releases/tag/v1.0.$build",
         "body": "## What's Changed\n* $change by @azman0101 in https://github.com/azman0101/call-inspector/pull/$build\n",
         "assets": [{"name": "info-operateur-1.0.$build.apk",
                     "browser_download_url": "https://github.com/azman0101/call-inspector/releases/download/v1.0.$build/info-operateur-1.0.$build.apk"}]}
    """.trimIndent()

    /** GET /releases: newest first. */
    private fun releases(vararg builds: Int) = builds.joinToString(",", "[", "]") { release(it) }

    private fun checker(installed: Int) =
        AppUpdateChecker(context, client, installedVersionCode = installed, installedVersionName = "1.0.$installed", now = { clock })

    @Before
    fun clearPrefs() {
        context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun `a newer release is offered, the installed or an older one is not`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180, 174)

        assertEquals(180, checker(174).availableUpdate()?.versionCode)
        assertNull(checker(180).availableUpdate(force = true))
        assertNull(checker(190).availableUpdate(force = true))
    }

    @Test
    fun `GitHub is asked at most once a day, the last answer is reused in between`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180)
        val checker = checker(174)

        checker.availableUpdate()
        clock += AppUpdateChecker.CHECK_INTERVAL_MS - 1
        responses[AppUpdateChecker.releasesUrl(1)] = releases(181, 180)
        assertEquals(180, checker.availableUpdate()?.versionCode)
        assertEquals(1, requested.size)

        clock += 1
        assertEquals(181, checker.availableUpdate()?.versionCode)
        assertEquals(2, requested.size)

        assertEquals(181, checker.availableUpdate(force = true)?.versionCode)
        assertEquals(3, requested.size)
    }

    @Test
    fun `offline, the last known release is kept and the next launch asks again`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180)
        val checker = checker(174)
        checker.availableUpdate()

        offline = true
        clock += AppUpdateChecker.CHECK_INTERVAL_MS
        assertEquals(180, checker.availableUpdate()?.versionCode)
        assertEquals(2, requested.size)
        assertEquals(180, checker.availableUpdate()?.versionCode)
        assertEquals(3, requested.size) // a failed check does not count as done
    }

    @Test
    fun `a dismissed version is not offered again, the next one is`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180)
        val checker = checker(174)

        checker.dismiss(checker.availableUpdate()!!)
        assertNull(checker.availableUpdate(force = true))

        responses[AppUpdateChecker.releasesUrl(1)] = releases(181, 180)
        assertEquals(181, checker.availableUpdate(force = true)?.versionCode)
    }

    @Test
    fun `turned off, nothing is asked and nothing is offered`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180)
        val checker = checker(174)
        checker.isEnabled = false

        assertNull(checker.availableUpdate(force = true))
        assertNull(checker.whatsNewAfterUpdate())
        assertEquals(emptyList<String>(), requested)
        checker.isEnabled = true
        assertEquals(180, checker.availableUpdate()?.versionCode)
    }

    @Test
    fun `the offer brings the changes of every release since the installed one, oldest first`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = listOf(
            release(182, "fix(tile): third"),
            release(181, "feat: second"),
            release(180, "feat: first"),
            release(174, "feat: already installed"),
        ).joinToString(",", "[", "]")

        val update = checker(180).availableUpdate()!!
        assertEquals(182, update.versionCode)
        assertEquals(listOf("feat: second (#181)", "fix(tile): third (#182)"), update.changes)
        assertEquals("1.0.180", update.changesSince)

        val oneAhead = checker(181).availableUpdate(force = true)!!
        assertEquals(listOf("fix(tile): third (#182)"), oneAhead.changes)
        assertNull(oneAhead.changesSince)
    }

    @Test
    fun `more than a page of skipped releases is read page after page, up to the installed one`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(*(260 downTo 231).toList().toIntArray())
        responses[AppUpdateChecker.releasesUrl(2)] = releases(*(230 downTo 201).toList().toIntArray())
        responses[AppUpdateChecker.releasesUrl(3)] = releases(*(200 downTo 190).toList().toIntArray())

        val update = checker(225).availableUpdate()!!
        assertEquals(260, update.versionCode)
        assertEquals((226..260).map { "feat: something (#$it)" }, update.changes)
        assertEquals(listOf(AppUpdateChecker.releasesUrl(1), AppUpdateChecker.releasesUrl(2)), requested)

        requested.clear()
        assertEquals(35, checker(225).availableUpdate(force = true)!!.changes.size)
        assertEquals(2, requested.size)
        requested.clear()
        checker(240).availableUpdate(force = true)
        assertEquals("the first page reaches 240", 1, requested.size)
    }

    @Test
    fun `a full page with a pre-release in it is not taken for the last one`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = (260 downTo 231).joinToString(",", "[", "]") { release(it, prerelease = it == 245) }
        responses[AppUpdateChecker.releasesUrl(2)] = releases(*(230 downTo 201).toList().toIntArray())

        val update = checker(225).availableUpdate()!!
        assertEquals((226..260).filter { it != 245 }.map { "feat: something (#$it)" }, update.changes)
        assertEquals(2, requested.size)
    }

    @Test
    fun `a page GitHub cannot give fails the whole check, the last answer is kept`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180)
        assertEquals(180, checker(174).availableUpdate()?.versionCode)

        responses[AppUpdateChecker.releasesUrl(1)] = releases(*(260 downTo 231).toList().toIntArray()) // page 2: 404
        assertEquals(180, checker(174).availableUpdate(force = true)?.versionCode)
    }

    @Test
    fun `the first check after this version asks GitHub even when the last one is recent`() = runBlocking {
        // What an older version left: the latest release alone, checked a moment ago.
        context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).edit()
            .putString("latest_release_json", release(180))
            .putLong("last_check_at", clock)
            .commit()
        responses[AppUpdateChecker.releasesUrl(1)] = releases(181, 180)

        assertEquals(181, checker(174).availableUpdate()?.versionCode)
        assertEquals(listOf(AppUpdateChecker.releasesUrl(1)), requested)
        assertEquals(false, context.getSharedPreferences("app_updates", Context.MODE_PRIVATE).contains("latest_release_json"))
        assertEquals(181, checker(174).availableUpdate()?.versionCode)
        assertEquals(1, requested.size)
    }

    @Test
    fun `release offers in a debug build are off by default and remembered once turned on`() {
        assertEquals(false, checker(174).isEnabledInDebug)
        checker(174).isEnabledInDebug = true
        assertEquals(true, checker(174).isEnabledInDebug)
        assertEquals(true, checker(174).isEnabled) // the general setting is independent
    }

    @Test
    fun `what's new shows once, after an update, never on a fresh install`() = runBlocking {
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180, 174)

        assertNull(checker(174).whatsNewAfterUpdate()) // fresh install: remembers 174
        assertEquals(emptyList<String>(), requested)
        assertNull(checker(174).whatsNewAfterUpdate())

        val afterUpdate = checker(180)
        assertEquals(180, afterUpdate.whatsNewAfterUpdate()?.versionCode)
        assertNull(afterUpdate.whatsNewAfterUpdate())
    }

    @Test
    fun `what's new brings the changes of every release since the version last opened`() = runBlocking {
        assertNull(checker(174).whatsNewAfterUpdate())
        responses[AppUpdateChecker.releasesUrl(1)] = listOf(
            release(182, "feat: not installed yet"),
            release(181, "fix: second"),
            release(180, "feat: first"),
            release(174, "feat: the version last opened"),
        ).joinToString(",", "[", "]")

        val whatsNew = checker(181).whatsNewAfterUpdate()!!
        assertEquals(181, whatsNew.versionCode)
        assertEquals(listOf("feat: first (#180)", "fix: second (#181)"), whatsNew.changes)
        assertEquals("1.0.174", whatsNew.changesSince)
    }

    @Test
    fun `what's new of a version that is not published is not asked again`() = runBlocking {
        assertNull(checker(174).whatsNewAfterUpdate())
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180, 174)

        assertNull(checker(179).whatsNewAfterUpdate())
        assertNull(checker(179).whatsNewAfterUpdate())
        assertEquals(1, requested.size)
    }

    @Test
    fun `what's new is retried at the next launch when GitHub cannot be reached`() = runBlocking {
        assertNull(checker(174).whatsNewAfterUpdate())

        offline = true
        assertNull(checker(180).whatsNewAfterUpdate())
        offline = false
        responses[AppUpdateChecker.releasesUrl(1)] = releases(180)
        assertEquals(180, checker(180).whatsNewAfterUpdate()?.versionCode)
    }

    @Test
    fun `the installed version's notes come from its tag`() = runBlocking {
        responses[AppUpdateChecker.TAG_URL + "v1.0.174"] = release(174)

        assertEquals("1.0.174", checker(174).installedRelease()?.versionName)
        assertNull(checker(175).installedRelease()) // not published: 404
    }
}
