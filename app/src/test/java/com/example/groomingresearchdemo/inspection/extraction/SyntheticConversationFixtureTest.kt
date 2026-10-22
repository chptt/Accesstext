package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.ArmResult
import com.example.groomingresearchdemo.inspection.ArmStatus
import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.FakeNode
import com.example.groomingresearchdemo.inspection.InspectionController
import com.example.groomingresearchdemo.inspection.MessageLevelTraverser
import com.example.groomingresearchdemo.inspection.NodeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The fourteen synthetic accessibility trees the task requires, and the outcome each must produce.
 *
 * EVERY STRING IN THIS FILE IS INVENTED FOR THE CASE IT EXERCISES. No private conversation is a
 * unit-test fixture, in this file or any other. The turns are named alpha/beta/gamma/delta so a
 * reviewer can see at a glance that they are synthetic, and so a leakage assertion has a marker that
 * could not plausibly occur in anyone's chat history.
 *
 * These run the whole pipeline — real traverser, real classifier, real reconstructor — against
 * trees, because the behaviour the task specifies lives in the seams between those stages.
 */
class SyntheticConversationFixtureTest {

    private val extractor = MessageLevelExtractor()
    private val viewport get() = FakeNode.viewport()

    /** The reconstructed conversation, as a reader would see it. */
    private fun Reconstruction.turns(): List<MessageRecord> =
        records.filter { it.isUsable }.sortedBy { it.visibleOrderIndex }

