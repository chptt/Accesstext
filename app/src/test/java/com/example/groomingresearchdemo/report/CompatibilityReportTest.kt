package com.example.groomingresearchdemo.report

import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.FakeNode
import com.example.groomingresearchdemo.inspection.InspectionResult
import com.example.groomingresearchdemo.inspection.StructuralInspector
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The privacy guarantee of the compatibility report, asserted rather than promised: a report is
 * built from a tree whose text is a distinctive marker, and that marker must not appear anywhere
 * in the rendered record.
 */
class CompatibilityReportTest {

    private val marker = "PRIVATE-MESSAGE-MARKER-8f21c"

    /** Whitespace-tolerant line matcher, so the assertions do not depend on report padding. */
    private fun String.has(label: String, value: String): Boolean =
        Regex("${Regex.escape(label)}\\s*:\\s*${Regex.escape(value)}").containsMatchIn(this)

    private fun resultFor(
        root: FakeNode?,
        outcome: ExperimentOutcome = if (root != null) ExperimentOutcome.COMPLETED else ExperimentOutcome.FAILED,
    ): InspectionResult = InspectionResult(
        timestampMillis = 1_700_000_000_000L,
        targetPackage = "com.google.android.apps.messaging",
        eventReceived = true,
        packageMatched = true,
        rootAvailable = root != null,
        stats = StructuralInspector().inspect(root),
        outcome = outcome,
        failureReason = if (root == null) "Active accessibility root was unavailable at inspection time." else null,
    )

    private fun report(result: InspectionResult) = CompatibilityReportBuilder.build(
        result = result,
        consentAccepted = true,
        serviceEnabled = true,
        targetInstalled = true,
    )

    @Test
    fun nodeTextNeverAppearsInTheReport() {
        val tree = FakeNode(classValue = "android.widget.FrameLayout")
            .addChild(FakeNode(textValue = marker, classValue = "android.widget.TextView"))
            .addChild(FakeNode(descValue = marker, classValue = "android.widget.ImageView"))
        val text = report(resultFor(tree))

        assertFalse("the report leaked node text", text.contains(marker))
        // The structural facts derived from those nodes are present, which is the whole point.
        assertTrue(text.has("android.widget.TextView", "1"))
        assertTrue(text.has("Nodes exposing text", "1"))
        assertTrue(text.has("Nodes with label only (contentDescription)", "1"))
        assertTrue(text.has("Aggregate text characters exposed", marker.length.toString()))
    }

    @Test
    fun completedInspectionReportsStagesAndPercentages() {
        val tree = FakeNode(classValue = "android.widget.FrameLayout")
            .addChild(FakeNode(textValue = "hello"))
        val text = report(resultFor(tree))
        assertTrue(text.has("Outcome", "COMPLETED"))
        assertTrue(text.has("Foreground package matched", "Yes"))
        assertTrue(text.has("Active root available", "Yes"))
        assertTrue(text.has("Nodes visited", "2"))
        assertTrue(text.contains("(50% of visited)"))
        assertTrue(text.contains(CompatibilityReportBuilder.PRIVACY_FOOTER))
    }

    @Test
    fun failedInspectionIsRecordedAsAFailureNotAnEmptySuccess() {
        val text = report(resultFor(null))
        assertTrue(text.has("Outcome", "FAILED"))
        assertTrue(text.has("Active root available", "No"))
        assertTrue(text.contains("No tree was traversed"))
        assertTrue(text.contains("Active accessibility root was unavailable"))
    }

    @Test
    fun unknownInstallationStateIsPrintedAsUnknown() {
        val text = CompatibilityReportBuilder.build(
            result = resultFor(FakeNode()),
            consentAccepted = false,
            serviceEnabled = false,
            targetInstalled = null,
        )
        assertTrue(text.has("Installed on device", "Unknown"))
        assertTrue(text.has("Consent accepted", "No"))
        assertTrue(text.has("Service enabled", "No"))
    }
}
