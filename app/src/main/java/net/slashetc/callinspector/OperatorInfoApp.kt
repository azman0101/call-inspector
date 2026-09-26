package net.slashetc.callinspector

import android.app.Application
import io.sentry.Sentry
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid

/**
 * Classe Application principale de l'application Info Opérateur.
 * Gère l'initialisation sécurisée et respectueuse de la vie privée du SDK Sentry / GlitchTip.
 */
class OperatorInfoApp : Application() {

    override fun onCreate() {
        super.onCreate()
        setupSentry()
    }

    fun setupSentry() {
        // Opted out: don't start the SDK at all, so sessions, performance transactions and
        // native crash reports (none of which go through beforeSend) are never sent either.
        if (!net.slashetc.callinspector.util.SentryHelper.isTelemetryEnabled(this)) return
        try {
            val dsn = net.slashetc.callinspector.util.SentryHelper.getResolvedDsn(this)

            if (dsn.isBlank()) {
                return
            }

            SentryAndroid.init(this) { options ->
                options.dsn = dsn
                options.isDebug = BuildConfig.DEBUG
                options.logs.isEnabled = BuildConfig.DEBUG
                options.tracesSampleRate = 1.0
                options.isSendDefaultPii = false

                // Options globales de confidentialité : pas de capture d'interactions UI ni réseau
                options.isEnableUserInteractionBreadcrumbs = false
                options.isEnableNetworkEventBreadcrumbs = false

                // Hook beforeBreadcrumb : abandonne tout breadcrumb sensible et assainit le texte
                options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                    // Si l'utilisateur a désactivé les rapports de diagnostic/télémétrie
                    if (!net.slashetc.callinspector.util.SentryHelper.isTelemetryEnabled(this)) {
                        return@BeforeBreadcrumbCallback null
                    }
                    if (breadcrumb.category == "ui.click") {
                        val viewId = breadcrumb.data["view.id"] as? String
                        if (viewId != null && (viewId.contains("phone", ignoreCase = true) ||
                                viewId.contains("number", ignoreCase = true) ||
                                viewId.contains("note", ignoreCase = true) ||
                                viewId.contains("search", ignoreCase = true))
                        ) {
                            return@BeforeBreadcrumbCallback null
                        }
                    }
                    breadcrumb.message = sanitizeSensitiveText(breadcrumb.message)
                    breadcrumb.data?.let { dataMap ->
                        val sensitiveKeys = dataMap.keys.filter { key ->
                            key.contains("phone", ignoreCase = true) ||
                            key.contains("number", ignoreCase = true) ||
                            key.contains("call", ignoreCase = true) ||
                            key.contains("contact", ignoreCase = true) ||
                            key.contains("email", ignoreCase = true) ||
                            key.contains("note", ignoreCase = true) ||
                            key.contains("query", ignoreCase = true)
                        }
                        sensitiveKeys.forEach { dataMap.remove(it) }
                    }
                    breadcrumb
                }

                // Filtre de confidentialité strict via beforeSend :
                // 1. Si télémétrie désactivée par l'utilisateur -> abandon complet (null)
                // 2. Aucune donnée d'appel ni identifiant persistant d'appareil ne quitte le terminal
                options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                    if (!net.slashetc.callinspector.util.SentryHelper.isTelemetryEnabled(this)) {
                        return@BeforeSendCallback null
                    }

                    // 1. Suppression stricte de tout objet utilisateur
                    event.user = null

                    // 2. Nettoyage du contexte matériel (Device / App)
                    val device = event.contexts.device
                    if (device != null) {
                        device.id = null          // Supprimer l'identifiant persistant de l'appareil
                        device.bootTime = null    // Supprimer l'horodatage de démarrage discriminatoire
                    }
                    event.contexts.app?.appStartTime = null // Supprimer l'heure exacte de démarrage

                    // 3. Nettoyage des breadcrumbs
                    event.breadcrumbs?.forEach { breadcrumb ->
                        breadcrumb.message = sanitizeSensitiveText(breadcrumb.message)
                        breadcrumb.data?.let { dataMap ->
                            val sensitiveKeys = dataMap.keys.filter { key ->
                                key.contains("phone", ignoreCase = true) ||
                                key.contains("number", ignoreCase = true) ||
                                key.contains("call", ignoreCase = true) ||
                                key.contains("contact", ignoreCase = true) ||
                                key.contains("email", ignoreCase = true) ||
                                key.contains("note", ignoreCase = true) ||
                                key.contains("query", ignoreCase = true)
                            }
                            sensitiveKeys.forEach { dataMap.remove(it) }
                        }
                    }

                    // 4. Nettoyage des messages d'exceptions
                    event.exceptions?.forEach { sentryException ->
                        sentryException.value = sanitizeSensitiveText(sentryException.value)
                    }

                    // 5. Nettoyage du message principal de l'événement
                    event.message?.let { message ->
                        message.formatted = sanitizeSensitiveText(message.formatted)
                    }

                    event
                }

                // Transactions (tracesSampleRate) bypass beforeSend: same opt-out and device scrubbing.
                options.beforeSendTransaction = SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
                    if (!net.slashetc.callinspector.util.SentryHelper.isTelemetryEnabled(this)) {
                        return@BeforeSendTransactionCallback null
                    }
                    transaction.user = null
                    transaction.contexts.device?.let { device ->
                        device.id = null
                        device.bootTime = null
                    }
                    transaction.contexts.app?.appStartTime = null
                    transaction
                }
            }

            // Journalisation de démarrage de l'application
            try {
                Sentry.logger().info("Info Opérateur initialisé avec succès (Android SDK)")
                Sentry.flush(2000)
            } catch (_: Throwable) {
                // Ignoré si le logger n'est pas actif
            }
        } catch (_: Throwable) {
            // Empêche tout crash au démarrage si Sentry ne peut s'initialiser
        }
    }

    private fun sanitizeSensitiveText(text: String?): String? {
        if (text == null) return null
        // Masquer les numéros de téléphone (formats français courants et internationaux)
        val phoneRegex = Regex("""(\+?\d{1,3}[-.\s]?)?\(?\d{1,4}\)?[-.\s]?\d{1,4}[-.\s]?\d{1,9}""")
        var sanitized = phoneRegex.replace(text) { match ->
            val value = match.value.trim()
            // Ne remplacer que si cela ressemble à une séquence numérique de numéro de téléphone (au moins 6 chiffres)
            if (value.count { it.isDigit() } >= 6) "[REDACTED_PHONE]" else value
        }
        // Masquer les adresses email
        val emailRegex = Regex("""[a-zA-Z0-9_.+-]+@[a-zA-Z0-9-]+\.[a-zA-Z0-9-.]+""")
        sanitized = emailRegex.replace(sanitized, "[REDACTED_EMAIL]")
        return sanitized
    }
}
