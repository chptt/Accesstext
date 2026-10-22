package com.example.groomingresearchdemo.inspection

/**
 * A screen-rectangle copied OUT of an accessibility node, in pixels relative to the screen origin.
 *
 * This exists so the rest of the extraction pipeline can talk about geometry without ever holding
 * an [android.graphics.Rect] — and, more importantly, without holding the node itself. The values
 * are positions, not content: a rectangle cannot leak a message.
 *
 * Deliberately immutable and deliberately untyped-by-unit. The framework gives back whatever
 * coordinate space the display is in, so comparisons here are always *relative to other nodes in
 * the same traversal*, never to a hard-coded screen size.
 */
data class NodeBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    /** False for degenerate rectangles the framework returns before a view has been laid out. */
    val isValid: Boolean get() = right > left && bottom > top

    val height: Int get() = bottom - top
    val width: Int get() = right - left

    /** Vertical midpoint — the stable anchor for "which row is this" when bounds are partial. */
    val centerY: Int get() = top + height / 2

    /** Horizontal midpoint, used only by the opt-in alignment heuristic, never by default. */
    val centerX: Int get() = left + width / 2

    /**
     * True when the rectangle hangs off [viewport] on any side, i.e. the node is only partly on
     * screen. This is how a partially visible sample is detected instead of being silently
     * presented as a whole conversation.
     */
    fun isClippedBy(viewport: NodeBounds): Boolean {
        if (!isValid || !viewport.isValid) return false
        return left < viewport.left || top < viewport.top ||
            right > viewport.right || bottom > viewport.bottom
    }

    companion object {
        /**
         * Vertical gap between two boxes. Negative when they overlap, which is the normal case for
         * a label sitting inside the same message row as its body.
         */
        fun verticalGap(upper: NodeBounds, lower: NodeBounds): Int = lower.top - upper.bottom
    }
}
