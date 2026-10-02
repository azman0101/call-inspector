package net.slashetc.callinspector

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.slashetc.callinspector.ui.MainScreen
import net.slashetc.callinspector.ui.components.LegalTermsDialog
import net.slashetc.callinspector.ui.theme.MyApplicationTheme
import net.slashetc.callinspector.util.LegalTerms
import net.slashetc.callinspector.viewmodel.ArcepViewModel

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SHOW_DEMARCHAGE = "net.slashetc.callinspector.SHOW_DEMARCHAGE"
        const val EXTRA_SEARCH_NUMBER = "net.slashetc.callinspector.SEARCH_NUMBER"
    }

    private val viewModel: ArcepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShortcut(intent)
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
                // First launch, or terms changed since the version the user accepted.
                val terms = remember { LegalTerms.load(this) }
                var mustAcceptTerms by remember { mutableStateOf(LegalTerms.needsAcceptance(this, terms)) }
                if (mustAcceptTerms) {
                    LegalTermsDialog(
                        markdown = terms,
                        requireAcceptance = true,
                        onAccept = {
                            LegalTerms.accept(this, terms)
                            mustAcceptTerms = false
                        },
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShortcut(intent)
    }

    // From the home screen widget (telemarketing calls) or the quick settings tile (one caller's number).
    private fun handleShortcut(intent: Intent?) {
        when {
            intent == null -> Unit
            intent.getBooleanExtra(EXTRA_SHOW_DEMARCHAGE, false) -> viewModel.showDemarchageCalls()
            intent.hasExtra(EXTRA_SEARCH_NUMBER) -> viewModel.showCallsFrom(intent.getStringExtra(EXTRA_SEARCH_NUMBER).orEmpty())
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh call history if permission granted in background/settings
        viewModel.checkPermissionAndLoad()
    }
}

