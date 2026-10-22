package com.example.groomingresearchdemo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.screens.AuthorizedImportScreen
import com.example.groomingresearchdemo.ui.screens.ConsentScreen
import com.example.groomingresearchdemo.ui.screens.ExperimentScreen
import com.example.groomingresearchdemo.ui.screens.HomeScreen
import com.example.groomingresearchdemo.ui.screens.PreprocessingLabScreen
import com.example.groomingresearchdemo.ui.screens.ReportScreen
import com.example.groomingresearchdemo.ui.screens.ResultsScreen
import com.example.groomingresearchdemo.ui.screens.ReviewScreen
import com.example.groomingresearchdemo.ui.screens.ServiceSetupScreen
import com.example.groomingresearchdemo.ui.screens.TargetScreen

/** The screens described in the spec, wired together by a tiny manual navigation graph. */
enum class Screen(val label: String) {
    HOME("Home dashboard"),
    CONSENT("Consent and disclosure"),
    SERVICE("Accessibility service setup"),
    TARGET("Real-app compatibility target"),
    EXPERIMENT("Experiment controls"),
    RESULTS("Structural inspection results"),
    REPORT("Compatibility report"),
    LAB("Synthetic preprocessing laboratory"),
    IMPORT("Research-authorized text import"),
    REVIEW("Temporary review of extracted sample"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootScreen() {
    val uiState by ResearchStore.uiState.collectAsState(initial = ResearchUiState())
    var current by rememberSaveable { mutableStateOf(Screen.HOME) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current.label) },
                navigationIcon = {
                    if (current != Screen.HOME) {
                        IconButton(onClick = {
                            // Leaving the review screen is leaving the only surface that shows
                            // retained content, so content goes with it. Counts survive.
                            if (current == Screen.REVIEW) ResearchStore.forgetExtractedContent()
                            current = Screen.HOME
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to home")
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (current) {
                Screen.HOME -> HomeScreen(
                    uiState = uiState,
                    onNavigate = { current = it },
                )
                Screen.CONSENT -> ConsentScreen(uiState = uiState)
                Screen.SERVICE -> ServiceSetupScreen(uiState = uiState)
                Screen.TARGET -> TargetScreen(uiState = uiState)
                Screen.EXPERIMENT -> ExperimentScreen(uiState = uiState, onNavigate = { current = it })
                Screen.RESULTS -> ResultsScreen(uiState = uiState)
                Screen.REPORT -> ReportScreen(uiState = uiState)
                Screen.LAB -> PreprocessingLabScreen()
                Screen.IMPORT -> AuthorizedImportScreen()
                Screen.REVIEW -> ReviewScreen(onDone = {
                    ResearchStore.forgetExtractedContent()
                    current = Screen.HOME
                })
            }
        }
    }
}

/** Reusable content card used across the screens for a consistent, panel-friendly look. */
@Composable
fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

/** A labelled value line used for status displays, with an accent colour for the value. */
@Composable
fun StatusRow(label: String, value: String, emphasized: Boolean = false) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = if (emphasized) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
