package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.model.ChatMessage
import com.example.groomingresearchdemo.model.ConversationPreprocessor
import com.example.groomingresearchdemo.model.GroomingModelAdapter
import com.example.groomingresearchdemo.model.ModelPrediction
import com.example.groomingresearchdemo.model.PlaceholderGroomingModelAdapter
import com.example.groomingresearchdemo.model.PreprocessValidation
import com.example.groomingresearchdemo.model.SampleConversation
import com.example.groomingresearchdemo.model.SampleConversations
import com.example.groomingresearchdemo.ui.InfoCard

/**
 * Screen F — Synthetic text preprocessing laboratory.
 *
 * Everything here is self-contained and uses ONLY synthetic data; it does not touch the app under
 * test, the accessibility service, or any device conversation. It exists to show that the
 * preprocessing pipeline is correct and testable independently of the accessibility experiment.
 * For content that is not synthetic, use the separate authorized-import screen.
 */
@Composable
fun PreprocessingLabScreen(
    modelAdapter: GroomingModelAdapter = PlaceholderGroomingModelAdapter,
) {
    val scroll = rememberScrollState()
    val samples = remember { SampleConversations.ALL }
    var selectedId by remember { mutableStateOf(samples.first().id) }
    var output by remember { mutableStateOf<LabOutput?>(null) }

    val selected = samples.first { it.id == selectedId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Synthetic preprocessing laboratory", style = MaterialTheme.typography.titleLarge)
        Text(
            "All conversations below are SYNTHETIC TEST DATA invented for this demonstration. " +
                "Nothing here comes from Instagram or any real conversation, and running this " +
                "laboratory proves nothing about real message extraction.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )

        InfoCard(title = "1. Select a sample scenario") {
            FlowingChips(samples, selectedId) { selectedId = it; output = null }
            Text(selected.description, style = MaterialTheme.typography.bodyMedium)
        }

        InfoCard(title = "2. Raw representation") {
            SelectionContainer {
                Text(rawOf(selected.messages), style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(
            onClick = { output = runPreprocessing(selected, modelAdapter) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Run preprocessing")
        }

        val result = output
        if (result != null) {
            InfoCard(title = "3. Cleaned messages") {
                SelectionContainer {
                    Text(rawOf(result.cleanedMessages), style = MaterialTheme.typography.bodySmall)
                }
            }
            InfoCard(title = "4. Model input (copyable)") {
                OutlinedTextField(
                    value = result.validation.modelInput,
                    onValueChange = { },
                    readOnly = true,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    if (result.validation.ok) "Validation: OK (${result.validation.cleanedMessageCount} cleaned messages)."
                    else "Validation: PROBLEMS — ${result.validation.problems.joinToString() }",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoCard(title = "5. Model inference") {
                Text(result.predictionLabel)
                Text(
                    "Preprocessing is separate from inference. This placeholder does not label " +
                        "any conversation as a grooming incident and produces no scores.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Result of running the pipeline once, kept for display. */
private class LabOutput(
    val cleanedMessages: List<ChatMessage>,
    val validation: PreprocessValidation,
    val predictionLabel: String,
)

private fun runPreprocessing(
    sample: SampleConversation,
    modelAdapter: GroomingModelAdapter,
): LabOutput {
    val cleaned = ConversationPreprocessor.preserveMessageOrder(
        ConversationPreprocessor.cleanMessages(sample.messages)
    )
    val validation = ConversationPreprocessor.validateModelInput(sample.messages)
    val prediction = modelAdapter.predict(validation.modelInput)
    val label = when (prediction) {
        is ModelPrediction.NotIntegrated -> prediction.message
        is ModelPrediction.Scored -> "${prediction.label} (confidence ${prediction.confidence})"
    }
    return LabOutput(cleaned, validation, label)
}

private fun rawOf(messages: List<ChatMessage>): String =
    messages.joinToString("\n") { m ->
        val speaker = m.speaker ?: "<no speaker>"
        val ts = m.timestampMillis?.toString() ?: "<no timestamp>"
        val text = m.text?.let { "\"$it\"" } ?: "<null>"
        "[$ts] $speaker -> $text"
    }

@Composable
private fun FlowingChips(
    samples: List<SampleConversation>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    // A simple wrapped layout without pulling in extra accompanist/compose-flow dependencies.
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        samples.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { sample ->
                    FilterChip(
                        selected = sample.id == selectedId,
                        onClick = { onSelect(sample.id) },
                        label = { Text(sample.title) },
                    )
                }
            }
        }
    }
}
