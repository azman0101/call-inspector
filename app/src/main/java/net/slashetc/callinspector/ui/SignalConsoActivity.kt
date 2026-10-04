package net.slashetc.callinspector.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.slashetc.callinspector.data.db.ReporterProfile
import net.slashetc.callinspector.data.db.ReporterProfileStore
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.ui.theme.MyApplicationTheme
import net.slashetc.callinspector.util.ReportedCompany
import net.slashetc.callinspector.util.SignalConsoPlan
import net.slashetc.callinspector.util.SignalConsoReport
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/** Opens the SignalConso "démarchage abusif" form and prefills it from a call; the user submits. */
class SignalConsoActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PLAN_JSON = "plan_json"
        private const val EXTRA_REPORTED_NUMBER = "reported_number"
        private const val PAGE_POLL_MS = 1500L

        /** What the prefill script tells the app; flags only, never the user's data. */
        internal const val PAGE_STATE_QUERY =
            "({sent: !!window.__icReportSent, needsContact: !!window.__icNeedsContact, step: String(window.__icStep || 'other')})"

        /** The form's step, as far as the app needs it: which notes to show. */
        internal enum class FormStep { COMPANY, CONTACT, OTHER }

        internal data class PageState(val sent: Boolean, val needsContact: Boolean, val step: FormStep = FormStep.OTHER)

        /** Reads [PAGE_STATE_QUERY]'s result; anything else (page without the script, error) is an empty state. */
        internal fun parsePageState(json: String?): PageState =
            runCatching { JSONObject(json ?: "") }.getOrNull()
                ?.let {
                    val step = when (it.optString("step")) {
                        "company" -> FormStep.COMPANY
                        "contact" -> FormStep.CONTACT
                        else -> FormStep.OTHER
                    }
                    PageState(it.optBoolean("sent"), it.optBoolean("needsContact"), step)
                }
                ?: PageState(sent = false, needsContact = false)

        /** The notes above the form. Each is short, shown at the step it is about, and can be closed. */
        internal enum class Note { PREFILLED, DEFAULT_REASON, OPERATOR, PROFILE }

        /**
         * The notes to show. While the keyboard is open none is: the form needs the whole height to type in.
         * The general note and the default reason are about step 1, so they go once the form reached step 2.
         */
        internal fun visibleNotes(
            step: FormStep,
            pastFirstStep: Boolean,
            keyboardOpen: Boolean,
            isDefaultReason: Boolean,
            hasOperator: Boolean,
            hasProfile: Boolean,
            closed: Set<Note>,
        ): List<Note> {
            if (keyboardOpen) return emptyList()
            return buildList {
                if (!pastFirstStep) add(Note.PREFILLED)
                if (!pastFirstStep && isDefaultReason) add(Note.DEFAULT_REASON)
                if (step == FormStep.COMPANY && hasOperator) add(Note.OPERATOR)
                if (step == FormStep.CONTACT && !hasProfile) add(Note.PROFILE)
            }.filterNot { it in closed }
        }

        /** The prefill script, called with the plan: the plan stays in its closure, never in a global. */
        internal fun prefillInjection(script: String, planJson: String): String =
            script.trimEnd().removeSuffix(";") + "(" + planJson.toJsExpression() + ");"

        /** Hands the contact details (or null, to drop them) to the script, which keeps them for step 4 only. */
        internal fun contactDelivery(contactJson: String): String =
            "if (window.__icFillContact) window.__icFillContact(${contactJson.toJsExpression()});"

        /** Opens the form for [call]; the activity result is RESULT_OK once a report was sent and counted. */
        fun intent(context: Context, call: CallLogEntry, history: List<CallLogEntry>): Intent {
            val plan = SignalConsoReport.buildPlan(call, history, System.currentTimeMillis(), TimeZone.getDefault())
            return Intent(context, SignalConsoActivity::class.java)
                .putExtra(EXTRA_PLAN_JSON, plan.toJson().toString())
                .putExtra(EXTRA_REPORTED_NUMBER, call.normalizedNumber)
        }

        private fun SignalConsoPlan.toJson() = JSONObject().apply {
            put("problem", SignalConsoReport.PROBLEM)
            put("subcategory", subcategory)
            put("isDefaultReason", isDefaultReason)
            put("phone", phone ?: JSONObject.NULL)
            put("dates", JSONArray(dates))
            put("company", company?.let {
                JSONObject()
                    .put("source", it.source.name)
                    .put("name", it.name)
                    .put("siret", it.siret ?: JSONObject.NULL)
            } ?: JSONObject.NULL)
            put("description", description)
        }

        private fun ReporterProfile?.toContactJson(): String = this?.takeUnless { it.isEmpty() }?.let {
            JSONObject()
                .put("firstName", it.firstName)
                .put("lastName", it.lastName)
                .put("email", it.email)
                .put("phone", it.phone)
                .put("referenceNumber", it.referenceNumber)
                .put("shareContact", it.shareContact ?: JSONObject.NULL)
                .toString()
        } ?: "null"

        /** Encodes a JSON string as a safe JavaScript expression using JSON.parse(). */
        internal fun String.toJsExpression(): String =
            "JSON.parse(" + JSONObject.quote(this)
                .replace("\u2028", "\\u2028")
                .replace("\u2029", "\\u2029") + ")"
    }

    // The saved contact details as JSON, handed to the page only when its step 4 asks for them.
    private var contactJson = "null"

    private var webView: WebView? = null

    // A form opened from the app counts as one report at most.
    private var reportRecorded = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planJson = intent.getStringExtra(EXTRA_PLAN_JSON) ?: run { finish(); return }
        val prefillScript = assets.open("signalconso_prefill.js").bufferedReader().use { it.readText() }
        val injection = prefillInjection(prefillScript, planJson)
        val profileStore = ReporterProfileStore.getInstance(this)
        val operatorName = JSONObject(planJson).optJSONObject("company")
            ?.takeIf { it.optString("source") == ReportedCompany.Source.OPERATOR.name }
            ?.optString("name")
        val isDefaultReason = JSONObject(planJson).optBoolean("isDefaultReason")
        val reportedNumber = intent.getStringExtra(EXTRA_REPORTED_NUMBER)

        setContent {
            MyApplicationTheme {
                val webView = remember { createWebView(injection).also { this@SignalConsoActivity.webView = it } }
                val scope = rememberCoroutineScope()
                var profile by remember { mutableStateOf<ReporterProfile?>(null) }
                var editingProfile by remember { mutableStateOf(false) }
                var formStep by remember { mutableStateOf(FormStep.OTHER) }
                var pastFirstStep by remember { mutableStateOf(false) }
                var closedNotes by remember { mutableStateOf(emptySet<Note>()) }
                val keyboardOpen = rememberKeyboardOpen()
                fun useProfile(saved: ReporterProfile?) {
                    profile = saved
                    contactJson = saved.toContactJson()
                    // Saved or erased while the form is open: the script takes them at step 4 only.
                    if (SignalConsoReport.isFormUrl(webView.url)) webView.evaluateJavascript(contactDelivery(contactJson), null)
                }
                LaunchedEffect(Unit) { useProfile(profileStore.load()) }
                // No JS bridge: the prefill script raises flags (step 4 shown, report sent) and the app polls them.
                LaunchedEffect(Unit) {
                    var reportSent = false
                    while (!reportSent) {
                        delay(PAGE_POLL_MS)
                        if (!SignalConsoReport.isSignalConsoUrl(webView.url)) continue
                        webView.evaluateJavascript(PAGE_STATE_QUERY) { json ->
                            val state = parsePageState(json)
                            formStep = state.step
                            if (state.step != FormStep.OTHER) pastFirstStep = true
                            if (state.needsContact && contactJson != "null" && SignalConsoReport.isFormUrl(webView.url)) {
                                webView.evaluateJavascript(contactDelivery(contactJson), null)
                            }
                            if (!state.sent) return@evaluateJavascript
                            reportSent = true
                            if (reportRecorded || reportedNumber == null) return@evaluateJavascript
                            reportRecorded = true
                            scope.launch {
                                runCatching { profileStore.recordReport(reportedNumber, System.currentTimeMillis()) }
                                    .onSuccess {
                                        setResult(RESULT_OK)
                                        Toast.makeText(this@SignalConsoActivity, "Signalement comptabilisé pour ce numéro", Toast.LENGTH_SHORT).show()
                                    }
                            }
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
                            title = { Text("Signaler sur SignalConso") },
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
                            step = formStep,
                            pastFirstStep = pastFirstStep,
                            keyboardOpen = keyboardOpen,
                            isDefaultReason = isDefaultReason,
                            hasOperator = operatorName != null,
                            hasProfile = profile != null,
                            closed = closedNotes,
                        )
                        notes.forEach { note ->
                            FormNote(
                                text = when (note) {
                                    Note.PREFILLED -> "Champs préremplis depuis votre journal d'appels. Vérifiez chaque étape : " +
                                        "rien n'est envoyé sans votre validation."
                                    Note.DEFAULT_REASON -> "Motif par défaut : démarché moins de 60 jours après votre refus. " +
                                        "Changez-le à l'étape 1 s'il ne correspond pas."
                                    Note.OPERATOR -> "Entreprise : $operatorName, l'opérateur du numéro, pas forcément l'appelant. " +
                                        "Si vous connaissez l'appelant, choisissez « Par son nom »."
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
                if (host == SignalConsoReport.HOST) return false
                // Anything else (annuaire des entreprises…) opens in the user's browser.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            // Only the report form gets the script and the plan: no other page of the site, no look-alike host.
            override fun onPageFinished(view: WebView, url: String) {
                if (SignalConsoReport.isFormUrl(url)) view.evaluateJavascript(injection, null)
            }
        }
        loadUrl(SignalConsoReport.URL)
    }
}

/** One short note above the form, closable. */
@Composable
private fun FormNote(text: String, highlighted: Boolean, onClose: () -> Unit) {
    Surface(
        color = if (highlighted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(16.dp))
            Text(
                text = text,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                modifier = Modifier.weight(1f).padding(vertical = 6.dp)
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Masquer", modifier = Modifier.size(16.dp))
            }
        }
    }
}

/** Whether the soft keyboard is open, from the window insets (works with and without edge-to-edge). */
@Composable
private fun rememberKeyboardOpen(): Boolean {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            open = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return open
}

@Composable
private fun ReporterProfileDialog(
    initial: ReporterProfile,
    canClear: Boolean,
    onSave: (ReporterProfile) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    var firstName by remember { mutableStateOf(initial.firstName) }
    var lastName by remember { mutableStateOf(initial.lastName) }
    var email by remember { mutableStateOf(initial.email) }
    var phone by remember { mutableStateOf(initial.phone) }
    var referenceNumber by remember { mutableStateOf(initial.referenceNumber) }
    var shareContact by remember { mutableStateOf(initial.shareContact) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mes coordonnées") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Préremplies dans SignalConso. Elles sont enregistrées uniquement sur ce téléphone " +
                        "(ni sauvegarde, ni transfert) et n'en sortent que dans un signalement que vous validez.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Prénom") }, singleLine = true)
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(
                    email, { email = it }, label = { Text("Email") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("Téléphone (facultatif)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                OutlinedTextField(
                    referenceNumber, { referenceNumber = it }, label = { Text("Numéro de référence (facultatif)") },
                    placeholder = { Text("ex : ZYX987654321") }, singleLine = true
                )
                Text("Partager vos coordonnées avec l'entreprise ?", fontSize = 13.sp)
                listOf(null to "Ne pas préremplir", true to "Je partage", false to "Je ne partage pas").forEach { (value, text) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = shareContact == value, onClick = { shareContact = value }, role = Role.RadioButton)
                    ) {
                        RadioButton(selected = shareContact == value, onClick = null)
                        Text(text, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(ReporterProfile(firstName, lastName, email, phone, referenceNumber, shareContact))
                }
            ) { Text("Enregistrer") }
        },
        dismissButton = {
            Row {
                if (canClear) TextButton(onClick = onClear) { Text("Effacer") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    )
}
