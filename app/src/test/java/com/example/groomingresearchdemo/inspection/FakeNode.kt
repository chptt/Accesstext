package com.example.groomingresearchdemo.inspection

/**
 * A hand-built fake accessibility node used only by unit tests. It lets us construct the
 * scenarios the spec requires — null roots, empty trees, trees with and without text-bearing
 * nodes, deep trees, hidden subtrees — without an emulator or the real AccessibilityNodeInfo class.
 *
 * EXTENDED FOR MESSAGE-LEVEL TESTS, WITHOUT BREAKING THE OLD ONES
 * Every new member defaults to "the platform gave me nothing", which is what the pre-existing
 * structural tests were written against: `bounds`, flags, parent and node id all stay absent unless a
 * test asks for them, so a 20-line test from the compatibility era still sees the same tree it was
 * written for. A geometry test sets geometry.
 *
 * NO REAL CONVERSATION APPEARS IN ANY FIXTURE. These strings are synthetic by policy: the task forbids
 * using private real conversations as unit-test inputs, and every fixture here was typed for the case
 * it exercises.
 */
class FakeNode(
    private val textValue: String? = null,
    private val descValue: String? = null,
    private val classValue: String? = null,
    /** Simulates a node whose children the platform refuses to enumerate. */
    private val throwOnChildren: Boolean = false,
    private val boundsValue: NodeBounds? = null,
    private val clickableValue: Boolean = false,
    private val scrollableValue: Boolean = false,
    /** null models "the framework would not say", which is distinct from false. */
    private val visibleValue: Boolean? = null,
    private val nodeIdValue: Long? = null,
    /** True when this node's text is really a copy of a child's — the container-echo case. */
    private val aggregatesChildTextValue: Boolean = false,
    /**
     * A function, not a value, so a fixture can hand back a DIFFERENT string on the second read and
     * so model a tree that mutates underneath the traversal.
     */
    private val mutableText: (() -> String?)? = null,
) : AccessibilityNodeView {

    private val kids = mutableListOf<FakeNode>()
    private var parentRef: FakeNode? = null

    fun addChild(child: FakeNode): FakeNode {
        kids.add(child)
        child.parentRef = this
        return this
    }

    /**
     * Attaches an already-built node to a second parent. That is the shared-node case a real tree
     * produces across interactive windows, and without it the double-visit suppression cannot be
     * tested at all. Deliberately does NOT re-parent, so [parent] keeps the first parent.
     */
    fun addSharedChild(child: FakeNode): FakeNode {
        kids.add(child)
        return this
    }

    override fun text(): CharSequence? = (mutableText?.invoke() ?: textValue)
    override fun contentDescription(): CharSequence? = descValue
    override fun className(): CharSequence? = classValue
    override fun childCount(): Int {
        if (throwOnChildren) throw IllegalStateException("children unavailable")
        return kids.size
    }
    override fun getChild(index: Int): AccessibilityNodeView? {
        if (throwOnChildren) throw IllegalStateException("children unavailable")
        return kids.getOrNull(index)
    }

    override fun boundsInScreen(): NodeBounds? = boundsValue
    override fun isClickable(): Boolean = clickableValue
    override fun isScrollable(): Boolean = scrollableValue
    override fun isVisibleToUser(): Boolean? = visibleValue
    override fun parent(): AccessibilityNodeView? = parentRef
    override fun nodeId(): Long? = nodeIdValue

    /**
     * Overridden rather than inherited because the real default implementation has to read its
     * children to find out; a fixture that wants the container-echo *outcome* should say so, and a
     * fixture that wants the *mechanism* exercises the default by giving parent and child the same
     * text. Passing `false` here still lets the inherited walk detect a genuine duplicate.
     */
    override fun aggregatesDescendantText(): Boolean =
        aggregatesChildTextValue || super<AccessibilityNodeView>.aggregatesDescendantText()

    companion object {
        /** Build a linear chain of [length] nodes; the deepest is [length] levels from the root. */
        fun chain(length: Int): FakeNode {
            require(length >= 1)
            val root = FakeNode()
            var cur = root
            repeat(length - 1) {
                val next = FakeNode()
                cur.addChild(next)
                cur = next
            }
            return root
        }

        /** A screen-sized viewport for the ordering and clipping cases. */
        fun viewport(width: Int = 1080, height: Int = 2400): NodeBounds =
            NodeBounds(0, 0, width, height)

        /**
         * A synthetic message row: one leaf text node in a vertical band [top]..[bottom].
         * Deliberately awkward English so a fixture string can never be mistaken for a real
         * conversation, and deliberately distinctive so leakage assertions have something to catch.
         */
        fun messageRow(
            text: String,
            top: Int,
            height: Int = 60,
            left: Int = 80,
            right: Int = 900,
            className: String = "android.widget.TextView",
            nodeId: Long? = null,
            visible: Boolean? = true,
        ): FakeNode = FakeNode(
            textValue = text,
            classValue = className,
            boundsValue = NodeBounds(left, top, right, top + height),
            visibleValue = visible,
            nodeIdValue = nodeId,
        )
    }
}
