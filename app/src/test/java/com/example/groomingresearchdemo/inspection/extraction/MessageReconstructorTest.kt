package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.NodeBounds
import com.example.groomingresearchdemo.inspection.Traversal
import com.example.groomingresearchdemo.inspection.candidate.NodeSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reconstruction rules, tested against hand-built sample lists.
 *
 * These tests do not touch Android or the traverser on purpose: every assertion here is about what
 * the rules promise, and it has to be checkable from values in and values out. The end-to-end
 * behaviour against real tree shapes is
 * [com.example.groomingresearchdemo.inspection.extraction.SyntheticConversationFixtureTest].
 */
class MessageReconstructorTest {

    private fun sample(
        text: String,
        top: Int,
        nodeId: Long,
        parentNodeId: Long,
        left: Int = 80,
        right: Int = 900,
        bottom: Int = top + 60,
        className: String? = "android.widget.TextView",
        visible: Boolean? = true,
        childCount: Int = 0,
        aggregates: Boolean = false,
        bounds: NodeBounds? = NodeBounds(left, top, right, bottom),
    ) = NodeSample(
        nodeId = nodeId,
        parentNodeId = parentNodeId,
        depth = 3,
        className = className,
        text = text,
        hasText = true,
        hasContentDescription = false,
        isClickable = false,
        isScrollable = false,
        isVisibleToUser = visible,
        bounds = bounds,
        childCount = childCount,
        aggregatesDescendantText = aggregates,
    )

    private fun traversal(vararg samples: NodeSample) = Traversal(
        samples = samples.toList(),
        nodesVisited = samples.size,
        duplicatesSuppressed = 0,
        nodesLostDuringTraversal = 0,
        truncated = false,
        viewport = NodeBounds(0, 0, 1080, 2400),
        notes = emptyList(),
    )

