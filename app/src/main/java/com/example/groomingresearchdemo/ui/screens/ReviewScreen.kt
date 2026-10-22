package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.inspection.extraction.MessageRecord
import com.example.groomingresearchdemo.inspection.extraction.MessageStatus
import com.example.groomingresearchdemo.inspection.extraction.Reconstruction
import com.example.groomingresearchdemo.model.ExtractionHandoff
import com.example.groomingresearchdemo.model.HandoffOutcome
import com.example.groomingresearchdemo.model.ModelPrediction
import com.example.groomingresearchdemo.model.PlaceholderGroomingModelAdapter
import com.example.groomingresearchdemo.model.ValidatedRecordAdapter
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.StatusRow

/**
 * The one deliberate, temporary text-review surface.
 *
 * WHY THIS IS A SEPARATE SCREEN AND NOT A SECTION OF THE RESULTS SCREEN
 * The results screen has to be safe to leave open in front of other people, in a screenshot, and in
 * a panel demo — so it shows counts. This screen shows content, which is why reaching it takes a
 * second, named action from the experiment controls rather than a scroll, and why leaving it destroys
 * what it showed. The distinction between "the inspection produced a structural summary" and "the
 * researcher chose to read the sample" is the privacy property; putting both on one surface would
 * delete it.
 *
 * WHAT LEAVING THIS SCREEN DOES
 * `onDone`, and `DisposableEffect` on dispose, both call `ResearchStore.forgetExtractedContent()`,
 * which nulls the single retained reconstruction. Counts stay in the session reports because they
 * were never content. Nothing is written first: there is no export, no share sheet, no clipboard
 * write, and no file path in this app. Selecting the text is available because a researcher may need
 * to read it carefully; copying it is a human action outside the app, the same as with the
 * compatibility report.
 *
 * @param onDone invoked when the researcher presses the finish action. RootScreen also forgets on
 *   back-navigation, so there are two exits and neither can be mistaken for "saved".
 */
@Composable
fun ReviewScreen(onDone: () -> Unit) {
    val scroll = rememberScrollState()
    // Read once, on entry. Re-reading on every recomposition would let a background inspection
    // silently swap the content under a researcher who is mid-way through reading it.
    var reconstruction by remember { mutableStateOf(ResearchStore.peekRetainedExtraction()) }
    var handoff by remember { mutableStateOf<ExtractionHandoff?>(null) }

    DisposableEffect(Unit) {
        onDispose { ResearchStore.forgetExtractedContent() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "OBSERVED CONTENT — YOU CHOSE TO OPEN THIS. It is held in memory only, is not saved, " +
                "is not exported, and is discarded the moment you leave this screen. Nobody else " +
                "was shown this and no record of it exists after you go back.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "Use it only for a conversation you are authorised to read. Where other people are " +
                "involved, their appropriate consent and your institution's ethics-review " +
                "requirements come before this screen, not after it.",
            style = MaterialTheme.typography.bodySmall,
        )

        val current = reconstruction
        if (current == null) {
            Text(
                "Nothing to review. Content is retained only for the most recent inspection that ran " +
                    "the message-level stage, and it is dropped as soon as an inspection is " +
                    "cancelled, superseded, or this screen is left.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to home") }
            return@Column
        }

        InfoCard(title = "Reconstruction under review") {
            StatusRow("Status", current.status.name, emphasized = true)
            StatusRow("Records shown", current.records.size.toString())
            StatusRow("Accepted", current.acceptedCount.toString())
            StatusRow("Ambiguous", current.ambiguousCount.toString())
            StatusRow("Reading order", current.orderBasis.name)
            StatusRow("Speakers", current.speakerAttribution.name)
            StatusRow("Timestamps recovered", if (current.timestampsRecovered) "Yes" else "No")
            StatusRow("Partial visible sample", if (current.isPartialVisibleSample) "Yes" else "No")
        }

        InfoCard(title = "Candidate records") {
            Text(
                "Ambiguous records are rendered, marked, and counted — they are not quietly promoted " +
                    "into the transcript, and they are not quietly deleted from it either.",
                style = MaterialTheme.typography.bodySmall,
            )
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    current.records.forEach { renderRecord(it) }
                }
            }
        }

        Button(
            onClick = { handoff = ValidatedRecordAdapter.handoff(current) },
            enabled = current.records.any { it.status == MessageStatus.ACCEPTED },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Hand accepted records to preprocessing")
        }

        val result = handoff
        if (result != null) {
            InfoCard(title = "Handoff to preprocessing") {
                StatusRow("Outcome", result.outcome.name, emphasized = true)
                StatusRow("Messages converted", result.messageCount.toString())
                result.reasons.forEach { Text("•  $it", style = MaterialTheme.typography.bodySmall) }
                val validation = result.validation
                if (result.outcome == HandoffOutcome.READY && validation != null) {
                    Text(
                        "Model input produced and validated (${validation.cleanedMessageCount} " +
                            "cleaned messages). Order preserved; unknown speakers are unlabeled " +
                            "lines, not names.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Model inference: ${predictionLabel(
                            PlaceholderGroomingModelAdapter.predict(validation.modelInput),
                        )}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Text(
                        "Nothing above is a grooming judgement. A heuristic classifier finding " +
                            "suspicious-looking text is not evidence about a conversation, and no " +
                            "label is produced without a trained model and an evaluation.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        "No model input was produced. The preprocessing stage cannot be run on this " +
                            "reconstruction, which is the intended behaviour rather than a bug.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }

        OutlinedButton(
            onClick = {
                ResearchStore.forgetExtractedContent()
                reconstruction = null
                handoff = null
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Discard this content now")
        }

        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text("Finish and forget")
        }

        Text(
            "Leaving by any route — this button, Discard, or the back arrow — forgets the content. " +
                "Structural counts stay in the session reports.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * ONE text layer per record.
 *
 * Rendering the header line and the body as two separate `Text` composables made adjacent records
 * print over each other inside the selection container on a real screen — words from record N and
 * record N+1 landed on the same baseline. A record is one thing to read, so it is now one thing to
 * lay out, and the list that holds them sets its own spacing instead of relying on each child to
 * push the next one down.
 */
@Composable
private fun renderRecord(record: MessageRecord) {
    val marker = when (record.status) {
        MessageStatus.ACCEPTED -> "accepted"
        MessageStatus.CANDIDATE -> "candidate"
        MessageStatus.AMBIGUOUS -> "AMBIGUOUS"
        MessageStatus.REJECTED -> "rejected"
    }
    val position = record.visibleOrderIndex?.toString() ?: "unplaced"
    val speaker = record.speakerLabel ?: "<no speaker>"
    val timestamp = record.timestampMillis?.toString() ?: "<no timestamp>"
    Text(
        text = "[$position] ($marker, ${record.confidence.name}) $speaker | $timestamp\n" +
            (record.text ?: "<no text retained>"),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Same rendering the laboratory and import screens use, so "no model" reads identically everywhere. */
private fun predictionLabel(prediction: ModelPrediction): String = when (prediction) {
    is ModelPrediction.NotIntegrated -> prediction.message
    is ModelPrediction.Scored -> "${prediction.label} (confidence ${prediction.confidence})"
}
