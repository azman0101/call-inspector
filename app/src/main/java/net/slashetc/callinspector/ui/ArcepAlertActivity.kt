package net.slashetc.callinspector.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.slashetc.callinspector.data.db.ReporterProfile
import net.slashetc.callinspector.data.db.ReporterProfileStore
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.ui.components.FormNote
import net.slashetc.callinspector.ui.components.ReporterProfileDialog
import net.slashetc.callinspector.ui.components.rememberKeyboardOpen
import net.slashetc.callinspector.ui.theme.MyApplicationTheme
import net.slashetc.callinspector.util.ArcepAlert
import net.slashetc.callinspector.util.ArcepAlertPlan
import net.slashetc.callinspector.util.toJsExpression
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * Opens J'alerte l'Arcep and prefills an alert about telemarketing calls: one call, or all the calls from
 * numbers the ARCEP assigned to the same operator. The user checks each step and sends it; the app never does.
 */
class ArcepAlertActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PLAN_JSON = "plan_json"
        private const val PAGE_POLL_MS = 1500L

        /** What the prefill script tells the app; flags only, never the user's data. */
        internal const val PAGE_STATE_QUERY =
            "({step: Number(window.__iaStep || 0), needsContact: !!window.__iaNeedsContact, needsCommune: !!window.__iaNeedsCommune})"

        internal data class PageState(val step: Int, val needsContact: Boolean, val needsCommune: Boolean)

        /** Reads [PAGE_STATE_QUERY]'s result; anything else (page without the script, error) is an empty state. */
        internal fun parsePageState(json: String?): PageState =
            runCatching { JSONObject(json ?: "") }.getOrNull()
                ?.let { PageState(it.optInt("step"), it.optBoolean("needsContact"), it.optBoolean("needsCommune")) }
                ?: PageState(step = 0, needsContact = false, needsCommune = false)

        /** The notes above the form. Each is short, shown at the step it is about, and can be closed. */
        internal enum class Note { PREFILLED, OPERATOR_TYPED, NO_POSTAL_CODE, PROFILE }

        /** The notes to show; none while the keyboard is open, the form needs the whole height to type in. */
        internal fun visibleNotes(
            step: Int,
            keyboardOpen: Boolean,
            operatorTyped: Boolean,
            hasPostalCode: Boolean,
            hasContact: Boolean,
            closed: Set<Note>,
        ): List<Note> {
            if (keyboardOpen) return emptyList()
            return buildList {
                if (step <= 2) add(Note.PREFILLED)
                if (step == 3 && operatorTyped) add(Note.OPERATOR_TYPED)
                if (step == 3 && !hasPostalCode) add(Note.NO_POSTAL_CODE)
                if (step == 5 && !hasContact) add(Note.PROFILE)
            }.filterNot { it in closed }
        }

        /** The prefill script, called with the plan: the plan stays in its closure, never in a global. */
        internal fun prefillInjection(script: String, planJson: String): String =
            script.trimEnd().removeSuffix(";") + "(" + planJson.toJsExpression() + ");"

        /** Hands the contact details (or null) to the script, which keeps them while step 5 is shown. */
        internal fun contactDelivery(contactJson: String): String =
            "if (window.__iaFillContact) window.__iaFillContact(${contactJson.toJsExpression()});"

        /** Hands the user's postal code and city (or null) to the script, for step 3's commune. */
        internal fun communeDelivery(communeJson: String): String =
            "if (window.__iaFillCommune) window.__iaFillCommune(${communeJson.toJsExpression()});"

        internal fun ReporterProfile?.toContactJson(): String = this
            ?.takeIf { it.email.isNotBlank() || it.lastName.isNotBlank() || it.firstName.isNotBlank() || it.phone.isNotBlank() }
            ?.let {
                JSONObject()
                    .put("email", it.email)
                    .put("lastName", it.lastName)
                    .put("firstName", it.firstName)
                    .put("phone", it.phone)
                    .toString()
            } ?: "null"

        internal fun ReporterProfile?.toCommuneJson(): String = this
            ?.takeIf { it.postalCode.isNotBlank() }
            ?.let { JSONObject().put("postalCode", it.postalCode).put("city", it.city).toString() }
            ?: "null"

        internal fun ArcepAlertPlan.toJson() = JSONObject().apply {
            put("numberTypes", JSONArray(numberTypes))
            put("operatorName", operatorName ?: JSONObject.NULL)
            put("jalerteOperator", jalerteOperator ?: JSONObject.NULL)
            put("description", description)
        }

        /** J'alerte l'Arcep's operator list, as bundled (see ArcepAlert.matchOperator). */
        fun jalerteOperators(context: Context): List<String> =
            context.assets.open("jalerte_operators.txt").bufferedReader().useLines { lines ->
                lines.map(String::trim).filter(String::isNotEmpty).toList()
            }

        /** Opens the form for [calls]: one call, or the calls from the same operator. */
        fun intent(context: Context, calls: List<CallLogEntry>): Intent {
            val plan = ArcepAlert.buildPlan(calls, jalerteOperators(context), TimeZone.getDefault())
            return Intent(context, ArcepAlertActivity::class.java).putExtra(EXTRA_PLAN_JSON, plan.toJson().toString())
        }
    }

    // The saved details as JSON, handed to the page only at the step that asks for them.
    private var contactJson = "null"
    private var communeJson = "null"

    private var webView: WebView? = null

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planJson = intent.getStringExtra(EXTRA_PLAN_JSON) ?: run { finish(); return }
        val prefillScript = assets.open("jalerte_prefill.js").bufferedReader().use { it.readText() }
        val injection = prefillInjection(prefillScript, planJson)
        val profileStore = ReporterProfileStore.getInstance(this)
        val plan = JSONObject(planJson)
        val operatorTyped = plan.isNull("jalerteOperator") && !plan.isNull("operatorName")

        setContent {
            MyApplicationTheme {
                val webView = remember { createWebView(injection).also { this@ArcepAlertActivity.webView = it } }
                val scope = rememberCoroutineScope()
                var profile by remember { mutableStateOf<ReporterProfile?>(null) }
                var editingProfile by remember { mutableStateOf(false) }
                var step by remember { mutableIntStateOf(0) }
                var closedNotes by remember { mutableStateOf(emptySet<Note>()) }
                val keyboardOpen = rememberKeyboardOpen()
                fun useProfile(saved: ReporterProfile?) {
                    profile = saved
                    contactJson = saved.toContactJson()
                    communeJson = saved.toCommuneJson()
                    // Saved or erased while the form is open: the script only takes them at their step.
                    if (ArcepAlert.isFormUrl(webView.url)) {
                        webView.evaluateJavascript(contactDelivery(contactJson), null)
                        webView.evaluateJavascript(communeDelivery(communeJson), null)
                    }
                }
                LaunchedEffect(Unit) { useProfile(profileStore.load()) }
                // No JS bridge: the prefill script raises flags (step shown, details needed) and the app polls them.
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(PAGE_POLL_MS)
                        if (!ArcepAlert.isFormUrl(webView.url)) continue
                        webView.evaluateJavascript(PAGE_STATE_QUERY) { json ->
                            val state = parsePageState(json)
                            step = state.step
                            if (!ArcepAlert.isFormUrl(webView.url)) return@evaluateJavascript
                            if (state.needsCommune && communeJson != "null") webView.evaluateJavascript(communeDelivery(communeJson), null)
                            if (state.needsContact && contactJson != "null") webView.evaluateJavascript(contactDelivery(contactJson), null)
                        }
                    }
                }
                if (editingProfile) {
                    ReporterProfileDialog(
                        initial = profile ?: ReporterProfile(),
                        canClear = profile != null,
                        onSave = { edited ->
                            editingProfile = false
                            scope.launch {
                                if (edited.isEmpty()) profileStore.clear() else profileStore.save(edited)
                                useProfile(profileStore.load())
                            }
                        },
                        onClear = {
                            editingProfile = false
                            scope.launch {
                                profileStore.clear()
                                useProfile(null)
                            }
                        },
                        onDismiss = { editingProfile = false }
                    )
                }
                BackHandler {
                    if (webView.canGoBack()) webView.goBack() else finish()
                }
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text("Alerter l'Arcep") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Fermer")
                                }
                            },
                            actions = {
                                IconButton(onClick = { editingProfile = true }) {
                                    Icon(Icons.Outlined.Person, contentDescription = "Mes coordonnées")
                                }
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        val notes = visibleNotes(
                            step = step,
                            keyboardOpen = keyboardOpen,
                            operatorTyped = operatorTyped,
                            hasPostalCode = !profile?.postalCode.isNullOrBlank(),
                            hasContact = profile.toContactJson() != "null",
                            closed = closedNotes,
                        )
                        notes.forEach { note ->
                            FormNote(
                                text = when (note) {
                                    Note.PREFILLED -> "Champs préremplis depuis votre journal d'appels. Vérifiez chaque étape : " +
                                        "rien n'est envoyé sans votre validation."
                                    Note.OPERATOR_TYPED -> "Opérateur absent de la liste de l'Arcep : son nom est saisi dans « Autre »."
                                    Note.NO_POSTAL_CODE -> "Commune obligatoire : enregistrez votre code postal avec l'icône en haut " +
                                        "à droite pour la préremplir."
                                    Note.PROFILE -> "Mémorisez vos coordonnées sur ce téléphone avec l'icône en haut à droite."
                                },
                                highlighted = note != Note.PREFILLED,
                                onClose = { closedNotes = closedNotes + note }
                            )
                        }
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    // The form's document, and whatever the page kept of the user's data, goes with the screen.
    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            // WebView.destroy() must come after the view leaves the hierarchy (still the AndroidView's here).
            (parent as? ViewGroup)?.removeView(this)
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(injection: String) = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return true
                if (host == ArcepAlert.HOST) return false
                // Anything else (arcep.fr pages, CGU…) opens in the user's browser.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            // Only the alert form gets the script and the plan: no other page, no look-alike host.
            override fun onPageFinished(view: WebView, url: String) {
                if (ArcepAlert.isFormUrl(url)) view.evaluateJavascript(injection, null)
            }
        }
        loadUrl(ArcepAlert.URL)
    }
}
