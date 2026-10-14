package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.*
import com.example.ui.theme.GitCompilerTheme
import com.example.ui.viewmodel.AppScreen
import com.example.ui.viewmodel.AppTab
import com.example.ui.viewmodel.CompilerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GitCompilerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    GitCompilerApp()
                }
            }
        }
    }
}

@Composable
fun GitCompilerApp(
    viewModel: CompilerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.currentScreen == AppScreen.AUTH) {
        AuthScreen(viewModel = viewModel, uiState = uiState)
    } else {
        // App principal com Barra de Navegação de 3 botões: Compilador, Histórico, Perfil
        val isLogsScreen = uiState.currentScreen == AppScreen.COMPILATION_LOGS

        Scaffold(
            bottomBar = {
                if (!isLogsScreen) {
                    NavigationBar(
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        // 1. Compilador
                        NavigationBarItem(
                            selected = uiState.selectedTab == AppTab.COMPILER,
                            onClick = { viewModel.selectTab(AppTab.COMPILER) },
                            icon = {
                                Icon(
                                    imageVector = if (uiState.selectedTab == AppTab.COMPILER) Icons.Filled.Build else Icons.Outlined.Build,
                                    contentDescription = "Compilador"
                                )
                            },
                            label = { Text("Compilador") },
                            modifier = Modifier.testTag("nav_compiler")
                        )

                        // 2. Histórico
                        NavigationBarItem(
                            selected = uiState.selectedTab == AppTab.HISTORY,
                            onClick = { viewModel.selectTab(AppTab.HISTORY) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (uiState.compilationHistory.isNotEmpty()) {
                                            Badge {
                                                Text("${uiState.compilationHistory.size}")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = if (uiState.selectedTab == AppTab.HISTORY) Icons.Filled.History else Icons.Outlined.History,
                                        contentDescription = "Histórico"
                                    )
                                }
                            },
                            label = { Text("Histórico") },
                            modifier = Modifier.testTag("nav_history")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Crossfade(
                targetState = uiState.selectedTab,
                label = "TabTransition",
                modifier = Modifier.padding(innerPadding)
            ) { tab ->
                when (tab) {
                    AppTab.COMPILER -> {
                        when (uiState.currentScreen) {
                            AppScreen.COMPILATION_STEPS -> CompilationStepsScreen(viewModel = viewModel, uiState = uiState)
                            AppScreen.COMPILATION_LOGS -> CompilationLogsScreen(viewModel = viewModel, uiState = uiState)
                            else -> SelectZipScreen(viewModel = viewModel, uiState = uiState)
                        }
                    }
                    AppTab.HISTORY -> {
                        HistoryScreen(viewModel = viewModel, uiState = uiState)
                    }
                    AppTab.PROFILE -> {
                        ProfileScreen(viewModel = viewModel, uiState = uiState)
                    }
                }
            }
        }
    }
}
