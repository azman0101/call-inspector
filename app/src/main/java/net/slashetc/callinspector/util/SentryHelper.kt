package net.slashetc.callinspector.util

import android.content.Context
import android.widget.Toast
import net.slashetc.callinspector.BuildConfig
import net.slashetc.callinspector.OperatorInfoApp
import io.sentry.Sentry
import io.sentry.SentryAttribute
import io.sentry.SentryAttributes
import io.sentry.SentryLogLevel
import io.sentry.logger.SentryLogParameters
import io.sentry.protocol.SentryId

/**
 * Gestionnaire utilitaire pour l'instrumentation de Sentry et les diagnostics en mode Développeur.
 * Conforme aux recommandations Sentry (SDK >= 8.12.0 / 8.58.0).
 */
object SentryHelper {
    private const val PREFS_NAME = "sentry_dev_prefs"
    private const val KEY_DEV_MODE = "dev_mode_enabled"
    private const val KEY_TELEMETRY_ENABLED = "telemetry_enabled"
    private const val KEY_TOAST_ENABLED = "toast_enabled"
    private const val KEY_CUSTOM_DSN = "custom_dsn"

    fun isTelemetryEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TELEMETRY_ENABLED, false)
    }

    fun setTelemetryEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TELEMETRY_ENABLED, enabled)
            .apply()
        // Apply now rather than at next launch: stop the SDK on opt-out, start it again on opt-in.
        if (!enabled) {
            Sentry.close()
        } else if (!Sentry.isEnabled()) {
            (context.applicationContext as? OperatorInfoApp)?.setupSentry()
        }
    }

    fun isDevModeEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_DEV_MODE, false)
    }

    fun setDevModeEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DEV_MODE, enabled)
            .apply()
    }

    fun isToastEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_TOAST_ENABLED, true)
    }

    fun setToastEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_TOAST_ENABLED, enabled)
            .apply()
    }

    fun getResolvedDsn(context: Context): String {
        val custom = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_DSN, "") ?: ""
        if (custom.isNotBlank()) return custom

        val configDsn = try { BuildConfig.SENTRY_DSN } catch (_: Throwable) { "" }
        if (configDsn.isNotBlank()) return configDsn

        val envDsn = System.getenv("SENTRY_DSN") ?: ""
        if (envDsn.isNotBlank()) return envDsn

        return ""
    }

    fun notifyIfToastEnabled(context: Context, message: String) {
        if (isToastEnabled(context)) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Déclenche une erreur contrôlée (RuntimeException) pour tester la capture d'erreurs par Sentry.
     */
    fun sendTestDummyError(context: Context): SentryId {
        val testException = IllegalStateException("Test Sentry: dummy error généré depuis le menu développeur")
        val sentryId = Sentry.captureException(testException)
        Sentry.flush(3000)
        notifyIfToastEnabled(context, "💥 Erreur test envoyée à Sentry !\nID: ${sentryId.toString().take(8)}...")
        return sentryId
    }

    /**
     * Émet des logs structurés (INFO, WARN, ERROR) suivant les meilleures pratiques Sentry SDK 8.58.0.
     */
    fun sendTestDummyLogs(context: Context) {
        try {
            // 1. Log INFO avec paramètres structurés
            val infoParams = SentryLogParameters.create(
                SentryAttributes.of(
                    SentryAttribute.stringAttribute("origin", "developer_menu"),
                    SentryAttribute.stringAttribute("event.category", "telemetry_test"),
                    SentryAttribute.stringAttribute("test.level", "INFO")
                )
            )
            Sentry.logger().log(
                SentryLogLevel.INFO,
                infoParams,
                "Diagnostic Sentry: test de log INFO avec attributs structurés"
            )

            // 2. Log WARN avec paramètres structurés
            val warnParams = SentryLogParameters.create(
                SentryAttributes.of(
                    SentryAttribute.stringAttribute("origin", "developer_menu"),
                    SentryAttribute.stringAttribute("event.category", "telemetry_test"),
                    SentryAttribute.stringAttribute("test.level", "WARN")
                )
            )
            Sentry.logger().log(
                SentryLogLevel.WARN,
                warnParams,
                "Diagnostic Sentry: test d'avertissement WARN simulé"
            )

            // 3. Log ERROR avec paramètres structurés
            val errorParams = SentryLogParameters.create(
                SentryAttributes.of(
                    SentryAttribute.stringAttribute("origin", "developer_menu"),
                    SentryAttribute.stringAttribute("event.category", "telemetry_test"),
                    SentryAttribute.stringAttribute("test.level", "ERROR")
                )
            )
            Sentry.logger().log(
                SentryLogLevel.ERROR,
                errorParams,
                "Diagnostic Sentry: test d'erreur ERROR journalisée sans crash"
            )

            Sentry.flush(3000)
            notifyIfToastEnabled(context, "📝 3 logs structurés envoyés à Sentry (INFO, WARN, ERROR) !")
        } catch (e: Throwable) {
            notifyIfToastEnabled(context, "Erreur logger Sentry: ${e.message}")
        }
    }

    /**
     * Envoie un message texte simple via captureMessage.
     */
    fun sendTestMessage(context: Context): SentryId {
        val sentryId = Sentry.captureMessage("Test Sentry: message dummy depuis le menu développeur")
        Sentry.flush(3000)
        notifyIfToastEnabled(context, "✉️ Message Sentry envoyé !\nID: ${sentryId.toString().take(8)}...")
        return sentryId
    }

    /**
     * Exemple de bonnes pratiques : Journalisation métier anonymisée d'une recherche.
     * Le numéro complet est STRICTEMENT masqué pour respecter la vie privée (RGPD).
     */
    fun logLookupEvent(context: Context, prefix: String, operatorName: String) {
        if (!isTelemetryEnabled(context)) return
        try {
            val params = SentryLogParameters.create(
                SentryAttributes.of(
                    SentryAttribute.stringAttribute("feature", "lookup"),
                    SentryAttribute.stringAttribute("prefix", prefix),
                    SentryAttribute.stringAttribute("operator", operatorName)
                )
            )
            Sentry.logger().log(
                SentryLogLevel.INFO,
                params,
                "Consultation opérateur effectuée pour le préfixe %s",
                prefix
            )
            if (isDevModeEnabled(context) && isToastEnabled(context)) {
                Toast.makeText(context, "📡 Log Sentry émis : Préfixe $prefix ($operatorName)", Toast.LENGTH_SHORT).show()
            }
        } catch (_: Throwable) {
        }
    }
}
