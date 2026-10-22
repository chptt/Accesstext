package com.example.groomingresearchdemo.inspection

/**
 * A minimal, Android-free view of an accessibility node.
 *
 * The real implementation wraps [android.view.accessibility.AccessibilityNodeInfo] (see
 * service/RealAccessibilityNodeView.kt), but keeping the traversal behind this interface lets
 * the structural inspector be unit-tested with hand-built fake trees — including null roots,
 * empty trees, and trees with/without text-bearing nodes — without an emulator.
 *
 * SHAPE OF THE INTERFACE, AND WHY IT GREW
 * The original five members were enough to answer "how much of this screen is exposed?" because
 * that question is answered with counts. Message-level work asks a different question — "which
 * of these nodes sits where, relative to which?" — and geometry, containment and interaction
 * flags are the only structural signals that can answer it. Those members were therefore added.
 *
 * They are added with DEFAULTS, not as abstract requirements, on purpose:
 *  - the structural path stays exactly as it was, so the compatibility numbers R1-R8 remain
 *    comparable with anything measured after this change;
 *  - a node whose property the platform refuses reports "unavailable" rather than throwing,
 *    which is the defensive behaviour the extraction stage is required to have;
 *  - a fake tree in a test only overrides what that test is about.
 *
 * WHAT IS DELIBERATELY NOT HERE: `viewIdResourceName` and any package/install metadata. Those are
 * the levers that would turn a research prototype into an app-specific scraper, and the framework
 * offers enough structural signal that they are not needed. `nodeId()` below is NOT one of them: it
 * is the platform's transient per-tree instance id, used solely to stop visiting the same node
 * twice, and it is never persisted, logged or reported.
 */
interface AccessibilityNodeView {
    fun text(): CharSequence?
    fun contentDescription(): CharSequence?

    /**
     * The widget class name, e.g. `android.widget.TextView`. This is structural metadata about
     * the screen, not user content, and it is what makes a compatibility report meaningful:
     * it tells the researcher which kinds of view the target app actually exposes.
     */
    fun className(): CharSequence?

    fun childCount(): Int
    fun getChild(index: Int): AccessibilityNodeView?

    /**
     * Screen-relative rectangle, or null when the platform does not supply a usable one. Roughly
     * a third of the "is this a message row, and in what order?" decision rests on this value;
     * when it is null the pipeline must say the order is unestablished instead of guessing.
     */
    fun boundsInScreen(): NodeBounds? = null

    /** Control-like node. Used to keep buttons out of the candidate pool, nothing more. */
    fun isClickable(): Boolean = false

    /** Scroll container. The root of a chat list is usually scrollable; its rows are not. */
    fun isScrollable(): Boolean = false

    /**
     * Whether the framework says the node is currently visible to the user. Null means the
     * property is unavailable (pre-API-19 behaviour, or a refused read) — which is NOT the same
     * as "invisible", and the classifier must keep those two states apart.
     */
    fun isVisibleToUser(): Boolean? = null

    /**
     * Direct parent, or null at the root or when the framework will not give one. Needed for the
     * parent-aggregate case: a container whose text is simply its children's text concatenated.
     */
    fun parent(): AccessibilityNodeView? = null

    /**
     * Transient identity of this node within the current tree snapshot, used only for
     * double-visit suppression. Null when unavailable, in which case the traversal falls back to
     * object identity.
     */
    fun nodeId(): Long? = null

    /**
     * True when this node reports the same text as one of its own descendants — the signature of a
     * container that aggregates rather than a message that speaks. Default implementation walks up
     * to a bounded depth so test fakes inherit it for free.
     */
    fun aggregatesDescendantText(): Boolean {
        val own = text()?.toString()?.trim() ?: return false
        if (own.isEmpty()) return false
        var matchedChild = false
        val direct = directChildren()
        for (child in direct) {
            val childText = runCatching { child.text()?.toString()?.trim() }.getOrNull()
            if (childText == own) { matchedChild = true; break }
            if (runCatching { child.contentDescription()?.toString()?.trim() }.getOrNull() == own) {
                matchedChild = true
                break
            }
        }
        return matchedChild
    }

    private fun directChildren(): List<AccessibilityNodeView> {
        val count = runCatching { childCount() }.getOrDefault(0)
        if (count <= 0) return emptyList()
        return (0 until minOf(count, MAX_INSPECTED_CHILDREN)).mapNotNull { index ->
            runCatching { getChild(index) }.getOrNull()
        }
    }

    companion object {
        /**
         * Ceiling on how many children of one node are examined for the aggregation test. A chat
         * row has a handful; a screen-wide container with hundreds of children is not a message and
         * spending the whole traversal proving that would be the wrong trade.
         */
        const val MAX_INSPECTED_CHILDREN = 64
    }
}
