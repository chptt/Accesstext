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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.model.AuthorizedTextParser
import com.example.groomingresearchdemo.model.ChatMessage
import com.example.groomingresearchdemo.model.ConversationPreprocessor
import com.example.groomingresearchdemo.model.GroomingModelAdapter
import com.example.groomingresearchdemo.model.ModelPrediction
import com.example.groomingresearchdemo.model.PlaceholderGroomingModelAdapter
import com.example.groomingresearchdemo.model.PreprocessValidation
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.StatusRow

/**
 * Screen I — Research-authorized text import.
 *
 * This is the ONLY place in the app that processes message text, and that text is supplied by the
 * researcher, never observed by the accessibility service. Keeping it on its own screen, with its
 * own warnings, is what makes the distinction the experiment depends on impossible to blur:
 *
 *   * live app  -> structure only (counts, depth, widget classes), on the results/report screens;
 *   * this screen -> content the researcher pasted, in memory only;
 *   * the laboratory -> fixed synthetic fixtures used by the unit tests.
 *
 * Nothing typed here is logged, persisted, or passed to the accessibility pipeline. Leaving the
 * screen drops it, and "Clear and forget" wipes the field immediately.
 */
@Composable
fun AuthorizedImportScreen(
    modelAdapter: GroomingModelAdapter = PlaceholderGroomingModelAdapter,
) {
    val scroll = rememberScrollState()
    var raw by remember { mutableStateOf("") }
    var output by remember { mutableStateOf<ImportOutput?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Research-authorized text import", style = MaterialTheme.typography.titleLarge)
        Text(
            "NOT OBSERVED FROM THE APP UNDER TEST. Everything on this screen is text you typed or " +
                "pasted here. The accessibility inspection never produces message text, so nothing " +
                "from a real conversation can arrive on this screen automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            "Use it only for content you are authorized to process: conversations involving other " +
                "people need their appropriate consent and must satisfy your institution's " +
                "ethics-review requirements first.",
            style = MaterialTheme.typography.bodySmall,
        )

        InfoCard(title = "1. Paste authorized text (one turn per line)") {
            OutlinedTextField(
                value = raw,
                onValueChange = { raw = it; output = null },
                label = { Text("Optional 'Speaker: ' prefix per line") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            StatusRow("Lines", raw.lines().count { it.isNotBlank() }.toString())
        }

        Button(
            onClick = { output = runImport(raw, modelAdapter) },
            enabled = raw.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Run preprocessing on this input")
        }

        OutlinedButton(
            onClick = { raw = ""; output = null },
            enabled = raw.isNotEmpty() || output != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Clear and forget")
        }

        val result = output
        if (result != null) {
            InfoCard(title = "2. Parsed messages (before cleaning)") {
                SelectionContainer {
                    Text(render(result.parsed), style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Timestamps show as <none> because pasted text carries no reliable time; the " +
                        "pipeline refuses to invent one and keeps the supplied order.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoCard(title = "3. Cleaned messages") {
                SelectionContainer {
                    Text(render(result.cleaned), style = MaterialTheme.typography.bodySmall)
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
                    if (result.validation.ok) {
                        "Validation: OK (${result.validation.cleanedMessageCount} cleaned messages)."
                    } else {
                        "Validation: PROBLEMS — ${result.validation.problems.joinToString()}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoCard(title = "5. Model inference") {
                Text(result.predictionLabel)
                Text(
                    "No trained grooming-detection model is connected, so no label and no score is " +
                        "produced. Preprocessing output is not a research finding by itself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private class ImportOutput(
    val parsed: List<ChatMessage>,
    val cleaned: List<ChatMessage>,
    val validation: PreprocessValidation,
    val predictionLabel: String,
)

private fun runImport(raw: String, modelAdapter: GroomingModelAdapter): ImportOutput {
    val parsed = AuthorizedTextParser.parse(raw)
    val cleaned = ConversationPreprocessor.preserveMessageOrder(
        ConversationPreprocessor.cleanMessages(parsed)
    )
    val validation = ConversationPreprocessor.validateModelInput(parsed)
    val prediction = modelAdapter.predict(validation.modelInput)
    val label = when (prediction) {
        is ModelPrediction.NotIntegrated -> prediction.message
        is ModelPrediction.Scored -> "${prediction.label} (confidence ${prediction.confidence})"
    }
    return ImportOutput(parsed, cleaned, validation, label)
}

private fun render(messages: List<ChatMessage>): String =
    if (messages.isEmpty()) {
        "(no messages survived parsing)"
    } else {
        messages.joinToString("\n") { m ->
            val speaker = m.speaker ?: "<no speaker>"
            val ts = m.timestampMillis?.toString() ?: "<none>"
            "[$ts] $speaker -> \"${m.text ?: "<null>"}\""
        }
    }
