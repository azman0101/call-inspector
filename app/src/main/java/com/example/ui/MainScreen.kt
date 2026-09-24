package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.CallHistoryScreen
import com.example.ui.screens.NumberLookupScreen
import com.example.ui.screens.StatsAndInfoScreen
import com.example.ui.theme.ArcepBlue
import com.example.ui.theme.ArcepNavy
import com.example.viewmodel.ArcepUiState
import com.example.viewmodel.ArcepViewModel

@Composable
fun MainScreen(
    uiState: ArcepUiState,
    viewModel: ArcepViewModel,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userNotice) {
        uiState.userNotice?.let { notice ->
            snackbarHostState.showSnackbar(notice)
            viewModel.clearUserNotice()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = uiState.currentTab == 0,
                    onClick = { viewModel.setTab(0) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.currentTab == 0) Icons.Filled.Call else Icons.Outlined.Call,
                            contentDescription = "Journal"
                        )
                    },
                    label = { Text("Journal", fontSize = 12.sp, fontWeight = if (uiState.currentTab == 0) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ArcepBlue,
                        indicatorColor = ArcepNavy
                    ),
                    modifier = Modifier.testTag("nav_tab_journal")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == 1,
                    onClick = { viewModel.setTab(1) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.currentTab == 1) Icons.Filled.Search else Icons.Outlined.Search,
                            contentDescription = "Recherche"
                        )
                    },
                    label = { Text("Recherche", fontSize = 12.sp, fontWeight = if (uiState.currentTab == 1) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ArcepBlue,
                        indicatorColor = ArcepNavy
                    ),
                    modifier = Modifier.testTag("nav_tab_recherche")
                )

                NavigationBarItem(
                    selected = uiState.currentTab == 2,
                    onClick = { viewModel.setTab(2) },
                    icon = {
                        Icon(
                            imageVector = if (uiState.currentTab == 2) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                            contentDescription = "Observatoire"
                        )
                    },
                    label = { Text("Observatoire", fontSize = 12.sp, fontWeight = if (uiState.currentTab == 2) FontWeight.Bold else FontWeight.Normal) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = ArcepBlue,
                        indicatorColor = ArcepNavy
                    ),
                    modifier = Modifier.testTag("nav_tab_observatoire")
                )
            }
        },
        floatingActionButton = {
            if (uiState.currentTab == 0) {
                FloatingActionButton(
                    onClick = { viewModel.setTab(1) },
                    containerColor = ArcepBlue,
                    contentColor = Color.White,
                    modifier = Modifier
                        .padding(bottom = 12.dp)
                        .testTag("fab_lookup_number")
                ) {
                    Icon(imageVector = Icons.Default.Dialpad, contentDescription = "Saisir un numéro")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = uiState.currentTab,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "TabContentTransition"
            ) { tab ->
                when (tab) {
                    0 -> CallHistoryScreen(uiState = uiState, viewModel = viewModel)
                    1 -> NumberLookupScreen(uiState = uiState, viewModel = viewModel)
                    2 -> StatsAndInfoScreen(
                        uiState = uiState,
                        onTriggerUpdate = { viewModel.triggerDatabaseUpdate() },
                        onResetUpdateStatus = { viewModel.resetUpdateStatus() }
                    )
                    else -> CallHistoryScreen(uiState = uiState, viewModel = viewModel)
                }
            }
        }
    }
}
