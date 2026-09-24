package com.example.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.data.db.ArcepDatabaseManager
import com.example.data.model.CallLogEntry
import com.example.data.model.CallType
import com.example.util.PhoneNumberFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    private val dbManager = ArcepDatabaseManager.getInstance(context)

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
        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE
        )

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = it.getColumnIndex(CallLog.Calls.DURATION)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)

                while (it.moveToNext() && entries.size < 100) {
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

                    val lookup = dbManager.lookupNumber(rawNum)
                    val note = dbManager.getCallNote(rawNum)

                    entries.add(
                        CallLogEntry(
                            id = id,
                            rawNumber = rawNum,
                            normalizedNumber = lookup.normalizedNumber,
                            formattedNumber = lookup.formattedNumber,
                            cachedName = cachedName,
                            timestamp = date,
                            durationSeconds = duration,
                            callType = callType,
                            lookupResult = lookup,
                            isSpamFlagged = note?.isSpam ?: lookup.numberType.isDemarchage,
                            isFavorite = note?.isFavorite ?: false,
                            userNote = note?.userNote
                        )
                    )
                }
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
        val sampleNumbers = listOf(
            Triple("0162001122", CallType.MISSED, now - 15 * 60 * 1000L), // 15 mins ago
            Triple("0612345678", CallType.INCOMING, now - 2 * 3600 * 1000L), // 2 hrs ago
            Triple("0270334455", CallType.MISSED, now - 5 * 3600 * 1000L), // 5 hrs ago
            Triple("0142680000", CallType.OUTGOING, now - 22 * 3600 * 1000L), // yesterday
            Triple("0781234567", CallType.INCOMING, now - 28 * 3600 * 1000L),
            Triple("0948123456", CallType.REJECTED, now - 48 * 3600 * 1000L),
            Triple("0491002233", CallType.INCOMING, now - 72 * 3600 * 1000L),
            Triple("0892353535", CallType.OUTGOING, now - 96 * 3600 * 1000L),
            Triple("0556000000", CallType.INCOMING, now - 120 * 3600 * 1000L),
            Triple("0590203040", CallType.MISSED, now - 150 * 3600 * 1000L)
        )

        val results = mutableListOf<CallLogEntry>()
        for ((idx, item) in sampleNumbers.withIndex()) {
            val (rawNum, callType, timestamp) = item
            val lookup = dbManager.lookupNumber(rawNum)
            val note = dbManager.getCallNote(rawNum)

            val cachedName = when (rawNum) {
                "0612345678" -> "Sophie Martin"
                "0142680000" -> "Cabinet Médical"
                "0781234567" -> "Alexandre D."
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
                    userNote = note?.userNote
                )
            )
        }
        results
    }

    suspend fun toggleSpamFlag(phoneNumber: String, currentFlag: Boolean) {
        val note = dbManager.getCallNote(phoneNumber)
        dbManager.saveCallNote(
            phoneNumber = phoneNumber,
            isFavorite = note?.isFavorite ?: false,
            isSpam = !currentFlag,
            userTag = note?.userTag,
            userNote = note?.userNote
        )
    }

    suspend fun toggleFavorite(phoneNumber: String, currentFavorite: Boolean) {
        val note = dbManager.getCallNote(phoneNumber)
        dbManager.saveCallNote(
            phoneNumber = phoneNumber,
            isFavorite = !currentFavorite,
            isSpam = note?.isSpam ?: false,
            userTag = note?.userTag,
            userNote = note?.userNote
        )
    }

    suspend fun saveNote(phoneNumber: String, noteText: String?) {
        val note = dbManager.getCallNote(phoneNumber)
        dbManager.saveCallNote(
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
