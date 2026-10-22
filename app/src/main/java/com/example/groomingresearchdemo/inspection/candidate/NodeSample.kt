package com.example.groomingresearchdemo.inspection.candidate

import com.example.groomingresearchdemo.inspection.NodeBounds

/**
 * One node, flattened into the structural metadata needed to decide whether it could be a message.
 *
 * This is the only object in the pipeline that carries a `text` value, and it is the reason the
 * class is written down here rather than buried in a function: the retention rules for that one
 * field have to be legible, because everything upstream (the AccessibilityNodeInfo) is dropped and
 * everything downstream (the report, the log, the preferences) must not see it.
 *
 * A sample is a *snapshot of a moment*. It is not a live handle, it does not keep the node alive,
 * and re-traversing the tree produces new samples rather than refreshed ones.
 */
data class NodeSample(
    /** Transient per-snapshot node id, or null when the platform would not say. Never persisted. */
    val nodeId: Long?,
    val parentNodeId: Long?,
    /** Root is depth 1, matching [com.example.groomingresearchdemo.inspection.StructuralInspector]. */
    val depth: Int,
    val className: String?,
    /**
     * The node's own text, trimmed, or null when it has none.
     *
     * Everything in this project that must be content-free — the compatibility report, the Logcat
     * line, `StructuralStats`, preferences, files — is built WITHOUT this field. It exists so a
     * message-shaped record can be produced for one authorised, armed inspection, and nothing more.
     */
    val text: String?,
    /** True when the node carried text; kept even when [text] is blank, to distinguish "empty" from "absent". */
    val hasText: Boolean,
    val hasContentDescription: Boolean,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    /** null = the platform refused to say. NOT the same as false, and never treated as such. */
    val isVisibleToUser: Boolean?,
    val bounds: NodeBounds?,
    val childCount: Int,
    /** True when a descendant carries this node's exact text: a container echoing, not a message. */
    val aggregatesDescendantText: Boolean,
) {
    val isLeaf: Boolean get() = childCount == 0
    val hasUsableBounds: Boolean get() = bounds?.isValid == true

    /**
     * NOT the generated one. This is the only pipeline object that holds a node's words, so its
     * debug rendering reports that text exists without reproducing it — otherwise any log line,
     * exception message or collection printing samples would put conversation text into a channel
     * this project has promised to keep content-free.
     */
    override fun toString(): String =
        "NodeSample(depth=$depth, class=$className, textPresent=${text != null}, " +
            "descriptionPresent=${text == null && hasContentDescription}, clickable=$isClickable, " +
            "scrollable=$isScrollable, visibleToUser=$isVisibleToUser, bounds=$bounds, " +
            "childCount=$childCount, aggregatesDescendantText=$aggregatesDescendantText)"
}

/** How far the structural evidence for one node reaches. A labelled band, NOT a probability. */
enum class EvidenceStrength {
    /**
     * Text plus a text-shaped class, and either leaf-hood or confirmed visibility — but geometry or
     * another signal is missing. Enough to keep as a candidate, not enough to call it a message.
     */
    LOW,

    /** Non-blank text, usable bounds, and no control-class or chrome-vocabulary objection. */
    MEDIUM,

    /** MEDIUM plus leaf-hood plus a positively text-bearing class plus confirmed visibility. */
    HIGH,
}

/** What the classifier decided about one node. */
enum class CandidateDecision {
    /** Looks like message text on the evidence available. */
    CANDIDATE,

    /** Looks like message text but with a hole in the evidence that cannot be closed from the tree. */
    AMBIGUOUS,

    /** Interface chrome, a container echo, an invisible node, or nothing readable at all. */
    REJECTED,
}

/**
 * The classifier's verdict plus the reasons for it.
 *
 * [evidence] entries are stable, machine-checkable tokens naming STRUCTURAL properties —
 * "text-present", "leaf-node", "control-class" — and are never derived from the node's words.
 * That makes the summary screens and the report safe to render, and it makes a reviewer's
 * disagreement with one decision checkable instead of vibes.
 */
data class CandidateJudgement(
    val decision: CandidateDecision,
    val strength: EvidenceStrength,
    val evidence: List<String>,
    /** Present only when the decision is REJECTED or AMBIGUOUS, and always a rule name, never text. */
    val objection: String? = null,
)
