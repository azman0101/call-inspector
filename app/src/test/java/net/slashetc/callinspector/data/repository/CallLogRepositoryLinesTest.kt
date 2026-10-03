package net.slashetc.callinspector.data.repository

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.CallLog
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Reads the receiving line of each call from a device call log (a fake CallLog provider). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CallLogRepositoryLinesTest {

    class FakeCallLogProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection!!
            return MatrixCursor(columns).apply {
                ROWS.forEach { row -> addRow(columns.map { row[it] }) }
            }
        }

        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    }

    private lateinit var application: Application

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALL_LOG)
        Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
    }

    @Test
    fun `each call carries the phone account that received it and the via number`() = runBlocking {
        val calls = CallLogRepository(application).getCallLogs(useSampleIfEmpty = false)

        assertEquals(listOf(3L, 2L, 1L), calls.map { it.id })
        assertEquals("$TELEPHONY|2", calls[0].lineId)
        assertEquals("+33639980002", calls[0].viaNumber)
        assertEquals("$TELEPHONY|1", calls[1].lineId)
        assertNull(calls[1].viaNumber) // blank in the call log
        assertNull(calls[2].lineId) // no phone account recorded
    }

    private companion object {
        const val TELEPHONY = "com.android.phone/com.android.services.telephony.TelephonyConnectionService"

        val ROWS = listOf(
            mapOf(
                CallLog.Calls._ID to 3L, CallLog.Calls.NUMBER to "0162001122", CallLog.Calls.DATE to 3_000L,
                CallLog.Calls.DURATION to 0L, CallLog.Calls.TYPE to CallLog.Calls.MISSED_TYPE,
                CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME to TELEPHONY, CallLog.Calls.PHONE_ACCOUNT_ID to "2",
                CallLog.Calls.VIA_NUMBER to "+33639980002",
            ),
            mapOf(
                CallLog.Calls._ID to 2L, CallLog.Calls.NUMBER to "0948123456", CallLog.Calls.DATE to 2_000L,
                CallLog.Calls.DURATION to 12L, CallLog.Calls.TYPE to CallLog.Calls.INCOMING_TYPE,
                CallLog.Calls.PHONE_ACCOUNT_COMPONENT_NAME to TELEPHONY, CallLog.Calls.PHONE_ACCOUNT_ID to "1",
                CallLog.Calls.VIA_NUMBER to "",
            ),
            mapOf(
                CallLog.Calls._ID to 1L, CallLog.Calls.NUMBER to "0270334455", CallLog.Calls.DATE to 1_000L,
                CallLog.Calls.DURATION to 0L, CallLog.Calls.TYPE to CallLog.Calls.BLOCKED_TYPE,
            ),
        )
    }
}
