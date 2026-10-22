package com.example.groomingresearchdemo.inspection

import com.example.groomingresearchdemo.inspection.extraction.Reconstruction

/**
 * Structural statistics gathered by traversing one accessibility tree.
 *
 * Every field is a COUNT, a DEPTH, a CLASS NAME, or a short note. No field can carry user
 * content: the node text is consumed inside the traversal only to decide blankness and to add
 * its length to [totalTextCharacters], then dropped.
 */
data class StructuralStats(
    val nodesVisited: Int,
    val textBearingNodes: Int,
    val emptyOrUnavailableTextNodes: Int,
    val maxDepth: Int,
    val truncated: Boolean,
    /** Nodes whose own `text` was non-blank. */
    val nodesWithText: Int = 0,
    /** Nodes that expose a label only through `contentDescription` (accessibility label, not text). */
    val nodesWithContentDescriptionOnly: Int = 0,
    /** Sum of the lengths of all exposed text. A volume measure, never the content itself. */
    val totalTextCharacters: Int = 0,
    /** How many distinct widget classes appeared anywhere in the tree. */
    val distinctNodeClasses: Int = 0,
    /** Widget class -> how many nodes had it. Bounded by [AppConstants.MAX_CLASS_HISTOGRAM_KEYS]. */
    val nodeClassCounts: Map<String, Int> = emptyMap(),
    /** Nodes whose children could not be enumerated — a real compatibility failure mode. */
    val unreadableChildNodes: Int = 0,
    /** Short, content-free observations worth surfacing to the researcher. */
    val notes: List<String> = emptyList(),
) {
    /** Percentage of visited nodes that exposed any label, or 0 for an empty traversal. */
    val textAvailabilityPercent: Int
        get() = if (nodesVisited == 0) 0 else textBearingNodes * 100 / nodesVisited
}

/**
 * The full outcome of a single inspection attempt.
 *
 * Deliberately reports each stage separately (event received, package matched, root
 * available, statistics produced) so a structural inspection is never labelled "successful"
 * merely because the service received an event.
 *
 * [extraction] holds the message-level stage's output, or null when the researcher did not opt into
 * it for this inspection. The controller returns it with records attached; it is the store's job to
 * publish a record-free copy into the session list, because that list can hold twenty results and
 * twenty retained sets of message text would be a retention surface the project's data-minimisation
 * rule forbids. See `ResearchStore.handleAccessibilityEvent`.
 */
data class InspectionResult(
    val timestampMillis: Long,
    val targetPackage: String,
    val eventReceived: Boolean,
    val packageMatched: Boolean,
    val rootAvailable: Boolean,
    val stats: StructuralStats?,
    val outcome: ExperimentOutcome,
    val failureReason: String?,
    val extraction: Reconstruction? = null,
)
