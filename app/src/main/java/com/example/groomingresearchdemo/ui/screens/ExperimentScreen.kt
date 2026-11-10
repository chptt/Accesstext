package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.inspection.ArmResult
import com.example.groomingresearchdemo.inspection.ArmStatus
import com.example.groomingresearchdemo.inspection.ConsentStatus
import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.Screen
import com.example.groomingresearchdemo.ui.StatusRow

/**
 * Screen D — Experiment controls.
 *
 * The inspection is armed only by an explicit button press and is consumed once by the first
 * qualifying event from the app selected on the target screen. It never re-arms itself and never
 * runs on a timer.
 *
 * The two modes are chosen by which Start button is pressed: node counts only, or node counts plus
 * message-level extraction. Both are one deliberate press and one root read; the extraction mode
 * walks the SAME snapshot a second time. The choice is captured at arm time, so it cannot change
 * underneath an inspection that is already waiting, and text is still only rendered on the separate
 * review screen.
 */
@Composable
fun ExperimentScreen(
    uiState: ResearchUiState,
    onNavigate: (Screen) -> Unit = {},
) {
    val scroll = rememberScrollState()
    var armMessage by remember { mutableStateOf<String?>(null) }
    val installed = ResearchStore.isPackageInstalled(uiState.targetPackage)
    val canArm = uiState.consentStatus == ConsentStatus.ACCEPTED &&
        uiState.serviceEnabled &&
        uiState.armStatus == ArmStatus.NOT_ARMED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("One-shot structural inspection", style = MaterialTheme.typography.titleLarge)

        InfoCard(title = "What this does") {
            Text(
                "You arm the inspection once. When you then bring the app under test to the " +
                    "foreground, Android delivers a window event for that package. The service " +
                    "reads the ACTIVE window's accessibility tree once and reports structural " +
                    "statistics — node counts, text availability, widget classes, depth. If you " +
                    "chose the extraction mode, the same snapshot is walked a second time for " +
                    "message candidates before the service disarms. It does not scroll, click, " +
                    "send, navigate, screenshot, or record."
            )
            StatusRow("App under test", uiState.targetPackage, emphasized = true)
            StatusRow(
                "Installed on this device",
                if (installed) "Yes" else "No — install and sign in manually first",
            )
            StatusRow("Armed target", uiState.armedTarget ?: "nothing")
            StatusRow(
                "Armed with message-level stage",
                if (uiState.armedExtraction) "Yes (this inspection)" else "No",
            )
        }

        InfoCard(title = "Two one-press modes") {
            Text(
                "Node counts only — the structural pass that produced R1–R8: nodes visited, text " +
                    "availability, widget classes, depth and truncation, with no message records.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Node counts + message extraction — the same single root read, then a second pass " +
                    "over that snapshot: sample geometry and structure, apply the documented " +
                    "rule-based classifier, and report candidate / accepted / ambiguous / rejected " +
                    "counts beside the structural ones. The results screen stays structural; " +
                    "message records appear only if you separately open the review screen.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Neither mode adds a second trigger, an extra tree read, a scroll, a retry or any " +
                    "storage. Candidate text exists in RAM for one inspection only, is overwritten " +
                    "by the next one, and is dropped on cancel, revoke, disconnect or Forget. The " +
                    "mode is captured when you press Start, so changing it afterwards cannot widen " +
                    "an inspection that is already waiting.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Unvalidated on real apps. No annotated, consented ground-truth sample exists yet, " +
                    "so no precision or recall number for the extraction stage may be quoted — " +
                    "including by this screen. Sender labels and timestamps are OFF by default and " +
                    "are never invented to fill a gap.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        InfoCard(title = "State") {
            Text("Indicator: ${armIndicator(uiState)}")
            armMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }

        Button(
            onClick = {
                ResearchStore.refreshServiceStatus()
                armMessage = describeArmResult(
                    ResearchStore.startInspection(messageLevelExtraction = false),
                    uiState.targetPackage,
                    withExtraction = false,
                )
            },
            enabled = canArm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start Inspection — Node Counts Only")
        }

        Button(
            onClick = {
                ResearchStore.refreshServiceStatus()
                armMessage = describeArmResult(
                    ResearchStore.startInspection(messageLevelExtraction = true),
                    uiState.targetPackage,
                    withExtraction = true,
                )
            },
            enabled = canArm,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Start Inspection + Message Extraction")
        }

        OutlinedButton(
            onClick = {
                if (ResearchStore.cancelInspection()) armMessage = "Inspection disarmed immediately; any retained sample was forgotten."
                else armMessage = "Nothing to cancel."
            },
            enabled = uiState.armStatus == ArmStatus.ARMED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Stop / Cancel Inspection")
        }

        OutlinedButton(
            onClick = { onNavigate(Screen.RESULTS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open the inspection result summary")
        }

        OutlinedButton(
            onClick = { onNavigate(Screen.TARGET) },
            enabled = uiState.armStatus == ArmStatus.NOT_ARMED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Change the app under test")
        }

        OutlinedButton(
            onClick = { onNavigate(Screen.REPORT) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open the compatibility report")
        }

        OutlinedButton(
            onClick = { onNavigate(Screen.REVIEW) },
            enabled = uiState.hasRetainedContent,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Review extracted sample text (separate, deliberate)")
        }

        Text(
            "If the app under test is not in the foreground, or its accessibility root is " +
                "unavailable, the result screen reports that as a distinct failed stage rather " +
                "than retrying. An app that exposes nothing is a normal compatibility finding, and " +
                "so is a screen that yields candidates but no establishable message boundaries.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun describeArmResult(
    result: ArmResult,
    target: String,
    withExtraction: Boolean,
): String = when (result) {
    ArmResult.CONSENT_REQUIRED -> "Cannot arm: consent not accepted."
    ArmResult.SERVICE_REQUIRED -> "Cannot arm: accessibility service is not enabled."
    ArmResult.TARGET_REQUIRED -> "Cannot arm: no app under test is selected."
    ArmResult.ALREADY_ARMED -> "An inspection is already armed."
    ArmResult.ARMED -> if (withExtraction) {
        "Armed for $target with message extraction. Now bring that app to the foreground to produce one qualifying event."
    } else {
        "Armed for $target (node counts only). Now bring that app to the foreground to produce one qualifying event."
    }
}

private fun armIndicator(uiState: ResearchUiState): String = when (uiState.armStatus) {
    ArmStatus.NOT_ARMED -> when (uiState.lastResult?.outcome) {
        ExperimentOutcome.COMPLETED -> "Inspection completed (disarmed)."
        ExperimentOutcome.FAILED -> "Inspection failed (disarmed)."
        ExperimentOutcome.CANCELLED -> "Cancelled."
        else -> "Not armed."
    }
    ArmStatus.ARMED -> "Waiting for a qualifying accessibility event."
    ArmStatus.EXECUTING -> "Inspection executing."
}
