package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.FakeNode
import com.example.groomingresearchdemo.inspection.InspectionResult
import com.example.groomingresearchdemo.inspection.NodeBounds
import com.example.groomingresearchdemo.inspection.StructuralInspector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The five claims the results screen may make, tested as five separate gates.
 *
 * A run that completed is not a run that found candidates; candidates are not a reconstruction; a
 * reconstruction is not validated attribution; and attribution is not a complete conversation. The
 * last two gates also need evidence the app cannot generate itself — an annotated sample, or a
 * researcher's statement that the screen really was the whole thread — so on any unattended live run
 * they read "not met". That is the design working, not a shortfall.
 */
class InspectionLadderTest {

    private val viewport get() = FakeNode.viewport()

    private fun cleanChatScreen(): FakeNode {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = viewport)
        list.addChild(FakeNode.messageRow("alpha synthetic turn", top = 100))
        list.addChild(FakeNode.messageRow("beta synthetic turn", top = 200))
        return FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(list)
    }

    /** A name, a body and a stamp inside one vertical band: boundaries cannot be settled here. */
    private fun crowdedRowScreen(): FakeNode {
        val row = FakeNode(classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(0, 100, 1080, 190))
        row.addChild(FakeNode(textValue = "Synthetic Name One", classValue = "android.widget.TextView", boundsValue = NodeBounds(80, 100, 400, 160)))
        row.addChild(FakeNode(textValue = "alpha synthetic turn", classValue = "android.widget.TextView", boundsValue = NodeBounds(420, 100, 900, 160)))
        row.addChild(FakeNode(textValue = "10:45 synthetic stamp", classValue = "android.widget.TextView", boundsValue = NodeBounds(920, 100, 1060, 160)))
        return row
    }

    private fun labelAboveTurnsScreen(): FakeNode {
        val row = FakeNode(classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(0, 80, 1080, 480), nodeIdValue = 5)
        row.addChild(FakeNode(textValue = "Synthetic Name One", classValue = "android.widget.TextView", boundsValue = NodeBounds(80, 100, 400, 160), nodeIdValue = 6))
        row.addChild(FakeNode.messageRow("alpha synthetic turn", top = 200, nodeId = 7))
        row.addChild(FakeNode.messageRow("beta synthetic turn", top = 300, nodeId = 8))
        return FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(row)
    }

    private fun result(root: FakeNode?, extraction: Reconstruction?): InspectionResult {
        val available = root != null
        return InspectionResult(
            timestampMillis = 1_700_000_000_000L,
            targetPackage = "com.google.android.apps.messaging",
            eventReceived = true,
            packageMatched = true,
            rootAvailable = available,
            stats = StructuralInspector().inspect(root),
            outcome = if (available) ExperimentOutcome.COMPLETED else ExperimentOutcome.FAILED,
            failureReason = if (available) null else "Active accessibility root was unavailable at inspection time.",
            extraction = extraction,
        )
    }

    private fun extract(root: FakeNode, extractor: MessageLevelExtractor = MessageLevelExtractor()) =
        extractor.extract(root, viewport, "ladder")

    @Test
    fun withNoResultAtAllNothingOnTheLadderIsMet() {
        val ladder = InspectionLadder.of(null)

        assertEquals(0, ladder.highestLevelReached)
        assertTrue(ladder.unmetReasons.any { it.contains("No inspection has produced a result") })
    }

    @Test
    fun aFailedInspectionDoesNotLiftAnyLaterClaim() {
        val ladder = InspectionLadder.of(result(null, null))

        assertFalse(ladder.inspectionCompleted)
        assertFalse(ladder.candidatesDetected)
        assertFalse(ladder.structureReconstructed)
    }

    @Test
    fun aStructuralOnlyRunWithNoExtractionStageStopsAtLevelOne() {
        val ladder = InspectionLadder.of(result(cleanChatScreen(), null))

        assertTrue(ladder.inspectionCompleted)
        assertFalse(ladder.candidatesDetected)
        assertEquals(1, ladder.highestLevelReached)
        assertTrue(ladder.unmetReasons.any { it.contains("extraction did not run") })
    }

    @Test
    fun aReconstructedScreenReachesLevelThreeAndNoFurtherWithoutExternalEvidence() {
        val root = cleanChatScreen()
        val ladder = InspectionLadder.of(result(root, extract(root)))

        assertTrue(ladder.inspectionCompleted)
        assertTrue(ladder.candidatesDetected)
        assertTrue(ladder.structureReconstructed)
        assertFalse(ladder.speakerAttributionValidated)
        assertFalse(ladder.completeConversationValidated)
        assertEquals(3, ladder.highestLevelReached)
        assertTrue(ladder.unmetReasons.any { it.contains("no speaker rule was enabled") })
        assertTrue(ladder.unmetReasons.any { it.contains("has not attested") })
    }

    @Test
    fun candidatesAreARealStageEvenWhenBoundariesCannotBeSettled() {
        val root = crowdedRowScreen()
        val extraction = extract(root)
        val ladder = InspectionLadder.of(result(root, extraction))

        assertEquals(ReconstructionStatus.INCOMPLETE, extraction.status)
        assertTrue(ladder.candidatesDetected)
        assertFalse(ladder.structureReconstructed)
        // Two of the five claims are met, and the third is the one a transcript would need.
        assertEquals(2, ladder.highestLevelReached)
    }

    @Test
    fun attributionNeedsGroundTruthValidationNotMerelyARuleThatFired() {
        val root = labelAboveTurnsScreen()
        val extraction = extract(root, MessageLevelExtractor(enableSpeakerLabelRule = true))

        val attemptedOnly = InspectionLadder.of(result(root, extraction))
        val validated = InspectionLadder.of(result(root, extraction), attributionValidatedAgainstGroundTruth = true)

        assertEquals(SpeakerAttribution.NODE_LABEL_EVIDENCE, extraction.speakerAttribution)
        assertFalse(attemptedOnly.speakerAttributionValidated)
        assertTrue(validated.speakerAttributionValidated)
    }

    @Test
    fun completenessIsAResearchersStatementNotSomethingTheTreeCanProve() {
        val root = cleanChatScreen()

        val unattested = InspectionLadder.of(result(root, extract(root)))
        val attested = InspectionLadder.of(
            result(root, extract(root)),
            completeSampleAttestedByResearcher = true,
        )

        assertFalse(unattested.completeConversationValidated)
        assertTrue(attested.completeConversationValidated)
        assertEquals(4, attested.highestLevelReached)
    }

    @Test
    fun aPartialSampleCannotBeAttestedAsACompleteConversation() {
        // A screen taller than the viewport: the clipping marks the sample partial, and an
        // attestation cannot override geometry.
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = viewport)
        list.addChild(FakeNode.messageRow("alpha synthetic turn", top = 100))
        list.addChild(FakeNode.messageRow("beta synthetic turn", top = 2350))
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport).addChild(list)

        val ladder = InspectionLadder.of(result(root, extract(root)), completeSampleAttestedByResearcher = true)

        assertTrue(ladder.structureReconstructed)
        assertFalse(ladder.completeConversationValidated)
        assertTrue(ladder.unmetReasons.any { it.contains("the sample is partial") })
    }

    @Test
    fun anUnsupportedScreenReachesLevelOneOnly() {
        val root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = viewport)
            .addChild(FakeNode(textValue = "Type a message", classValue = "android.widget.EditText", boundsValue = NodeBounds(80, 2300, 900, 2380)))
        val ladder = InspectionLadder.of(result(root, extract(root)))

        assertFalse(ladder.candidatesDetected)
        assertFalse(ladder.structureReconstructed)
        assertEquals(1, ladder.highestLevelReached)
        assertTrue(ladder.unmetReasons.any { it.contains("no tree, or nothing message-shaped") })
    }
}
