package net.slashetc.callinspector

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.slashetc.callinspector.ui.MainScreen
import net.slashetc.callinspector.ui.theme.MyApplicationTheme
import net.slashetc.callinspector.viewmodel.ArcepViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: ArcepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen(
                        uiState = uiState,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh call history if permission granted in background/settings
        viewModel.checkPermissionAndLoad()
    }
}

