package net.slashetc.callinspector.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
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
        private const val REPORT_SENT_POLL_MS = 1500L

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

    // The saved contact details, as a JS literal, injected with the plan on every page load.
    private var contactJson = "null"

    // A form opened from the app counts as one report at most.
    private var reportRecorded = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planJson = intent.getStringExtra(EXTRA_PLAN_JSON) ?: run { finish(); return }
        val prefillScript = assets.open("signalconso_prefill.js").bufferedReader().use { it.readText() }
        // Safely pass JSON strings as parsed JS expressions via JSON.parse(quote(...)).
        val injection = { "window.__icPlan = ${planJson.toJsExpression()};\nwindow.__icContact = ${contactJson.toJsExpression()};\n$prefillScript" }
        val profileStore = ReporterProfileStore.getInstance(this)
        val operatorName = JSONObject(planJson).optJSONObject("company")
            ?.takeIf { it.optString("source") == ReportedCompany.Source.OPERATOR.name }
            ?.optString("name")
        val isDefaultReason = JSONObject(planJson).optBoolean("isDefaultReason")
        val reportedNumber = intent.getStringExtra(EXTRA_REPORTED_NUMBER)

        setContent {
            MyApplicationTheme {
                val webView = remember { createWebView(injection) }
                val scope = rememberCoroutineScope()
                var profile by remember { mutableStateOf<ReporterProfile?>(null) }
                var editingProfile by remember { mutableStateOf(false) }
                fun useProfile(saved: ReporterProfile?) {
                    profile = saved
                    contactJson = saved.toContactJson()
                    if (Uri.parse(webView.url ?: "").host == SignalConsoReport.HOST) {
                        webView.evaluateJavascript(
                            "window.__icContact = ${contactJson.toJsExpression()}; if (window.__icPrefillRun) window.__icPrefillRun();", null
                        )
                    }
                }
                LaunchedEffect(Unit) { useProfile(profileStore.load()) }
                // No JS bridge: the prefill script flags SignalConso's acknowledgment page and the app reads the flag.
                LaunchedEffect(Unit) {
                    while (!reportRecorded && reportedNumber != null) {
                        delay(REPORT_SENT_POLL_MS)
                        if (Uri.parse(webView.url ?: "").host != SignalConsoReport.HOST) continue
                        webView.evaluateJavascript("!!window.__icReportSent") { sent ->
                            if (sent != "true" || reportRecorded) return@evaluateJavascript
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
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Les champs connus sont préremplis depuis votre journal d'appels. " +
                                    "Vérifiez chaque étape : rien n'est envoyé tant que vous ne validez pas le signalement." +
                                    if (profile == null) {
                                        " Vos coordonnées (étape 4) peuvent être mémorisées sur ce téléphone via l'icône en haut à droite."
                                    } else {
                                        ""
                                    },
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        if (isDefaultReason) {
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "Aucun motif ne ressort de l'appel (en semaine, aux heures autorisées) : motif par défaut " +
                                        "« demandé à ne pas être démarché, moins de 60 jours après mon refus ». Vérifiez qu'il " +
                                        "correspond à votre situation, sinon choisissez-en un autre à l'étape 1 ou ajoutez une note " +
                                        "à l'appel (ex. « isolation », « CPF », « se fait passer pour la CAF »).",
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                        if (operatorName != null) {
                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    Icon(
                                        Icons.Outlined.Info,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp).size(18.dp)
                                    )
                                    Text(
                                        text = "Entreprise préremplie : $operatorName, l'opérateur auquel l'ARCEP a attribué ce numéro. " +
                                            "L'entreprise qui vous a appelé n'est pas identifiable depuis le numéro : elle utilise un numéro " +
                                            "fourni par cet opérateur (directement ou via un revendeur), qui est tenu de veiller à l'usage " +
                                            "qu'en font ses clients. Si vous connaissez le nom de " +
                                            "l'appelant, choisissez plutôt « Par son nom ».",
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(injection: () -> String) = WebView(this).apply {
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

            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == SignalConsoReport.HOST) view.evaluateJavascript(injection(), null)
            }
        }
        loadUrl(SignalConsoReport.URL)
    }
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
