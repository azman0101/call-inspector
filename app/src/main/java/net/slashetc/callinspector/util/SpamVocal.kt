package net.slashetc.callinspector.util

/**
 * The 33700, the operators' platform for unwanted SMS and calls (AF2M). An unwanted call, such as a one-ring
 * call from abroad that tempts you to call a premium number back, is reported by texting "spam vocal" followed
 * by the caller's number to the 33700 (service-public.fr, fiche F36525); the operators then can suspend it.
 */
object SpamVocal {
    const val NUMBER = "33700"

    /** The SMS to send to [NUMBER]: "spam vocal +491734119637". */
    fun message(callerNumber: String): String = "spam vocal $callerNumber"
}
