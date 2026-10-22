package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.inspection.ConsentStatus
import com.example.groomingresearchdemo.report.CompatibilityReportBuilder
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen H — Real-app compatibility report.
 *
 * One record per completed or failed inspection in this session, rendered from counts, depths and
 * widget class names only. It is the artefact the researcher pastes into their lab notebook.
 * Reports exist in memory only: closing the app discards them, and nothing here is written to a
 * file, preference, log, or network.
 */
@Composable
fun ReportScreen(uiState: ResearchUiState) {
    val scroll = rememberScrollState()
    val reports = uiState.reports
    var selectedByTime by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Real-app compatibility report", style = MaterialTheme.typography.titleLarge)
        Text(
            "Structural observations only: whether the app exposed a tree, how large it was, how " +
                "much of it carried a label, and which widget classes appeared. No message text, " +
                "sender name or account identifier is part of this record.",
            style = MaterialTheme.typography.bodyMedium,
        )

        if (reports.isEmpty()) {
            InfoCard(title = "No reports in this session") {
                Text(
                    "Run an armed inspection against the selected app to produce one. A failed " +
                        "attempt is recorded too — an app that hides its tree is a legitimate " +
                        "compatibility result, not something to work around.",
                )
            }
        } else {
            InfoCard(title = "Inspections this session (${reports.size})") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    reports.forEach { report ->
                        FilterChip(
                            selected = label(report.timestampMillis) == (selectedByTime ?: label(reports.first().timestampMillis)),
                            onClick = { selectedByTime = label(report.timestampMillis) },
                            label = { Text("${label(report.timestampMillis)} · ${report.outcome.name}") },
                        )
                    }
                }
            }

            val chosen = reports.firstOrNull { label(it.timestampMillis) == selectedByTime } ?: reports.first()
            val text = CompatibilityReportBuilder.build(
                result = chosen,
                consentAccepted = uiState.consentStatus == ConsentStatus.ACCEPTED,
                serviceEnabled = uiState.serviceEnabled,
                targetInstalled = ResearchStore.isPackageInstalled(chosen.targetPackage),
            )
            InfoCard(title = "Report (select-all to copy)", modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(text, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        OutlinedButton(
            onClick = { ResearchStore.clearResults(); selectedByTime = null },
            enabled = reports.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear session reports")
        }

        Text(
            "Clearing results also clears these reports. They are never stored on disk, so a " +
                "process restart discards them automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

private fun label(millis: Long): String = timeFormat.format(Date(millis))
