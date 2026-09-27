package net.slashetc.callinspector.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.sentry.Sentry
import net.slashetc.callinspector.OperatorInfoApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SentryHelperTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Reset test preferences
        context.getSharedPreferences("sentry_dev_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @After
    fun tearDown() {
        Sentry.close()
    }

    // Enabling telemetry starts the real SDK: point it at a local port that accepts nothing, so tests
    // never send events to the project's Sentry (the fallback DSN, or the CI's SENTRY_DSN).
    private fun useUnreachableDsn() {
        context.getSharedPreferences("sentry_dev_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("custom_dsn", "http://test@127.0.0.1:9/1")
            .commit()
    }

    @Test
    fun `telemetry is disabled by default (opt-in)`() {
        assertFalse(SentryHelper.isTelemetryEnabled(context))
    }

    @Test
    fun `telemetry opt-out updates preference correctly`() {
        useUnreachableDsn()
        SentryHelper.setTelemetryEnabled(context, false)
        assertFalse(SentryHelper.isTelemetryEnabled(context))

        SentryHelper.setTelemetryEnabled(context, true)
        assertTrue(SentryHelper.isTelemetryEnabled(context))
    }

    @Test
    fun `developer mode is disabled by default`() {
        assertFalse(SentryHelper.isDevModeEnabled(context))
    }

    @Test
    fun `developer mode toggles correctly`() {
        SentryHelper.setDevModeEnabled(context, true)
        assertTrue(SentryHelper.isDevModeEnabled(context))

        SentryHelper.setDevModeEnabled(context, false)
        assertFalse(SentryHelper.isDevModeEnabled(context))
    }

    @Test
    fun `getResolvedDsn returns default fallback when no custom or build DSN is set`() {
        val dsn = SentryHelper.getResolvedDsn(context)
        assertEquals(SentryHelper.FALLBACK_DSN, dsn)
    }

    @Test
    fun `logLookupEvent does not crash when telemetry is disabled`() {
        SentryHelper.setTelemetryEnabled(context, false)
        SentryHelper.logLookupEvent(context, "0162", "Manifone")
        // Should return early and not throw any exception
    }

    @Test
    fun `opting out stops the Sentry SDK immediately`() {
        SentryHelper.setTelemetryEnabled(context, false)
        assertFalse(Sentry.isEnabled())
    }

    @Test
    fun `opting back in restarts the Sentry SDK`() {
        assumeTrue(context is OperatorInfoApp)
        useUnreachableDsn()
        SentryHelper.setTelemetryEnabled(context, false)
        SentryHelper.setTelemetryEnabled(context, true)
        assertTrue(Sentry.isEnabled())
    }
}
