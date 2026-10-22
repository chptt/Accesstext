package com.example.groomingresearchdemo.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extraction traversal, tested against the tree pathologies the design has to survive: a node
 * reached twice, a subtree that vanishes mid-walk, a tree whose text changes between reads, a node
 * cap, and a missing viewport.
 *
 * What these have in common is that the traversal must DEGRADE LOUDLY. Silently returning a shorter
 * sample is how a partial screen ends up reported as a whole conversation.
 */
class MessageLevelTraverserTest {

    private val traverser = MessageLevelTraverser()

    @Test
    fun nullRootYieldsNoSamplesAndSaysWhy() {
        val result = traverser.traverse(null)
        assertEquals(0, result.nodesVisited)
        assertTrue(result.samples.isEmpty())
        assertEquals(listOf("root unavailable"), result.notes)
    }

    @Test
    fun everyNodeOfATreeIsSampledOnceWithItsDepth() {
        val root = FakeNode(classValue = "android.widget.FrameLayout")
            .addChild(FakeNode(textValue = "first synthetic line", classValue = "android.widget.TextView"))
            .addChild(
                FakeNode(classValue = "android.widget.LinearLayout")
                    .addChild(FakeNode(textValue = "nested synthetic line", classValue = "android.widget.TextView")),
            )

        val result = traverser.traverse(root, FakeNode.viewport())

        assertEquals(4, result.nodesVisited)
        assertEquals(listOf(1, 2, 2, 3), result.samples.map { it.depth })
        assertFalse(result.truncated)
        assertFalse(result.mutatedDuringInspection)
    }

    @Test
    fun aNodeReachedTwiceThroughTheSameParentIsSampledOnceByIdentity() {
        val shared = FakeNode(textValue = "shared synthetic line")
        val root = FakeNode()
            .addChild(shared)
            .addSharedChild(shared)

        val result = traverser.traverse(root, FakeNode.viewport())

        assertEquals(1, result.samples.count { it.text == "shared synthetic line" })
        assertEquals(1, result.duplicatesSuppressed)
    }

    @Test
    fun twoWrapperObjectsCarryingTheSamePlatformNodeIdCollapseToOneSample() {
        val root = FakeNode()
            .addChild(FakeNode(textValue = "wrapper A", nodeIdValue = 44L))
            .addChild(FakeNode(textValue = "wrapper B", nodeIdValue = 44L))

        val result = traverser.traverse(root, FakeNode.viewport())

        // Distinct objects, so object identity cannot catch this; the transient node id does.
        assertEquals(1, result.duplicatesSuppressed)
        assertEquals(listOf("wrapper A"), result.samples.mapNotNull { it.text })
    }

    @Test
    fun aNodeThatRefusesToEnumerateIsCountedAsALostSubtree() {
        val root = FakeNode()
            .addChild(FakeNode(textValue = "fine", classValue = "android.widget.TextView"))
            .addChild(FakeNode(throwOnChildren = true))

        val result = traverser.traverse(root, FakeNode.viewport())

        assertEquals(1, result.nodesLostDuringTraversal)
        assertTrue(result.mutatedDuringInspection)
        assertTrue(result.notes.any { it.contains("the tree changed during inspection") })
    }

    @Test
    fun aNodeThatClaimsMoreChildrenThanItReturnsIsDetected() {
        val root = ThreeClaimsTwoHolds(
            listOf(FakeNode(textValue = "held one"), FakeNode(textValue = "held two")),
        )

        val result = traverser.traverse(root, FakeNode.viewport())

        assertEquals(3, result.nodesVisited)
        assertEquals(1, result.nodesLostDuringTraversal)
        assertTrue(result.mutatedDuringInspection)
    }

    @Test
    fun textAndItsPresenceFlagComeFromTheSameRead() {
        var reads = 0
        val root = FakeNode(
            classValue = "android.widget.TextView",
            mutableText = {
                reads++
                // Alternates between a value and nothing, the way a redrawing node does.
                if (reads % 2 == 1) "reading one" else null
            },
        )

        val sample = traverser.traverse(root, FakeNode.viewport()).samples.single()

        assertEquals(sample.text != null, sample.hasText)
        assertTrue("the traversal never read the node's text", reads >= 1)
    }

    @Test
    fun theNodeCapTruncatesAndRecordsIt() {
        val root = FakeNode()
        repeat(12) { root.addChild(FakeNode(textValue = "synthetic line $it")) }

        val result = MessageLevelTraverser(maxNodes = 5).traverse(root, FakeNode.viewport())

        assertTrue(result.truncated)
        assertEquals(5, result.nodesVisited)
        assertTrue(result.notes.any { it.contains("node cap") })
    }

    @Test
    fun anUnavailableViewportBecomesANoteRatherThanAClaim() {
        val result = traverser.traverse(FakeNode(textValue = "no viewport here"))

        assertNull(result.viewport)
        assertTrue(result.notes.any { it.contains("partial-visibility could not be assessed") })
    }

    @Test
    fun geometryAndInteractionFlagsAreCarriedIntoTheSample() {
        val row = FakeNode(
            textValue = "flagged synthetic line",
            classValue = "android.widget.TextView",
            boundsValue = NodeBounds(10, 20, 300, 80),
            clickableValue = true,
            scrollableValue = true,
            visibleValue = false,
        )
        val result = traverser.traverse(FakeNode().addChild(row), FakeNode.viewport())

        val sample = result.samples.last()
        assertEquals(NodeBounds(10, 20, 300, 80), sample.bounds)
        assertTrue(sample.isClickable)
        assertTrue(sample.isScrollable)
        assertEquals(false, sample.isVisibleToUser)
        assertEquals(0, sample.childCount)
        assertTrue(sample.isLeaf)
    }

    @Test
    fun aRefusedPropertyCostsThatPropertyNotTheNode() {
        val result = traverser.traverse(NoGeometryNoClass(), FakeNode.viewport())
        val sample = result.samples.single()

        assertEquals(1, result.nodesVisited)
        assertNull(sample.bounds)
        assertNull(sample.className)
        assertNull(sample.parentNodeId)
        assertEquals("text still survives", "geometry-less synthetic line", sample.text)
        assertTrue(sample.hasText)
    }

    /** A node that reports three children and hands back two — the mid-traversal-shrink shape. */
    private class ThreeClaimsTwoHolds(
        private val kids: List<AccessibilityNodeView>,
    ) : AccessibilityNodeView {
        override fun text(): CharSequence? = "container"
        override fun contentDescription(): CharSequence? = null
        override fun className(): CharSequence? = "android.widget.ListView"
        override fun childCount(): Int = kids.size + 1
        override fun getChild(index: Int): AccessibilityNodeView? = kids.getOrNull(index)
    }

    /** A node whose geometry, class, parent and visibility reads all throw — the refused-property shape. */
    private class NoGeometryNoClass : AccessibilityNodeView {
        override fun text(): CharSequence = "geometry-less synthetic line"
        override fun contentDescription(): CharSequence? = null
        override fun className(): CharSequence? = throw IllegalStateException("class unavailable")
        override fun childCount(): Int = 0
        override fun getChild(index: Int): AccessibilityNodeView? = null
        override fun boundsInScreen(): NodeBounds? = throw IllegalStateException("bounds unavailable")
        override fun parent(): AccessibilityNodeView? = throw IllegalStateException("parent unavailable")
        override fun isVisibleToUser(): Boolean? = throw IllegalStateException("visibility unavailable")
    }
}
