package com.example.groomingresearchdemo.report

import com.example.groomingresearchdemo.inspection.InspectionResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders one [InspectionResult] as a copyable, purely structural compatibility record.
 *
 * The point of this experiment is to answer "does a real messaging app expose usable
 * accessibility structure?" without collecting the conversations themselves, so the report is
 * built exclusively from counts, depths, widget class names and notes. There is no code path
 * from a node's text into this string — the inspector only ever adds a node's text LENGTH to an
 * aggregate. `CompatibilityReportTest` asserts that property with a tree whose text is a
 * distinctive marker.
 */
object CompatibilityReportBuilder {

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** Number of widget classes listed in the histogram section. */
    private const val TOP_CLASSES = 10

    fun build(
        result: InspectionResult,
        consentAccepted: Boolean,
        serviceEnabled: Boolean,
        targetInstalled: Boolean?,
    ): String {
        val stats = result.stats
        return buildString {
            appendLine("REAL-APP ACCESSIBILITY COMPATIBILITY REPORT")
            appendLine("Generated: ${timeFormat.format(Date(result.timestampMillis))}")
            appendLine()
            appendLine("Test app package : ${result.targetPackage}")
            appendLine("Installed on device: ${describe(targetInstalled)}")
            appendLine("Consent accepted : ${describe(consentAccepted)}")
            appendLine("Service enabled  : ${describe(serviceEnabled)}")
            appendLine()
            appendLine("Stage results")
            appendLine("  Accessibility event received : ${describe(result.eventReceived)}")
            appendLine("  Foreground package matched   : ${describe(result.packageMatched)}")
            appendLine("  Active root available        : ${describe(result.rootAvailable)}")
            appendLine("  Outcome                      : ${result.outcome.name}")
            result.failureReason?.let { appendLine("  Failure reason                 : $it") }
            appendLine()
            if (stats == null) {
                appendLine("Structure")
                appendLine("  No tree was traversed, so no structural observation exists for this attempt.")
            } else {
                appendLine("Structure (counts only)")
                appendLine("  Nodes visited                     : ${stats.nodesVisited}")
                appendLine("  Nodes exposing text               : ${stats.nodesWithText}")
                appendLine("  Nodes with label only (contentDescription) : ${stats.nodesWithContentDescriptionOnly}")
                appendLine("  Text-bearing nodes (either)       : ${stats.textBearingNodes} (${stats.textAvailabilityPercent}% of visited)")
                appendLine("  Nodes with empty / unavailable text : ${stats.emptyOrUnavailableTextNodes}")
                appendLine("  Aggregate text characters exposed : ${stats.totalTextCharacters}")
                appendLine("  Maximum hierarchy depth           : ${stats.maxDepth}")
                appendLine("  Traversal truncated at node cap   : ${describe(stats.truncated)}")
                appendLine("  Distinct widget classes           : ${stats.distinctNodeClasses}")
                appendLine("  Nodes whose children were unreadable : ${stats.unreadableChildNodes}")
                appendLine()
                appendLine("Widget classes seen (top $TOP_CLASSES by node count)")
                if (stats.nodeClassCounts.isEmpty()) {
                    appendLine("  (none recorded)")
                } else {
                    stats.nodeClassCounts.entries
                        .sortedByDescending { it.value }
                        .take(TOP_CLASSES)
                        .forEach { (className, count) -> appendLine("  $className : $count") }
                }
                if (stats.notes.isNotEmpty()) {
                    appendLine()
                    appendLine("Structural notes")
                    stats.notes.forEach { appendLine("  - $it") }
                }
            }
            appendLine()
            append(PRIVACY_FOOTER)
        }
    }

    /**
     * The interpretation the panel needs alongside every number: structure was measured,
     * content was not.
     */
    const val PRIVACY_FOOTER =
        "Privacy record: this report contains structural observations only. No message text, " +
            "sender name, contact name, account identifier or conversation content was read out " +
            "of the accessibility tree, displayed, logged, stored, or transmitted. A " +
            "text-bearing node is not necessarily a chat message, and these counts are not an " +
            "extraction accuracy measure."

    private fun describe(value: Boolean): String = if (value) "Yes" else "No"

    private fun describe(value: Boolean?): String = when (value) {
        true -> "Yes"
        false -> "No"
        null -> "Unknown"
    }
}
