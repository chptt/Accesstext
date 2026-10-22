package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.FakeNode
import com.example.groomingresearchdemo.inspection.InspectionResult
import com.example.groomingresearchdemo.inspection.MessageLevelTraverser
import com.example.groomingresearchdemo.inspection.StructuralInspector
import com.example.groomingresearchdemo.inspection.candidate.CandidateClassifier
import com.example.groomingresearchdemo.report.CompatibilityReportBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The extraction stage's privacy guarantee, asserted rather than promised.
 *
 * The compatibility report already had a leakage test from the structural era. This file extends the
 * same idea to the stage that DOES hold text: one marker string is placed in a tree, run through the
 * real pipeline, and then hunted for in every surface that is supposed to be content-free — the
 * report, the reasons, the evidence tokens, the candidate ids, and the `toString()` of every object
 * in the pipeline, because a generated `toString()` is the quietest way for text to end up in a log.
 */
class ExtractionPrivacyTest {

    private val marker = "PRIVATE-MESSAGE-MARKER-4b7b"

    private fun treeWithMarker(): FakeNode {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = FakeNode.viewport())
        list.addChild(FakeNode.messageRow(marker, top = 100))
        list.addChild(FakeNode.messageRow("a second synthetic turn", top = 200))
        return FakeNode(classValue = "android.widget.FrameLayout", boundsValue = FakeNode.viewport()).addChild(list)
    }

    private fun reconstruct(): Reconstruction = MessageLevelExtractor().extract(treeWithMarker(), FakeNode.viewport(), "privacy")

    private fun resultWithExtraction(extraction: Reconstruction): InspectionResult {
        val root = treeWithMarker()
        return InspectionResult(
            timestampMillis = 1_700_000_000_000L,
            targetPackage = "com.google.android.apps.messaging",
            eventReceived = true,
            packageMatched = true,
            rootAvailable = true,
            stats = StructuralInspector().inspect(root),
            outcome = ExperimentOutcome.COMPLETED,
            failureReason = null,
            extraction = extraction,
        )
    }

    @Test
    fun theMarkerReachesTheRecordsSoThisTestIsActuallyTestingSomething() {
        val result = reconstruct()

        assertTrue(result.records.any { it.text == marker })
        assertTrue(result.acceptedCount > 0)
    }

    @Test
    fun theCompatibilityReportStaysCleanEvenWhenExtractionRan() {
        val report = CompatibilityReportBuilder.build(
            result = resultWithExtraction(reconstruct()),
            consentAccepted = true,
            serviceEnabled = true,
            targetInstalled = true,
        )

        assertFalse("the report leaked extracted text", report.contains(marker))
        // The structural half is still reported, because that is the report's job.
        assertTrue(report.contains(CompatibilityReportBuilder.PRIVACY_FOOTER))
    }

    @Test
    fun noPipelineObjectPrintsMessageTextThroughToString() {
        val traversal = MessageLevelTraverser().traverse(treeWithMarker(), FakeNode.viewport())
        val result = reconstruct()
        val record = result.records.first { it.text == marker }
        val sample = traversal.samples.first { it.text == marker }
        val judgement = CandidateClassifier.classify(sample)

        listOf(
            "Traversal" to traversal.toString(),
            "NodeSample" to sample.toString(),
            "CandidateJudgement" to judgement.toString(),
            "MessageRecord" to record.toString(),
            "Reconstruction" to result.toString(),
        ).forEach { (name, rendered) ->
            assertFalse("$name.toString() leaked the marker", rendered.contains(marker))
        }
    }

    @Test
    fun aRecordStillReportsThatItHoldsTextWithoutSayingWhatItSays() {
        val rendered = reconstruct().records.first { it.text == marker }.toString()

        assertTrue(rendered.contains("textPresent=true"))
        assertTrue(rendered.contains("candidateId=privacy-"))
    }

    @Test
    fun reasonsAndEvidenceNeverQuoteTheScreen() {
        val result = reconstruct()

        assertTrue(result.reasons.none { it.contains(marker) })
        assertTrue(result.records.flatMap { it.evidence }.none { it.contains(marker) })
        assertTrue(result.records.none { it.candidateId.contains(marker) })
    }

    @Test
    fun forgettingTheRecordsKeepsEveryDiagnosticThatMatters() {
        val full = reconstruct()
        val published = full.withoutRecords()

        assertTrue(full.records.any { it.text == marker })
        assertTrue(published.records.isEmpty())
        assertEqualsCounts(full, published)
        assertFalse(published.toString().contains(marker))
        // The published copy is what a session report holds; the full copy is what review reads.
        assertTrue(full.nodesSampled > 0)
    }

    private fun assertEqualsCounts(full: Reconstruction, stripped: Reconstruction) {
        assertEquals(full.status, stripped.status)
        assertEquals(full.nodesSampled, stripped.nodesSampled)
        assertEquals(full.candidateCount, stripped.candidateCount)
        assertEquals(full.acceptedCount, stripped.acceptedCount)
        assertEquals(full.ambiguousCount, stripped.ambiguousCount)
        assertEquals(full.rejectedCount, stripped.rejectedCount)
        assertEquals(full.orderBasis, stripped.orderBasis)
        assertEquals(full.speakerAttribution, stripped.speakerAttribution)
        assertEquals(full.reasons, stripped.reasons)
    }
}
