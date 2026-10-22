package com.example.groomingresearchdemo.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers spec test items 5–9 (null root, empty tree, no-text tree, several text nodes, depth). */
class StructuralInspectorTest {

    private val inspector = StructuralInspector()

    @Test
    fun nullRootYieldsNull() {
        // Item 5: a null accessibility root must be reported as null, not as a zero-node success.
        assertNull(inspector.inspect(null))
    }

    @Test
    fun singleEmptyNodeTree() {
        // Item 6: an "empty" tree still contains the root node itself.
        val stats = inspector.inspect(FakeNode())!!
        assertEquals(1, stats.nodesVisited)
        assertEquals(0, stats.textBearingNodes)
        assertEquals(1, stats.emptyOrUnavailableTextNodes)
        assertEquals(1, stats.maxDepth)
        assertEquals(false, stats.truncated)
    }

    @Test
    fun treeWithNoTextBearingNodes() {
        // Item 7: structure exists but exposes no text at all.
        val root = FakeNode()
            .addChild(FakeNode())
            .addChild(FakeNode().addChild(FakeNode()))
        val stats = inspector.inspect(root)!!
        assertEquals(4, stats.nodesVisited)
        assertEquals(0, stats.textBearingNodes)
        assertEquals(4, stats.emptyOrUnavailableTextNodes)
    }

    @Test
    fun treeWithSeveralTextBearingNodes() {
        // Item 8: contentDescription also counts as a text-bearing node.
        val grandchild = FakeNode()
        val child1 = FakeNode(textValue = "a").addChild(grandchild)
        val child2 = FakeNode(descValue = "b")
        val root = FakeNode(textValue = "root").addChild(child1).addChild(child2)

        val stats = inspector.inspect(root)!!
        assertEquals(4, stats.nodesVisited)
        assertEquals(3, stats.textBearingNodes)
        assertEquals(1, stats.emptyOrUnavailableTextNodes)
    }

    @Test
    fun whitespaceOnlyTextIsNotBearing() {
        val root = FakeNode(textValue = "   ")
        val stats = inspector.inspect(root)!!
        assertEquals(0, stats.textBearingNodes)
    }

    @Test
    fun maximumDepthIsMeasuredFromRoot() {
        // Item 9: root is depth 1, so a chain of 4 has maxDepth 4.
        val stats = inspector.inspect(FakeNode.chain(4))!!
        assertEquals(4, stats.nodesVisited)
        assertEquals(4, stats.maxDepth)
    }

    @Test
    fun traversalRespectsNodeCapAndFlagsTruncation() {
        // A pathologically large tree must not spin forever; it stops and marks truncation.
        val capped = StructuralInspector(maxNodes = 3)
        val stats = capped.inspect(FakeNode.chain(10))!!
        assertEquals(3, stats.nodesVisited)
        assertEquals(true, stats.truncated)
    }

    @Test
    fun nodeClassHistogramCountsEachClass() {
        // Compatibility question: which widget kinds does the app actually expose?
        val root = FakeNode(classValue = "android.widget.FrameLayout")
            .addChild(FakeNode(textValue = "hello", classValue = "android.widget.TextView"))
            .addChild(FakeNode(textValue = "world", classValue = "android.widget.TextView"))
            .addChild(FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView"))
        val stats = inspector.inspect(root)!!
        assertEquals(4, stats.nodesVisited)
        assertEquals(3, stats.distinctNodeClasses)
        assertEquals(2, stats.nodeClassCounts["android.widget.TextView"])
        assertEquals(1, stats.nodeClassCounts["androidx.recyclerview.widget.RecyclerView"])
    }

    @Test
    fun missingClassNameIsRecordedRatherThanDropped() {
        val stats = inspector.inspect(FakeNode(textValue = "no class"))!!
        assertEquals(1, stats.nodeClassCounts["<unknown-class>"])
        assertEquals(1, stats.distinctNodeClasses)
    }

    @Test
    fun textAndContentDescriptionAreCountedSeparately() {
        // A label on an icon is not message text; the report must be able to tell them apart.
        val root = FakeNode()
            .addChild(FakeNode(textValue = "abcd"))
            .addChild(FakeNode(descValue = "Say hello"))
            .addChild(FakeNode())
        val stats = inspector.inspect(root)!!
        assertEquals(1, stats.nodesWithText)
        assertEquals(1, stats.nodesWithContentDescriptionOnly)
        assertEquals(2, stats.textBearingNodes)
        // Only the node's own text contributes characters; contentDescription does not.
        assertEquals(4, stats.totalTextCharacters)
    }

    @Test
    fun textAvailabilityPercentageIsDerivedFromVisitedNodes() {
        val root = FakeNode()
            .addChild(FakeNode(textValue = "a"))
            .addChild(FakeNode(textValue = "b"))
            .addChild(FakeNode())
            .addChild(FakeNode())
        val stats = inspector.inspect(root)!!
        assertEquals(5, stats.nodesVisited)
        assertEquals(40, stats.textAvailabilityPercent)
    }

    @Test
    fun unreadableChildrenAreCountedAndNoted() {
        // "The app hid this subtree" is an experiment result, so it must be visible in the stats.
        val hidden = FakeNode(classValue = "com.example.PrivateView", throwOnChildren = true)
        val root = FakeNode(classValue = "android.widget.FrameLayout").addChild(hidden)
        val stats = inspector.inspect(root)!!
        assertEquals(2, stats.nodesVisited)
        assertEquals(1, stats.unreadableChildNodes)
        assertTrue(stats.notes.any { it.contains("PrivateView") })
    }

    @Test
    fun aTreeWithNoExposedTextSaysSoInItsNotes() {
        val stats = inspector.inspect(FakeNode().addChild(FakeNode()))!!
        assertEquals(0, stats.textBearingNodes)
        assertTrue(stats.notes.any { it.contains("No node in the visited tree") })
    }
}
