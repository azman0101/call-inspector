package net.slashetc.callinspector.ui

import android.app.Application
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import net.slashetc.callinspector.R
import net.slashetc.callinspector.ui.widget.DemarchageWidgetProvider
import net.slashetc.callinspector.util.DemarchageCounts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemarchageWidgetTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    private fun render(counts: DemarchageCounts?): View =
        DemarchageWidgetProvider.views(context, counts).apply(context, FrameLayout(context))

    @Test
    fun `the widget shows today's and this week's counts, today in red when there is any`() {
        val view = render(DemarchageCounts(today = 2, thisWeek = 7))

        val today = view.findViewById<TextView>(R.id.widget_today_count)
        assertEquals("2", today.text.toString())
        assertEquals("7", view.findViewById<TextView>(R.id.widget_week_count).text.toString())
        assertEquals(ContextCompat.getColor(context, R.color.widget_alert), today.currentTextColor)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_counts).visibility)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_message).visibility)
    }

    @Test
    fun `a quiet day is not in red`() {
        val today = render(DemarchageCounts(today = 0, thisWeek = 3)).findViewById<TextView>(R.id.widget_today_count)

        assertEquals(ContextCompat.getColor(context, R.color.widget_text), today.currentTextColor)
    }

    @Test
    fun `without the call log permission the widget asks to open the app`() = runBlocking {
        assertNull(DemarchageWidgetProvider.counts(context))

        val view = render(null)
        assertEquals(View.GONE, view.findViewById<View>(R.id.widget_counts).visibility)
        assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_message).visibility)
    }

    @Test
    fun `asking for an update with no widget on the home screen does nothing`() {
        DemarchageWidgetProvider.requestUpdate(context)
    }
}
