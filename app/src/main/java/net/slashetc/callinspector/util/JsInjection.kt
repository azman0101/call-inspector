package net.slashetc.callinspector.util

import org.json.JSONObject

/**
 * Encodes a JSON string as a safe JavaScript expression using JSON.parse(), for the scripts the app injects
 * into the SignalConso and J'alerte l'Arcep forms: the value can't break out of its string literal.
 */
internal fun String.toJsExpression(): String =
    "JSON.parse(" + JSONObject.quote(this)
        .replace(" ", "\\u2028")
        .replace(" ", "\\u2029") + ")"
