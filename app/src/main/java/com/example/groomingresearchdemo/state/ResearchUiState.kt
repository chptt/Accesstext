package com.example.groomingresearchdemo.state

import com.example.groomingresearchdemo.AppConstants
import com.example.groomingresearchdemo.inspection.ArmStatus
import com.example.groomingresearchdemo.inspection.ConsentStatus
import com.example.groomingresearchdemo.inspection.InspectionResult
import com.example.groomingresearchdemo.inspection.extraction.InspectionLadder

/**
 * Everything the UI needs to render, kept as one immutable snapshot.
 *
 * Note the separate fields for consent, service enablement, arm status, and result. This is the
 * "do not rely on a single Boolean" rule made concrete — each concept is orthogonal.
 * [targetPackage] is the app chosen for the compatibility experiment and [armedTarget] is the
 * package captured by the inspection that is currently waiting, which may differ if the
 * researcher changes the selection mid-flight.
 *
 * [ladder] extends the same rule to the five extraction claims. It is a five-field value rather
 * than a level number precisely so a screen cannot paint "level 1 achieved" as "conversation
 * reconstructed". [hasRetainedContent] says whether the deliberate review action has anything to
 * show, without itself exposing any of it.
 */
data class ResearchUiState(
    val consentStatus: ConsentStatus = ConsentStatus.NOT_GIVEN,
    val serviceEnabled: Boolean = false,
    val armStatus: ArmStatus = ArmStatus.NOT_ARMED,
    val lastResult: InspectionResult? = null,
    val completedCount: Int = 0,
    val targetPackage: String = AppConstants.DEFAULT_TARGET_PACKAGE,
    val armedTarget: String? = null,
    val armedExtraction: Boolean = false,
    val reports: List<InspectionResult> = emptyList(),
    val hasRetainedContent: Boolean = false,
    val ladder: InspectionLadder = InspectionLadder.of(null),
)
