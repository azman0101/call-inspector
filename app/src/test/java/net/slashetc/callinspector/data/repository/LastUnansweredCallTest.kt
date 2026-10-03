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
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.data.model.PhoneNumberType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** The call the "Qui m'a appelé ?" tile describes, read from a fake call log. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LastUnansweredCallTest {

    /** Ignores the selection on purpose: the repository must still skip answered calls. */
    class FakeCallLogProvider : ContentProvider() {
        override fun onCreate() = true

        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
            val columns = projection!!
            return MatrixCursor(columns).apply { rows.forEach { row -> addRow(columns.map { row[it] }) } }
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
        Robolectric.setupContentProvider(FakeCallLogProvider::class.java, CallLog.AUTHORITY)
    }

    private fun row(id: Long, number: String, date: Long, type: Int) = mapOf<String, Any?>(
        CallLog.Calls._ID to id, CallLog.Calls.NUMBER to number, CallLog.Calls.CACHED_NAME to null,
        CallLog.Calls.DATE to date, CallLog.Calls.TYPE to type,
    )

    @Test
    fun `the newest call not taken is described with its operator, answered calls are skipped`() = runBlocking {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALL_LOG)
        rows = listOf(
            row(3, "0612345678", 3_000, CallLog.Calls.INCOMING_TYPE),
            row(2, "0105612345", 2_000, CallLog.Calls.REJECTED_TYPE),
            row(1, "0162001122", 1_000, CallLog.Calls.MISSED_TYPE),
        )

        val call = CallLogRepository(application).lastUnansweredCall()!!

        assertEquals(2L, call.id)
        assertEquals(CallType.REJECTED, call.callType)
        assertEquals("Orange", call.lookupResult.operatorDisplayName) // 01 05 61 is an Orange range in the bundled ARCEP data
        assertEquals(PhoneNumberType.FIXE_ILE_DE_FRANCE, call.lookupResult.numberType)
    }

    @Test
    fun `a blocked telemarketing call counts, and is flagged as spam`() = runBlocking {
        shadowOf(application).grantPermissions(Manifest.permission.READ_CALL_LOG)
        rows = listOf(row(1, "0162001122", 1_000, CallLog.Calls.BLOCKED_TYPE))

        val call = CallLogRepository(application).lastUnansweredCall()!!

        assertEquals(CallType.BLOCKED, call.callType)
        assertTrue(call.isSpamFlagged)
    }

    @Test
    fun `nothing without the permission or without a call not taken`() = runBlocking {
        rows = listOf(row(1, "0162001122", 1_000, CallLog.Calls.MISSED_TYPE))
        assertNull(CallLogRepository(application).lastUnansweredCall())

        shadowOf(application).grantPermissions(Manifest.permission.READ_CALL_LOG)
        rows = listOf(row(1, "0612345678", 1_000, CallLog.Calls.INCOMING_TYPE))
        assertNull(CallLogRepository(application).lastUnansweredCall())
    }

    companion object {
        var rows: List<Map<String, Any?>> = emptyList()
    }
}