    /** A chat list: one scroll container holding rows, each row holding one text bubble. */
    private fun chatScreen(vararg texts: Pair<String, Int>): FakeNode {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = viewport)
        texts.forEach { (text, top) ->
            val row = FakeNode(classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(0, top, 1080, top + 90))
            row.addChild(FakeNode.messageRow(text, top = top + 10))
            list.addChild(row)
        }
        return FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(list)
    }

    // F1 — a simple visible conversation with two speakers.
    @Test
    fun f1_twoSpeakerConversationBecomesAnOrderedRecordSetWithNoSendersAttached() {
        val result = extractor.extract(
            chatScreen(
                "alpha synthetic turn" to 100,
                "beta synthetic turn" to 200,
                "gamma synthetic turn" to 300,
                "delta synthetic turn" to 400,
            ),
            viewport,
            "f1",
        )

        assertEquals(ReconstructionStatus.RECONSTRUCTED, result.status)
        assertEquals(4, result.acceptedCount)
        assertEquals(
            listOf("alpha synthetic turn", "beta synthetic turn", "gamma synthetic turn", "delta synthetic turn"),
            result.turns().map { it.text },
        )
        assertEquals(listOf(1, 2, 3, 4), result.turns().map { it.visibleOrderIndex })
        // Two people typed these, and the tree never once said who.
        assertEquals(SpeakerAttribution.NOT_ATTEMPTED, result.speakerAttribution)
        assertTrue(result.records.all { it.speakerLabel == null })
        assertTrue(result.boundariesEstablished)
    }

    // F2 — the same conversation with speaker labels missing from the tree entirely.
    @Test
    fun f2_missingSpeakerLabelsProduceAReconstructionThatSaysItHasNoSenders() {
        val result = extractor.extract(
            chatScreen("alpha synthetic turn" to 100, "beta synthetic turn" to 200),
            viewport,
            "f2",
        )

        assertEquals(ReconstructionStatus.RECONSTRUCTED, result.status)
        assertEquals(SpeakerAttribution.NOT_ATTEMPTED, result.speakerAttribution)
        assertFalse(result.records.any { it.speakerLabel != null })
    }

    // F3 — no timestamps anywhere.
    @Test
    fun f3_absentTimestampsStayAbsentRatherThanBecomingReconstructionOrder() {
        val result = extractor.extract(
            chatScreen("alpha synthetic turn" to 100, "beta synthetic turn" to 200),
            viewport,
            "f3",
        )

        assertFalse(result.timestampsRecovered)
        assertTrue(result.records.all { it.timestampMillis == null })
    }

    // F4 — nested containers echoing the same text as their child.
    @Test
    fun f4_nestedContainersEchoingOneBubbleYieldOneRecordNotTwo() {
        val first = "one synthetic turn echoed upward"
        val second = "a second synthetic turn echoed upward"
        val wrapperA = FakeNode(textValue = first, classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(70, 95, 910, 170), nodeIdValue = 10)
        wrapperA.addChild(FakeNode.messageRow(first, top = 100, nodeId = 11))
        val wrapperB = FakeNode(textValue = second, classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(70, 195, 910, 270), nodeIdValue = 20)
        wrapperB.addChild(FakeNode.messageRow(second, top = 200, nodeId = 21))
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport, nodeIdValue = 1)
            .addChild(wrapperA)
            .addChild(wrapperB)

        val result = extractor.extract(root, viewport, "f4")

        // Both wrappers report their only child's text: the aggregation signature, suppressed twice.
        assertEquals(2, result.containerEchoesSuppressed)
        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.turns().count { it.text == first })
        assertEquals(listOf(first, second), result.turns().map { it.text })
    }

    // F5 — a screen that is nothing but interface labels.
    @Test
    fun f5_aScreenOfChromeIsUnsupportedRatherThanAnEmptyConversation() {
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport)
            .addChild(
                FakeNode(classValue = "android.widget.Toolbar", boundsValue = NodeBounds(0, 0, 1080, 140))
                    .addChild(FakeNode(textValue = "Search", classValue = "android.widget.TextView", boundsValue = NodeBounds(200, 40, 800, 100))),
            )
            .addChild(FakeNode(textValue = "More options", classValue = "android.widget.TextView", boundsValue = NodeBounds(900, 40, 1060, 100)))
            .addChild(FakeNode(textValue = "Type a message", classValue = "android.widget.EditText", boundsValue = NodeBounds(80, 2300, 900, 2380)))
            .addChild(FakeNode(textValue = "Send", classValue = "android.widget.Button", boundsValue = NodeBounds(940, 2300, 1060, 2380)))

        val result = extractor.extract(root, viewport, "f5")

        assertEquals(ReconstructionStatus.UNSUPPORTED, result.status)
        assertTrue(result.records.isEmpty())
        assertEquals(0, result.acceptedCount)
        assertTrue(result.rejectedCount > 0)
        // The counts still describe the screen; that is what makes an unsupported result a result.
        assertTrue(result.nodesSampled > 0)
    }

    // F6 — multi-line messages.
    @Test
    fun f6_aMultiLineBubbleIsOneRecordAndKeepsItsLineBreaks() {
        val multiLine = "first synthetic line\nsecond synthetic line\nthird synthetic line"
        val result = extractor.extract(
            chatScreen(multiLine to 100, "following synthetic turn" to 300),
            viewport,
            "f6",
        )

        assertEquals(2, result.acceptedCount)
        val bubble = result.turns().first { it.text == multiLine }
        assertEquals(3, bubble.text?.lines()?.size)
        assertTrue(result.boundariesEstablished)
    }

    // F7 — the same text sent twice.
    @Test
    fun f7_repeatedIdenticalTextStaysTwoRecords() {
        val result = extractor.extract(
            chatScreen("same synthetic turn" to 100, "same synthetic turn" to 200),
            viewport,
            "f7",
        )

        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.repeatedTextRecords)
        assertEquals(listOf(1, 2), result.turns().map { it.visibleOrderIndex })
    }

    // F8 — nodes enumerated in an order that is NOT the reading order.
    @Test
    fun f8_backwardsEnumerationIsReorderedByGeometryNotTrustedAsTraversalOrder() {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = viewport)
        // Added bottom-most first, so traversal order and visual order disagree on purpose.
        list.addChild(FakeNode.messageRow("gamma synthetic turn", top = 310, nodeId = 3))
        list.addChild(FakeNode.messageRow("alpha synthetic turn", top = 110, nodeId = 1))
        list.addChild(FakeNode.messageRow("beta synthetic turn", top = 210, nodeId = 2))
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(list)

        val result = extractor.extract(root, viewport, "f8")

        assertEquals(
            listOf("alpha synthetic turn", "beta synthetic turn", "gamma synthetic turn"),
            result.turns().map { it.text },
        )
        assertEquals(OrderBasis.GEOMETRY, result.orderBasis)
        assertEquals(listOf(1, 2, 3), result.turns().map { it.visibleOrderIndex })
    }

    // F9 — empty, null and whitespace-only text alongside real turns.
    @Test
    fun f9_emptyNullAndBlankTextAreRejectedWithoutEndingTheRun() {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = viewport)
            .addChild(FakeNode.messageRow("alpha synthetic turn", top = 100))
            .addChild(FakeNode.messageRow("   ", top = 200))
            .addChild(FakeNode.messageRow("z", top = 300))
            .addChild(FakeNode.messageRow("beta synthetic turn", top = 400))
            .addChild(FakeNode(classValue = "android.widget.TextView", boundsValue = NodeBounds(80, 500, 900, 560)))
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(list)

        val result = extractor.extract(root, viewport, "f9")

        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.ambiguousCount) // the one-character node: kept visible, not deleted
        assertTrue(result.rejectedCount >= 2) // the blank node and the null-text node
    }

    // F10 — a tree that changes underneath the traversal.
    @Test
    fun f10_aTreeThatMutatesMidTraversalIsReportedAsUnreliable() {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = viewport)
            .addChild(FakeNode.messageRow("alpha synthetic turn", top = 100))
            .addChild(FakeNode.messageRow("beta synthetic turn", top = 200))
            .addChild(FakeNode(throwOnChildren = true)) // the third row's subtree disappears
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(list)

        val traversal = MessageLevelTraverser().traverse(root, viewport)
        val result = extractor.extract(root, viewport, "f10")

        assertTrue(traversal.mutatedDuringInspection)
        assertTrue(result.isPartialVisibleSample)
        assertTrue(result.reasons.any { it.contains("changed during inspection") })
    }

    // F11 — a conversation taller than the viewport.
    @Test
    fun f11_aConversationRunningOffScreenIsLabelledPartial() {
        val result = extractor.extract(
            chatScreen(
                "alpha synthetic turn" to 100,
                "beta synthetic turn" to 200,
                "gamma synthetic turn" to 2350, // its bubble runs past the 2400 viewport
            ),
            viewport,
            "f11",
        )

        assertTrue("an off-screen row did not mark the sample partial", result.isPartialVisibleSample)
        assertTrue(result.reasons.any { it.contains("clipped by the viewport") })
    }

    // F12 — text nodes whose row boundaries cannot be established.
    @Test
    fun f12_ambiguousBoundariesAreKeptAndBlockAReconstructedVerdict() {
        // One row, three text nodes in the SAME vertical band: a label, a body and a stamp. This is
        // the shape R5 showed on a live inbox, and the pipeline's answer must be "unsure".
        val row = FakeNode(classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(0, 100, 1080, 190))
        row.addChild(FakeNode(textValue = "sender synthetic label", classValue = "android.widget.TextView", boundsValue = NodeBounds(80, 100, 400, 160)))
        row.addChild(FakeNode(textValue = "body synthetic turn", classValue = "android.widget.TextView", boundsValue = NodeBounds(420, 100, 900, 160)))
        row.addChild(FakeNode(textValue = "10:45 synthetic stamp", classValue = "android.widget.TextView", boundsValue = NodeBounds(920, 100, 1060, 160)))

        val result = extractor.extract(row, viewport, "f12")

        assertEquals(3, result.ambiguousCount)
        assertEquals(0, result.acceptedCount)
        assertFalse(result.boundariesEstablished)
        assertEquals(ReconstructionStatus.INCOMPLETE, result.status)
        assertTrue(result.records.all { it.boundaryUncertain })
        assertTrue(result.reasons.any { it.contains("Boundaries could not be established") })
    }

    // F13 — an unsupported app: an empty tree, and no tree at all.
    @Test
    fun f13_anEmptyTreeAndANullRootAreBothUnsupportedNotSilentlyEmpty() {
        val empty = extractor.extract(FakeNode(), viewport, "f13a")
        val nullRoot = extractor.extract(null, viewport, "f13b")

        assertEquals(ReconstructionStatus.UNSUPPORTED, empty.status)
        assertEquals(ReconstructionStatus.UNSUPPORTED, nullRoot.status)
        assertTrue(nullRoot.reasons.any { it.contains("No accessibility root") })
        assertEquals(0, empty.acceptedCount)
    }

    // F14 — cancellation during inspection.
    @Test
    fun f14_cancellingTheArmMeansTheExtractionStageNeverRuns() {
        val controller = InspectionController(clock = { 1_700_000_000_000L })
        val screen = chatScreen("alpha synthetic turn" to 100)

        assertEquals(
            ArmResult.ARMED,
            controller.arm(
                consentAccepted = true,
                serviceEnabled = true,
                targetPackage = "com.example.target",
                messageLevelExtraction = true,
            ),
        )
        assertTrue(controller.armedExtraction)
        assertTrue(controller.cancel())
        assertEquals(ArmStatus.NOT_ARMED, controller.armStatus)
        assertFalse(controller.armedExtraction)
        assertEquals(ExperimentOutcome.CANCELLED, controller.lastResult?.outcome)
        assertNull(controller.lastResult?.extraction)
        assertNull("a cancelled arm still consumed a later event", controller.handleEvent("com.example.target") { screen })
        assertEquals(0, controller.completedCount)

        // Re-arming is the only way records appear, which proves the cancel above stopped a real
        // extraction rather than a no-op.
        controller.arm(true, true, "com.example.target", messageLevelExtraction = true)
        val result = controller.handleEvent("com.example.target") { screen }
        assertNotNull(result?.extraction)
        assertTrue((result?.extraction?.acceptedCount ?: 0) > 0)
    }
}
