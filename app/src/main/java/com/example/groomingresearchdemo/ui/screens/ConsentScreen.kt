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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.inspection.ConsentStatus
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard

/**
 * Screen B — Consent and disclosure.
 *
 * Accept is the only action that enables inspection; Decline leaves inspection disabled. Once
 * accepted, a Revoke control cancels any pending inspection and clears local results (both are
 * enforced in the controller, not just in the UI).
 */
@Composable
fun ConsentScreen(uiState: ResearchUiState) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Informed consent", style = MaterialTheme.typography.titleLarge)

        InfoCard(title = "Purpose of this research experiment") {
            DisclosureBullet(
                "This is an academic demonstration for a final-year project on online grooming " +
                    "detection. It is not a monitoring or surveillance tool."
            )
            DisclosureBullet(
                "You enable an Android AccessibilityService yourself, in system settings, and can " +
                    "disable it at any time."
            )
            DisclosureBullet(
                "An inspection runs only when you explicitly arm it. It inspects the structure of " +
                    "the active window of the messaging app you selected as the compatibility " +
                    "target — node counts, depth, widget classes — to measure how much a screen " +
                    "exposes to accessibility tooling."
            )
            DisclosureBullet(
                "Android accessibility information CAN contain sensitive content. This app " +
                    "deliberately reads that content only to decide whether a node's text is empty " +
                    "and to count how many characters are exposed; it never displays, stores, logs, " +
                    "or transmits any actual message text, sender name, or account identifier."
            )
            DisclosureBullet(
                "This is a FEASIBILITY experiment. Its purpose is to find out whether a real app " +
                    "exposes usable structure. It is not a conversation extractor, and an " +
                    "inaccessible screen is a valid, expected result rather than something to work " +
                    "around."
            )
            DisclosureBullet(
                "Limitations: an accessibility tree is not the visible screen, node counts are not " +
                    "message counts, and text may be unavailable. Structural inspection is not the " +
                    "same as extracting a conversation."
            )
            DisclosureBullet(
                "Message-level preprocessing uses SYNTHETIC conversations by default. The separate " +
                    "\"Authorized text import\" screen exists only for content you are permitted to " +
                    "process for research; it is held in memory, never logged or saved, and is kept " +
                    "visually apart from anything observed in a live app."
            )
            DisclosureBullet(
                "Conversations involving other people require their appropriate consent and must " +
                    "satisfy your institution's ethics-review requirements before being used."
            )
            DisclosureBullet(
                "No network permission is declared. No data is sent to any server, cloud service, " +
                    "analytics platform, or external API. Nothing is written to a database."
            )
            DisclosureBullet(
                "To withdraw: use Revoke consent below (cancels pending inspections and clears " +
                    "results), then turn the service off in Android settings."
            )
        }

        Text(
            "Current consent: ${consentText(uiState.consentStatus)}",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )

        when (uiState.consentStatus) {
            ConsentStatus.ACCEPTED -> {
                OutlinedButton(
                    onClick = { ResearchStore.revokeConsent() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Revoke consent and clear results")
                }
            }
            else -> {
                Button(
                    onClick = { ResearchStore.acceptConsent() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = { ResearchStore.declineConsent() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Decline")
                }
            }
        }

        if (uiState.consentStatus == ConsentStatus.DECLINED) {
            Text(
                "You declined. Inspection stays disabled. You can Accept at any time to opt in.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun DisclosureBullet(text: String) {
    Text("•  $text", style = MaterialTheme.typography.bodyMedium)
}

private fun consentText(status: ConsentStatus): String = when (status) {
    ConsentStatus.NOT_GIVEN -> "not given"
    ConsentStatus.ACCEPTED -> "accepted"
    ConsentStatus.DECLINED -> "declined"
}