    @Test
    fun visibleOrderComesFromGeometryEvenWhenTheTraversalWasShuffled() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample("third synthetic line", top = 500, nodeId = 3, parentNodeId = 10),
                sample("first synthetic line", top = 100, nodeId = 1, parentNodeId = 10),
                sample("second synthetic line", top = 300, nodeId = 2, parentNodeId = 10),
            ),
        )

        assertEquals(OrderBasis.GEOMETRY, result.orderBasis)
        assertTrue(result.orderEstablished)
        assertEquals(ReconstructionStatus.RECONSTRUCTED, result.status)
        assertEquals(
            listOf("first synthetic line", "second synthetic line", "third synthetic line"),
            result.records.map { it.text },
        )
        assertEquals(listOf(1, 2, 3), result.records.map { it.visibleOrderIndex })
    }

    @Test
    fun aCandidateWithoutGeometryStopsTheWholeRunFromClaimingAnOrder() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample("placed synthetic line", top = 100, nodeId = 1, parentNodeId = 10),
                sample(
                    "unplaced synthetic line",
                    top = 0,
                    nodeId = 2,
                    parentNodeId = 10,
                    bounds = null,
                ),
            ),
        )

        assertEquals(OrderBasis.TRAVERSAL_UNVERIFIED, result.orderBasis)
        assertFalse(result.orderEstablished)
        assertEquals(ReconstructionStatus.INCOMPLETE, result.status)
        assertEquals(0, result.acceptedCount)
        assertEquals(2, result.candidateStageRecords)
        assertTrue(result.records.all { it.status == MessageStatus.CANDIDATE })
        assertTrue(result.records.all { it.visibleOrderIndex == null })
        assertTrue(result.reasons.any { it.contains("reading order cannot be established") })
    }

    @Test
    fun twoTextNodesSharingARowAreLeftAmbiguousAndNeverMerged() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample("sender label shape", top = 100, nodeId = 1, parentNodeId = 10, left = 80),
                sample("message body shape", top = 110, nodeId = 2, parentNodeId = 10, left = 300),
            ),
        )

        assertEquals(2, result.ambiguousCount)
        assertEquals(0, result.acceptedCount)
        assertTrue(result.records.all { it.status == MessageStatus.AMBIGUOUS })
        assertTrue(result.records.all { it.boundaryUncertain })
        // Rule 4: no composed sentence exists anywhere in the output.
        assertTrue(result.records.none { it.text?.contains("sender label shape message body shape") == true })
        assertEquals(ReconstructionStatus.INCOMPLETE, result.status)
    }

    @Test
    fun aMultiLineBubbleStaysOneRecord() {
        val multiLine = "first line of the synthetic bubble\nsecond line of it\nthird line of it"

        val result = MessageReconstructor().reconstruct(
            traversal(sample(multiLine, top = 100, nodeId = 1, parentNodeId = 10)),
        )

        assertEquals(1, result.records.size)
        assertEquals(multiLine, result.records.single().text)
        assertFalse("the reconstructor split a message", result.records.single().text?.contains('\n') == false)
    }

    @Test
    fun theSameWordsSaidTwiceStayTwoRecords() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample("again", top = 100, nodeId = 1, parentNodeId = 10),
                sample("again", top = 300, nodeId = 2, parentNodeId = 11),
            ),
        )

        assertEquals(2, result.acceptedCount)
        assertEquals(1, result.repeatedTextRecords)
    }

    @Test
    fun aContainerEchoingItsChildIsSuppressedWhileTheChildSurvives() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample(
                    "the same synthetic string twice",
                    top = 90,
                    nodeId = 1,
                    parentNodeId = 9,
                    childCount = 2,
                    aggregates = true,
                ),
                sample("the same synthetic string twice", top = 100, nodeId = 2, parentNodeId = 1),
            ),
        )

        assertEquals(1, result.containerEchoesSuppressed)
        assertEquals(1, result.acceptedCount)
        assertEquals(listOf("the same synthetic string twice"), result.records.map { it.text })
    }

    @Test
    fun aScreenOfNothingButChromeIsUnsupportedRatherThanAnEmptyTranscript() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample("Type a message", top = 100, nodeId = 1, parentNodeId = 10),
                sample("More options", top = 200, nodeId = 2, parentNodeId = 10),
                sample("Active now", top = 300, nodeId = 3, parentNodeId = 10),
            ),
        )

        assertEquals(ReconstructionStatus.UNSUPPORTED, result.status)
        assertTrue(result.records.isEmpty())
        assertEquals(3, result.rejectedCount)
        assertTrue(result.reasons.any { it.contains("Nothing message-shaped") })
    }

    @Test
    fun nothingIsInventedAboutSendersOrTimesByDefault() {
        val result = MessageReconstructor().reconstruct(
            traversal(
                sample("a synthetic line from person one", top = 100, nodeId = 1, parentNodeId = 10),
                sample("a synthetic line from person two", top = 300, nodeId = 2, parentNodeId = 11),
            ),
        )

        assertEquals(SpeakerAttribution.NOT_ATTEMPTED, result.speakerAttribution)
        assertFalse(result.timestampsRecovered)
        assertTrue(result.records.all { it.speakerLabel == null })
        assertTrue(result.records.all { it.timestampMillis == null })
    }

    @Test
    fun theAlignmentRuleReportsASideNotAPerson() {
        val result = MessageReconstructor(enableAlignmentHeuristic = true).reconstruct(
            traversal(
                sample("left column synthetic line", top = 100, nodeId = 1, parentNodeId = 10, left = 40, right = 400),
                sample("right column synthetic line", top = 300, nodeId = 2, parentNodeId = 11, left = 680, right = 1040),
            ),
        )

        assertEquals(SpeakerAttribution.ALIGNMENT_HEURISTIC, result.speakerAttribution)
        val labels = result.records.map { it.speakerLabel }
        assertTrue(labels.all { it != null && it.startsWith("alignment:") })
        assertEquals("alignment:left", labels[0])
        assertEquals("alignment:right", labels[1])
        assertTrue(result.reasons.any { it.contains("not identities") })
    }

    @Test
    fun theSpeakerLabelRuleIsOptInAndAnnouncesItselfAsUnvalidated() {
        val samples = traversal(
            // MEDIUM, not HIGH: the label node is exactly the weak-evidence shape the rule reads.
            sample("Synthetic Name One", top = 100, nodeId = 1, parentNodeId = 10, visible = null),
            sample("a synthetic line said by that name", top = 200, nodeId = 2, parentNodeId = 10),
            sample("another synthetic line by them", top = 300, nodeId = 3, parentNodeId = 10),
        )

        val off = MessageReconstructor().reconstruct(samples)
        val on = MessageReconstructor(enableSpeakerLabelRule = true).reconstruct(samples)

        assertEquals(SpeakerAttribution.NOT_ATTEMPTED, off.speakerAttribution)
        assertTrue(off.records.all { it.speakerLabel == null })

        assertEquals(SpeakerAttribution.NODE_LABEL_EVIDENCE, on.speakerAttribution)
        // The label node stops being a message; the two lines below it inherit it.
        assertEquals(2, on.records.size)
        assertTrue(on.records.all { it.speakerLabel == "Synthetic Name One" })
        assertTrue(on.reasons.any { it.contains("unvalidated against annotated real-app data") })
    }

    @Test
    fun aViewportThatCutsARecordMakesTheSamplePartialAndSaysSo() {
        val result = MessageReconstructor().reconstruct(
            Traversal(
                samples = listOf(
                    sample("a fully visible synthetic line", top = 100, nodeId = 1, parentNodeId = 10),
                    sample(
                        "a synthetic line running off the bottom",
                        top = 2360,
                        nodeId = 2,
                        parentNodeId = 10,
                        bottom = 2500,
                    ),
                ),
                nodesVisited = 2,
                duplicatesSuppressed = 0,
                nodesLostDuringTraversal = 0,
                truncated = false,
                viewport = NodeBounds(0, 0, 1080, 2400),
                notes = emptyList(),
            ),
        )

        assertTrue(result.isPartialVisibleSample)
        assertTrue(result.reasons.any { it.contains("clipped by the viewport") })
    }

    @Test
    fun reasonsAreBoundedAndCarryNoMessageText() {
        val marker = "PRIVATESAMPLESTRING-9d3a"
        val samples = List(10) { sample("$marker line $it", top = 100 + it * 200, nodeId = it.toLong(), parentNodeId = 10) }
        val result = MessageReconstructor().reconstruct(traversal(*samples.toTypedArray()))

        assertTrue(result.reasons.size <= 6)
        assertTrue(result.reasons.none { it.contains(marker) })
        assertTrue(result.records.all { it.evidence.none { token -> token.contains(marker) } })
    }

    @Test
    fun anEmptyTraversalIsUnsupported() {
        val result = MessageReconstructor().reconstruct(
            Traversal(
                samples = emptyList(),
                nodesVisited = 0,
                duplicatesSuppressed = 0,
                nodesLostDuringTraversal = 0,
                truncated = false,
                viewport = null,
                notes = listOf("root unavailable"),
            ),
        )

        assertEquals(ReconstructionStatus.UNSUPPORTED, result.status)
        assertEquals(OrderBasis.NOT_APPLICABLE, result.orderBasis)
        assertFalse(result.boundariesEstablished)
    }

    @Test
    fun candidateIdsAreScopedToTheInspectionAndNotToNodeIdentity() {
        val result = MessageReconstructor(inspectionLabel = "i42").reconstruct(
            traversal(sample("a synthetic line", top = 100, nodeId = 777, parentNodeId = 10)),
        )

        assertEquals("i42-0001", result.records.single().candidateId)
        assertFalse(result.records.single().candidateId.contains("777"))
        assertNull(result.records.single().timestampMillis)
    }

    @Test
    fun aTreeThatChangedMidTraversalIsReportedAsUnreliableNotClean() {
        val result = MessageReconstructor().reconstruct(
            Traversal(
                samples = listOf(sample("a synthetic line", top = 100, nodeId = 1, parentNodeId = 10)),
                nodesVisited = 1,
                duplicatesSuppressed = 0,
                nodesLostDuringTraversal = 2,
                truncated = false,
                viewport = NodeBounds(0, 0, 1080, 2400),
                notes = listOf("2 node(s) reported children that were not retrievable"),
            ),
        )

        assertTrue(result.isPartialVisibleSample)
        assertTrue(result.reasons.any { it.contains("changed during inspection") })
    }
}
