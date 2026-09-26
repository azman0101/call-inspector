package net.slashetc.callinspector.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import net.slashetc.callinspector.data.model.CallLogEntry
import net.slashetc.callinspector.ui.theme.MyApplicationTheme
import net.slashetc.callinspector.util.SignalConsoPlan
import net.slashetc.callinspector.util.SignalConsoReport
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/** Opens the SignalConso "démarchage abusif" form and prefills it from a call; the user submits. */
class SignalConsoActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_PLAN_JSON = "plan_json"

        fun start(context: Context, call: CallLogEntry, history: List<CallLogEntry>) {
            val plan = SignalConsoReport.buildPlan(call, history, System.currentTimeMillis(), TimeZone.getDefault())
            context.startActivity(
                Intent(context, SignalConsoActivity::class.java).putExtra(EXTRA_PLAN_JSON, plan.toJson().toString())
            )
        }

        private fun SignalConsoPlan.toJson() = JSONObject().apply {
            put("problem", SignalConsoReport.PROBLEM)
            put("subcategory", subcategory ?: JSONObject.NULL)
            put("phone", phone ?: JSONObject.NULL)
            put("dates", JSONArray(dates))
            put("description", description)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val planJson = intent.getStringExtra(EXTRA_PLAN_JSON) ?: run { finish(); return }
        val prefillScript = assets.open("signalconso_prefill.js").bufferedReader().use { it.readText() }
        // JSONObject.toString() yields a valid JS object literal, so the plan can't break out of the script.
        val injection = "window.__icPlan = $planJson;\n$prefillScript"

        setContent {
            MyApplicationTheme {
                val webView = remember { createWebView(injection) }
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
                            }
                        )
                    }
                ) { padding ->
                    Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Les champs connus sont préremplis depuis votre journal d'appels. " +
                                    "Vérifiez chaque étape : rien n'est envoyé tant que vous ne validez pas le signalement.",
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(injection: String) = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val host = request.url.host ?: return true
                if (host == SignalConsoReport.HOST) return false
                // Anything else (annuaire des entreprises, Bloctel…) opens in the user's browser.
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (Uri.parse(url).host == SignalConsoReport.HOST) view.evaluateJavascript(injection, null)
            }
        }
        loadUrl(SignalConsoReport.URL)
    }
}
