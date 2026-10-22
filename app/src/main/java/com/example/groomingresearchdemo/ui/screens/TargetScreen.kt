package com.example.groomingresearchdemo.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
import com.example.groomingresearchdemo.AppConstants
import com.example.groomingresearchdemo.inspection.ArmStatus
import com.example.groomingresearchdemo.state.ResearchStore
import com.example.groomingresearchdemo.state.ResearchUiState
import com.example.groomingresearchdemo.ui.InfoCard
import com.example.groomingresearchdemo.ui.StatusRow

/**
 * Screen G — Real-app compatibility target.
 *
 * The experiment asks whether a real messaging app exposes usable accessibility structure, so
 * the researcher must be able to say which app is being tested. Two things are enforced here:
 * the choice is restricted to a short allowlist (or an explicit package the researcher types),
 * and the choice never enables anything by itself — an inspection still needs consent, a manually
 * enabled service, and a button press.
 */
@Composable
fun TargetScreen(uiState: ResearchUiState) {
    val scroll = rememberScrollState()
    var customPackage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Choose the app under test", style = MaterialTheme.typography.titleLarge)
        Text(
            "The researcher installs and signs in to the messaging app by hand. This screen only " +
                "records which package an armed inspection is allowed to accept. Selecting an app " +
                "here does not start anything.",
            style = MaterialTheme.typography.bodyMedium,
        )

        InfoCard(title = "Current configuration") {
            StatusRow("Selected target", uiState.targetPackage, emphasized = true)
            StatusRow(
                "Installed on this device",
                when (ResearchStore.isPackageInstalled(uiState.targetPackage)) {
                    true -> "Yes"
                    false -> "No — install it before the experiment"
                },
            )
            StatusRow(
                "Armed target (this inspection)",
                uiState.armedTarget ?: "Nothing armed",
            )
        }

        InfoCard(title = "Allowlisted messaging apps") {
            Text(
                "The accessibility service can only ever receive events from these " +
                    "${AppConstants.TARGET_OPTIONS.size} packages; " +
                    "at connect time it narrows that to the single app selected here.",
                style = MaterialTheme.typography.bodySmall,
            )
            AppConstants.TARGET_OPTIONS.forEach { option ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    FilterChip(
                        selected = option.packageName == uiState.targetPackage,
                        onClick = { ResearchStore.setTargetPackage(option.packageName) },
                        label = { Text(option.label) },
                    )
                    Text(
                        if (ResearchStore.isPackageInstalled(option.packageName)) "installed" else "not installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    option.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        InfoCard(title = "Another package (advanced)") {
            OutlinedTextField(
                value = customPackage,
                onValueChange = { customPackage = it },
                label = { Text("Package name, e.g. com.example.chat") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    ResearchStore.setTargetPackage(customPackage)
                    customPackage = ""
                },
                enabled = customPackage.isNotBlank() &&
                    uiState.armStatus == ArmStatus.NOT_ARMED,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Set as target")
            }
            Text(
                "A custom package is recorded, but events will only arrive if the config XML " +
                    "allowlist covers it — by design, this app cannot widen its own reach.",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        OutlinedButton(
            onClick = { ResearchStore.setTargetPackage(AppConstants.INSTAGRAM_PACKAGE) },
            enabled = uiState.armStatus == ArmStatus.NOT_ARMED,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset to the original Instagram target")
        }

        if (uiState.armStatus != ArmStatus.NOT_ARMED) {
            Text(
                "An inspection is already armed against ${uiState.armedTarget ?: "a target"}. " +
                    "Cancel it before changing the target — an armed inspection keeps the package " +
                    "it was armed for.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
