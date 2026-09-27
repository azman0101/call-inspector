package net.slashetc.callinspector.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import net.slashetc.callinspector.data.db.ArcepDatabaseManager
import net.slashetc.callinspector.data.db.CallNote
import net.slashetc.callinspector.data.db.CallNotesStore
import net.slashetc.callinspector.data.db.ReportStats
import net.slashetc.callinspector.data.db.ReporterProfileStore
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.data.model.CallType
import net.slashetc.callinspector.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class RawCallRecord(
    val id: Long,
    val rawNum: String,
    val cachedName: String?,
    val date: Long,
    val duration: Long,
    val callType: CallType
)

class CallLogRepository(private val context: Context) {

    private val dbManager = ArcepDatabaseManager.getInstance(context)
    private val reportStore = ReporterProfileStore.getInstance(context)
    private val notesStore = CallNotesStore.getInstance(context)

    // Same for the encrypted notes: without them, calls just show no note, favorite or spam flag.
    private suspend fun loadNotes(): Map<String, CallNote> =
        runCatching { notesStore.all() }
            .onFailure { Log.e(TAG, "Failed to read the call notes", it) }
            .getOrDefault(emptyMap())

    // The call list must not depend on the encrypted report log: without it, calls just show no reports.
    private suspend fun loadReportStats(): Map<String, ReportStats> =
        runCatching { reportStore.reportStats() }
            .onFailure { Log.e(TAG, "Failed to read the SignalConso report log", it) }
            .getOrDefault(emptyMap())

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun getCallLogs(useSampleIfEmpty: Boolean = true): List<CallLogEntry> = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext if (useSampleIfEmpty) generateSampleCalls() else emptyList()
        }

        val entries = mutableListOf<CallLogEntry>()
        val reports = loadReportStats()
        val notes = loadNotes()
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        try {
            // Only calls received: the app inspects who called the user, not the numbers the user dialed.
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.TYPE} != ?",
                arrayOf(CallLog.Calls.OUTGOING_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"
            )

            val rawRecords = mutableListOf<RawCallRecord>()
            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                while (it.moveToNext() && rawRecords.size < 100) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                    val rawNum = if (numberIdx >= 0) it.getString(numberIdx) ?: "" else ""
                    val cachedName = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durationIdx >= 0) it.getLong(durationIdx) else 0L
                    val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else 0

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.UNKNOWN
                    }

                    rawRecords.add(RawCallRecord(id, rawNum, cachedName, date, duration, callType))
                }
            }

            val lookups = dbManager.lookupNumbers(rawRecords.map { it.rawNum })

            for (record in rawRecords) {
                val lookup = lookups[record.rawNum] ?: dbManager.lookupNumber(record.rawNum)
                val note = notes[lookup.normalizedNumber]

                entries.add(
                    CallLogEntry(
                        id = record.id,
                        rawNumber = record.rawNum,
                        normalizedNumber = lookup.normalizedNumber,
                        formattedNumber = lookup.formattedNumber,
                        cachedName = record.cachedName,
                        timestamp = record.date,
                        durationSeconds = record.duration,
                        callType = record.callType,
                        lookupResult = lookup,
                        isSpamFlagged = note?.isSpam ?: lookup.numberType.isDemarchage,
                        isFavorite = note?.isFavorite ?: false,
                        userNote = note?.userNote,
                        reportCount = reports[lookup.normalizedNumber]?.count ?: 0,
                        lastReportedAt = reports[lookup.normalizedNumber]?.lastReportedAt
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read the device call log", e)
        }

        if (entries.isEmpty() && useSampleIfEmpty) {
            return@withContext generateSampleCalls()
        }

        return@withContext entries
    }

    suspend fun generateSampleCalls(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        // Numbers shown with a contact name use the ranges ARCEP reserves for fiction (01 99 00, 06 39 98),
        // which belong to no one: no real subscriber appears next to an invented name. The others show
        // operator lookups on assigned ranges, without a name.
        val sampleNumbers = listOf(
            Triple("0162001122", CallType.MISSED, now - 15 * 60 * 1000L), // 15 mins ago
            Triple("0639980112", CallType.INCOMING, now - 2 * 3600 * 1000L), // 2 hrs ago
            Triple("0270334455", CallType.MISSED, now - 5 * 3600 * 1000L), // 5 hrs ago
            Triple("0199000134", CallType.INCOMING, now - 22 * 3600 * 1000L), // yesterday
            Triple("0639980567", CallType.INCOMING, now - 28 * 3600 * 1000L),
            Triple("0948123456", CallType.REJECTED, now - 48 * 3600 * 1000L),
            Triple("0491002233", CallType.INCOMING, now - 72 * 3600 * 1000L),
            Triple("0892353535", CallType.MISSED, now - 96 * 3600 * 1000L),
            Triple("0556000000", CallType.INCOMING, now - 120 * 3600 * 1000L),
            Triple("0590203040", CallType.MISSED, now - 150 * 3600 * 1000L)
        )

        val results = mutableListOf<CallLogEntry>()
        val reports = loadReportStats()
        val notes = loadNotes()
        val lookups = dbManager.lookupNumbers(sampleNumbers.map { it.first })

        for ((idx, item) in sampleNumbers.withIndex()) {
            val (rawNum, callType, timestamp) = item
            val lookup = lookups[rawNum] ?: dbManager.lookupNumber(rawNum)
            val note = notes[lookup.normalizedNumber]

            val cachedName = when (rawNum) {
                "0639980112" -> "Sophie Martin"
                "0199000134" -> "Cabinet Médical"
                "0639980567" -> "Alexandre D."
                else -> null
            }

            val duration = when (callType) {
                CallType.INCOMING -> (idx * 45L + 12L)
                CallType.OUTGOING -> (idx * 28L + 30L)
                else -> 0L
            }

            results.add(
                CallLogEntry(
                    id = (1000L + idx),
                    rawNumber = rawNum,
                    normalizedNumber = lookup.normalizedNumber,
                    formattedNumber = lookup.formattedNumber,
                    cachedName = cachedName,
                    timestamp = timestamp,
                    durationSeconds = duration,
                    callType = callType,
                    lookupResult = lookup,
                    isSpamFlagged = note?.isSpam ?: lookup.numberType.isDemarchage,
                    isFavorite = note?.isFavorite ?: false,
                    userNote = note?.userNote,
                    reportCount = reports[lookup.normalizedNumber]?.count ?: 0,
                    lastReportedAt = reports[lookup.normalizedNumber]?.lastReportedAt
                )
            )
        }
        results
    }

    suspend fun toggleSpamFlag(phoneNumber: String, currentFlag: Boolean) {
        val note = notesStore.get(phoneNumber)
        notesStore.save(
            phoneNumber = phoneNumber,
            isFavorite = note?.isFavorite ?: false,
            isSpam = !currentFlag,
            userTag = note?.userTag,
            userNote = note?.userNote
        )
    }

    suspend fun toggleFavorite(phoneNumber: String, currentFavorite: Boolean) {
        val note = notesStore.get(phoneNumber)
        notesStore.save(
            phoneNumber = phoneNumber,
            isFavorite = !currentFavorite,
            isSpam = note?.isSpam ?: false,
            userTag = note?.userTag,
            userNote = note?.userNote
        )
    }

    suspend fun saveNote(phoneNumber: String, noteText: String?) {
        val note = notesStore.get(phoneNumber)
        notesStore.save(
            phoneNumber = phoneNumber,
            isFavorite = note?.isFavorite ?: false,
            isSpam = note?.isSpam ?: false,
            userTag = note?.userTag,
            userNote = noteText
        )
    }

    private companion object {
        const val TAG = "CallLogRepository"
    }
}
