package net.slashetc.callinspector.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `telemetry is enabled by default`() {
        assertTrue(SentryHelper.isTelemetryEnabled(context))
    }

    @Test
    fun `telemetry opt-out updates preference correctly`() {
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
}
