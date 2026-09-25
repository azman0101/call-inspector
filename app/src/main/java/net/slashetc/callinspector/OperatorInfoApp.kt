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

    private fun setupSentry() {
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

            // Filtre de confidentialité strict via beforeSend :
            // Aucune donnée d'appel (numéro, email, note, contenu) ne doit être transmise
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                // 1. Suppression stricte de tout objet utilisateur
                event.user = null

                // 2. Nettoyage des breadcrumbs
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

                // 3. Nettoyage des messages d'exceptions
                event.exceptions?.forEach { sentryException ->
                    sentryException.value = sanitizeSensitiveText(sentryException.value)
                }

                // 4. Nettoyage du message principal de l'événement
                event.message?.let { message ->
                    message.formatted = sanitizeSensitiveText(message.formatted)
                }

                event
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
