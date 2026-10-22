package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.AccessibilityNodeView
import com.example.groomingresearchdemo.inspection.MessageLevelTraverser
import com.example.groomingresearchdemo.inspection.NodeBounds

/**
 * The one call the inspection controller makes to run the message-level stage.
 *
 * Exists so the controller stays free of the two-object pipeline, and so the flag pair that decides
 * how much the pipeline is *allowed* to guess is set in exactly one place — here, at construction —
 * rather than being threaded through a UI, a store, and a service.
 *
 * Both speaker rules default to off. A run that recovers no senders is a complete, honest result; a
 * run that recovers senders nobody validated is a fabricated one.
 */
open class MessageLevelExtractor(
    private val traverser: MessageLevelTraverser = MessageLevelTraverser(),
    private val enableSpeakerLabelRule: Boolean = false,
    private val enableAlignmentHeuristic: Boolean = false,
) {

    /**
     * @param root the same node the structural pass walked. No new tree is opened here: one armed
     *   inspection reads `rootInActiveWindow` once, and both stages consume that one snapshot.
     * @param viewport the visible window rectangle. When null the root's own bounds are used, which
     *   is an approximation of the window rather than a measurement of it — and when *that* is also
     *   unavailable, partial-visibility becomes unknown and is reported as unknown.
     *
     * Open so a test can substitute a stage that fails, which is the only way to prove the
     * controller's `runCatching` around it records a reason instead of losing the inspection.
     */
    open fun extract(
        root: AccessibilityNodeView?,
        viewport: NodeBounds? = null,
        inspectionLabel: String = "insp",
    ): Reconstruction {
        if (root == null) {
            return Reconstruction.unsupported(listOf("No accessibility root was available to extract from."))
        }
        val effectiveViewport = viewport ?: runCatching { root.boundsInScreen() }.getOrNull()?.takeIf { it.isValid }
        val traversal = traverser.traverse(root, effectiveViewport)
        val reconstructor = MessageReconstructor(
            enableSpeakerLabelRule = enableSpeakerLabelRule,
            enableAlignmentHeuristic = enableAlignmentHeuristic,
            inspectionLabel = inspectionLabel,
        )
        return reconstructor.reconstruct(traversal)
    }
}
