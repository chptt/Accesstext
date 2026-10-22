package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.InspectionResult
import com.example.groomingresearchdemo.inspection.extraction.SpeakerAttribution
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.StatusRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen E — Structural inspection results.
 *
 * Every stage is reported separately. A structural inspection is NOT marked successful just
 * because the service received an event; COMPLETED additionally requires that an active root
 * was available and a traversal produced statistics. No Instagram message content is ever shown.
 *
 * The message-level section below is a COUNTS section. It reports how many nodes became candidates,
 * how many were accepted, ambiguous or rejected, what the ordering basis was, and whether speakers
 * or timestamps were recovered — and it renders none of the text those candidates were built from.
 * Reviewing content takes a separate, named action from the experiment controls.
 */
@Composable
fun ResultsScreen(uiState: ResearchUiState) {
    val scroll = rememberScrollState()
    val result = uiState.lastResult

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Structural inspection results", style = MaterialTheme.typography.titleLarge)

        if (result == null) {
            Text(
                "No experiment has produced a result in this session yet.",
                style = MaterialTheme.typography.bodyMedium,
            )
        } else {
            InfoCard(title = "Outcome") {
                StatusRow("Outcome", result.outcome.name, emphasized = true)
                StatusRow("Timestamp", formatTime(result.timestampMillis))
                StatusRow("App under test", result.targetPackage)
                StatusRow("Target package match", yesNo(result.packageMatched))
                StatusRow("Accessibility event received", yesNo(result.eventReceived))
                StatusRow("Active root available", yesNo(result.rootAvailable))
                if (result.outcome == ExperimentOutcome.FAILED || result.failureReason != null) {
                    StatusRow("Failure reason", result.failureReason ?: "Unknown")
                }
            }

            val stats = result.stats
            if (stats != null) {
                InfoCard(title = "Structure (counts only, no content)") {
                    StatusRow("Total nodes visited", stats.nodesVisited.toString())
                    StatusRow("Nodes exposing text", stats.nodesWithText.toString())
                    StatusRow("Nodes with an accessibility label only", stats.nodesWithContentDescriptionOnly.toString())
                    StatusRow("Text-bearing nodes", "${stats.textBearingNodes} (${stats.textAvailabilityPercent}%)")
                    StatusRow("Nodes with empty / unavailable text", stats.emptyOrUnavailableTextNodes.toString())
                    StatusRow("Aggregate text characters exposed", stats.totalTextCharacters.toString())
                    StatusRow("Maximum hierarchy depth", stats.maxDepth.toString())
                    StatusRow("Distinct widget classes", stats.distinctNodeClasses.toString())
                    StatusRow("Nodes whose children were unreadable", stats.unreadableChildNodes.toString())
                    StatusRow("Traversal truncated at cap", yesNo(stats.truncated))
                }

                if (stats.nodeClassCounts.isNotEmpty()) {
                    InfoCard(title = "Widget classes seen") {
                        stats.nodeClassCounts.entries
                            .sortedByDescending { it.value }
                            .take(10)
                            .forEach { (className, count) -> StatusRow(className, count.toString()) }
                    }
                }

                if (stats.notes.isNotEmpty()) {
                    InfoCard(title = "Structural notes") {
                        stats.notes.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                Text(
                    "No tree was traversed (root unavailable or inspection cancelled).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            val extraction = result.extraction
            if (extraction == null) {
                InfoCard(title = "Message-level stage") {
                    Text(
                        "Not run for this inspection — the message-level opt-in was not ticked when " +
                            "the inspection was armed. That is the default, and it is the same " +
                            "structural-only path that produced observations R1-R8.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            } else {
                InfoCard(title = "Message-level candidates (counts only, no content)") {
                    StatusRow("Reconstruction status", extraction.status.name, emphasized = true)
                    StatusRow("Nodes sampled", extraction.nodesSampled.toString())
                    StatusRow("Candidate messages", extraction.candidateCount.toString())
                    StatusRow("Accepted candidates", extraction.acceptedCount.toString())
                    StatusRow("Ambiguous candidates", extraction.ambiguousCount.toString())
                    StatusRow("Rejected (interface chrome etc.)", extraction.rejectedCount.toString())
                    StatusRow("Unplaced candidates (no order)", extraction.candidateStageRecords.toString())
                    StatusRow("Duplicate visits suppressed", extraction.duplicateVisitsSuppressed.toString())
                    StatusRow("Container echoes suppressed", extraction.containerEchoesSuppressed.toString())
                    StatusRow("Repeated identical text kept", extraction.repeatedTextRecords.toString())
                    StatusRow("Reading order basis", extraction.orderBasis.name)
                    StatusRow("Message order established", yesNo(extraction.orderEstablished))
                    StatusRow("Message boundaries established", yesNo(extraction.boundariesEstablished))
                    StatusRow("Sender labels reliably identified", extraction.speakerAttribution.describeSenders())
                    StatusRow("Timestamps reliably identified", yesNo(extraction.timestampsRecovered))
                    StatusRow(
                        "Visible sample appears incomplete",
                        if (extraction.isPartialVisibleSample) "Yes — treat as partial" else "No evidence of clipping",
                    )
                }
                if (extraction.reasons.isNotEmpty()) {
                    InfoCard(title = "Why this run stopped where it did") {
                        extraction.reasons.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            InfoCard(title = "Five claims, gated separately") {
                val ladder = uiState.ladder
                LadderRow("Inspection completed", ladder.inspectionCompleted)
                LadderRow("Candidates detected", ladder.candidatesDetected)
                LadderRow("Message structure reconstructed", ladder.structureReconstructed)
                LadderRow("Speaker attribution validated", ladder.speakerAttributionValidated)
                LadderRow("Complete conversation validated", ladder.completeConversationValidated)
                Text(
                    "These are not equivalent and none of them implies the next. Level 1 means a " +
                        "traversal ran. Level 2 means some nodes looked message-shaped. Level 3 means " +
                        "boundaries and order were established. Level 4 additionally needs an " +
                        "annotated ground-truth sample; level 5 additionally needs the researcher to " +
                        "attest that this screen held the whole conversation. Levels 4 and 5 are " +
                        "expected to read 'not met' on every run so far, because that evidence does " +
                        "not exist yet.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (ladder.unmetReasons.isNotEmpty()) {
                    Text(
                        "Unmet: ${ladder.unmetReasons.joinToString(" ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            InfoCard(title = "Interpretation") {
                Text(
                    "Node and text-bearing counts describe how much structure the accessibility " +
                        "tree exposed at that moment. They are NOT the number of messages, and a " +
                        "text-bearing node is not necessarily a message — it may be a button " +
                        "label, a tab title, or an icon description. A 'candidate' is the same claim " +
                        "one level up: a node that satisfied a documented structural rule, not a " +
                        "proven turn. Sender identity and timestamps are not reliably recoverable " +
                        "from this tree. No message content is displayed or stored.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        Text(
            "Clear results to reset this session's statistics and forget any retained sample.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.OutlinedButton(
            onClick = { ResearchStore.clearResults() },
            enabled = result != null || uiState.completedCount > 0,
        ) {
            Text("Clear Results")
        }
    }
}

@Composable
private fun LadderRow(claim: String, met: Boolean) {
    StatusRow(
        label = claim,
        value = if (met) "MET" else "not met",
        emphasized = met,
    )
}

/**
 * The one wording that must not be over-optimistic. A heuristic that fired is not a validated
 * attribution, and saying "Attempted" instead of "Yes" is the difference between a research
 * result and a claim the panel can strike out.
 */
private fun SpeakerAttribution.describeSenders(): String = when (this) {
    SpeakerAttribution.NOT_ATTEMPTED -> "No — not attempted"
    SpeakerAttribution.NODE_LABEL_EVIDENCE -> "Label-rule fired, NOT validated against ground truth"
    SpeakerAttribution.ALIGNMENT_HEURISTIC -> "No — alignment heuristic only, positional tokens not identities"
    SpeakerAttribution.ATTEMPTED_NOT_ESTABLISHED -> "No — attempted, nothing established"
}

private fun yesNo(b: Boolean): String = if (b) "Yes" else "No"

private fun formatTime(millis: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(millis))
