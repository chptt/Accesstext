package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.inspection.ArmStatus
import com.example.groomingresearchdemo.inspection.ConsentStatus
import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.Screen
import com.example.groomingresearchdemo.ui.StatusRow

/**
 * Screen A — Home dashboard.
 *
 * Reports consent, service status, last experiment, and the session inspection count as four
 * separate facts. When service status has not been verified it is shown as unknown rather than
 * faked to "enabled", per the spec's no-fake-success rule.
 */
@Composable
fun HomeScreen(
    uiState: ResearchUiState,
    onNavigate: (Screen) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Grooming Detection Research Demo", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Accessibility Inspection and Text Preprocessing",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "A consent-based research demonstration for a B.Tech final-year project on online " +
                "grooming detection. It performs a manually armed, one-time STRUCTURAL inspection " +
                "of a messaging app's accessibility tree to test real-app compatibility, and keeps " +
                "text preprocessing separate: synthetic fixtures in the laboratory, plus an " +
                "explicit authorized-import screen for content you are permitted to process. " +
                "No data leaves the device.",
            style = MaterialTheme.typography.bodyMedium,
        )

        InfoCard(title = "Current status") {
            StatusRow("Consent", consentLabel(uiState.consentStatus), emphasized = uiState.consentStatus == ConsentStatus.ACCEPTED)
            StatusRow(
                "Accessibility service",
                if (uiState.serviceEnabled) "Enabled (verified)" else "Not enabled / unverified",
                emphasized = uiState.serviceEnabled,
            )
            StatusRow("Compatibility target", uiState.targetPackage)
            StatusRow("Armed for", uiState.armedTarget ?: "nothing")
            StatusRow("Last experiment", outcomeLabel(uiState.lastResult?.outcome ?: ExperimentOutcome.NONE))
            StatusRow("Inspections completed (this session)", uiState.completedCount.toString())
            StatusRow("Reports held in memory", uiState.reports.size.toString())
        }

        HorizontalDivider()

        InfoCard(title = "Actions") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onNavigate(Screen.CONSENT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Review Consent")
                }
                OutlinedButton(onClick = { onNavigate(Screen.SERVICE) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Enable Accessibility Service")
                }
                OutlinedButton(onClick = { onNavigate(Screen.TARGET) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Choose app under test")
                }
                Button(
                    onClick = {
                        ResearchStore.refreshServiceStatus()
                        ResearchStore.startInspection()
                        onNavigate(Screen.EXPERIMENT)
                    },
                    enabled = uiState.consentStatus == ConsentStatus.ACCEPTED && uiState.serviceEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Start One-Time Inspection")
                }
                OutlinedButton(
                    onClick = { ResearchStore.cancelInspection() },
                    enabled = uiState.armStatus == ArmStatus.ARMED,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancel Inspection")
                }
                OutlinedButton(onClick = { onNavigate(Screen.RESULTS) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View Results")
                }
                OutlinedButton(onClick = { onNavigate(Screen.REPORT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("View Compatibility Report")
                }
                OutlinedButton(
                    onClick = { ResearchStore.clearResults() },
                    enabled = uiState.lastResult != null || uiState.completedCount > 0 || uiState.reports.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear Results")
                }
                Button(onClick = { onNavigate(Screen.LAB) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Preprocessing Laboratory (synthetic)")
                }
                OutlinedButton(onClick = { onNavigate(Screen.IMPORT) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import research-authorized text")
                }
            }
        }

        if (uiState.consentStatus != ConsentStatus.ACCEPTED) {
            Text(
                "Accept the consent disclosure and enable the accessibility service before an " +
                    "inspection can be armed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun consentLabel(status: ConsentStatus): String = when (status) {
    ConsentStatus.NOT_GIVEN -> "Not given"
    ConsentStatus.ACCEPTED -> "Accepted"
    ConsentStatus.DECLINED -> "Declined"
}

private fun outcomeLabel(outcome: ExperimentOutcome): String = when (outcome) {
    ExperimentOutcome.NONE -> "None yet"
    ExperimentOutcome.COMPLETED -> "Completed"
    ExperimentOutcome.FAILED -> "Failed"
    ExperimentOutcome.CANCELLED -> "Cancelled"
}
