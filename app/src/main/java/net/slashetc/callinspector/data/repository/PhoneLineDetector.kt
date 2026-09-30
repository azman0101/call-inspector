package net.slashetc.callinspector.data.repository

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.util.Log
import net.slashetc.callinspector.util.DetectedLine
import net.slashetc.callinspector.util.PhoneLines

/**
 * Asks the system for the name and number of the phone accounts (SIM...) that received calls. The app
 * holds no phone-state or phone-number permission, so depending on the device and Android version this
 * returns everything, the name only, or nothing: the user then gives the number once (ReporterProfileStore).
 */
class PhoneLineDetector(context: Context) {

    private val telecom = context.applicationContext.getSystemService(TelecomManager::class.java)

    fun detect(lineIds: Collection<String>): Map<String, DetectedLine> =
        lineIds.associateWith { detectOne(it) }.filterValues { it.label != null || it.number != null }

    private fun detectOne(lineId: String): DetectedLine {
        val (component, accountId) = PhoneLines.accountOf(lineId) ?: return DetectedLine()
        val componentName = ComponentName.unflattenFromString(component) ?: return DetectedLine()
        val account: PhoneAccount = try {
            telecom?.getPhoneAccount(PhoneAccountHandle(componentName, accountId))
        } catch (e: RuntimeException) {
            // SecurityException on devices that require a phone permission for this.
            Log.i(TAG, "Phone account details unavailable", e)
            null
        } ?: return DetectedLine()
        val number = listOfNotNull(account.address, account.subscriptionAddress)
            .map { it.schemeSpecificPart.orEmpty() }
            .firstOrNull { it.any(Char::isDigit) }
        return DetectedLine(label = account.label?.toString()?.takeIf { it.isNotBlank() }, number = number)
    }

    private companion object {
        const val TAG = "PhoneLineDetector"
    }
}
