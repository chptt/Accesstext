package com.example.groomingresearchdemo.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.StatusRow

/**
 * Screen C — Accessibility service setup.
 *
 * The app never enables the service programmatically (Android forbids it for AccessibilityService
 * anyway). It opens the accessibility settings page, explains the manual steps, and reports the
 * status it can actually verify.
 */
@Composable
fun ServiceSetupScreen(uiState: ResearchUiState) {
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Enable the accessibility service manually", style = MaterialTheme.typography.titleLarge)

        InfoCard(title = "Steps") {
            Text("1. Tap the button below to open Android accessibility settings.")
            Text("2. Find \"Grooming Research Inspection (structural only)\" under Downloadable services.")
            Text("3. Toggle it on and confirm the system warning. Android reads the app's disclosure text here.")
            Text("4. Return to this app and tap \"Re-check status\". The status below reflects what the app can verify.")
        }

        InfoCard(title = "Verified service status") {
            StatusRow(
                "Status",
                if (uiState.serviceEnabled) "Enabled (verified)" else "Not enabled / unverified",
                emphasized = uiState.serviceEnabled,
            )
            Text(
                "The app only reports \"Enabled\" after it confirms the service is active. It never " +
                    "assumes enablement.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Button(
            onClick = { openAccessibilitySettings(context) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Open accessibility settings")
        }
        OutlinedButton(
            onClick = { ResearchStore.refreshServiceStatus() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Re-check status")
        }

        Text(
            "If the settings page does not open on your Android version, go to Settings > " +
                "Accessibility > Installed apps / Downloaded services and enable the service there.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun openAccessibilitySettings(context: android.content.Context) {
    try {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (e: ActivityNotFoundException) {
        // Graceful fallback: the manual instructions above remain visible; we do not crash.
        com.example.groomingresearchdemo.service.LogUtils.w("Accessibility settings not launchable.", e)
    }
}
