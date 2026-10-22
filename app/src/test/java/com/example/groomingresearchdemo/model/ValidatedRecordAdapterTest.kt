package com.example.groomingresearchdemo.model

import com.example.groomingresearchdemo.inspection.NodeBounds
import com.example.groomingresearchdemo.inspection.candidate.EvidenceStrength
import com.example.groomingresearchdemo.inspection.extraction.MessageRecord
import com.example.groomingresearchdemo.inspection.extraction.MessageStatus
import com.example.groomingresearchdemo.inspection.extraction.Reconstruction
import com.example.groomingresearchdemo.inspection.extraction.ReconstructionStatus
import com.example.groomingresearchdemo.inspection.extraction.SpeakerAttribution
import com.example.groomingresearchdemo.inspection.extraction.OrderBasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one boundary between extraction and preprocessing, tested from both sides.
 *
 * What matters here is not that conversion works — it is that refusal works. A handoff that warns
 * and continues would let a guessed transcript reach a classifier; this one returns no model input
 * at all, so there is literally nothing for a caller to feed `predict()`.
 */
class ValidatedRecordAdapterTest {

    private fun record(
        text: String?,
        index: Int?,
        status: MessageStatus = MessageStatus.ACCEPTED,
        speaker: String? = null,
        timestamp: Long? = null,
    ) = MessageRecord(
        candidateId = "t-%04d".format(index ?: 0),
        text = text,
        speakerLabel = speaker,
        timestampMillis = timestamp,
        bounds = NodeBounds(80, (index ?: 0) * 100, 900, (index ?: 0) * 100 + 60),
        confidence = EvidenceStrength.HIGH,
        evidence = listOf("text-present", "leaf-node"),
        status = status,
        visibleOrderIndex = index,
    )

    private fun reconstruction(
        status: ReconstructionStatus,
        records: List<MessageRecord>,
        reasons: List<String> = emptyList(),
    ) = Reconstruction(
        status = status,
        records = records,
        nodesSampled = 20,
        candidateCount = records.size,
        acceptedCount = records.count { it.isUsable },
        ambiguousCount = records.count { it.status == MessageStatus.AMBIGUOUS },
        rejectedCount = 0,
        duplicateVisitsSuppressed = 0,
        containerEchoesSuppressed = 0,
        orderBasis = OrderBasis.GEOMETRY,
        orderEstablished = status == ReconstructionStatus.RECONSTRUCTED,
        speakerAttribution = SpeakerAttribution.NOT_ATTEMPTED,
        timestampsRecovered = false,
        isPartialVisibleSample = false,
        boundariesEstablished = status == ReconstructionStatus.RECONSTRUCTED,
        reasons = reasons,
    )

    @Test
    fun anIncompleteReconstructionIsRefusedAndReturnsNoModelInput() {
        val handoff = ValidatedRecordAdapter.handoff(
            reconstruction(
                ReconstructionStatus.INCOMPLETE,
                listOf(record("alpha synthetic turn", 1), record("beta synthetic turn", 2)),
                reasons = listOf("Boundaries could not be established for every record."),
            ),
        )

        assertEquals(HandoffOutcome.REFUSED, handoff.outcome)
        assertNull("a refusal must not carry a model input", handoff.validation)
        assertTrue(handoff.reasons.any { it.contains("INCOMPLETE") })
    }

    @Test
    fun anUnsupportedReconstructionIsRefused() {
        val handoff = ValidatedRecordAdapter.handoff(reconstruction(ReconstructionStatus.UNSUPPORTED, emptyList()))

        assertEquals(HandoffOutcome.REFUSED, handoff.outcome)
        assertNull(handoff.validation)
    }

    @Test
    fun aReconstructedSampleCrossesInItsPlacedOrderNotInRecordOrder() {
        val records = listOf(
            record("gamma synthetic turn", 3),
            record("alpha synthetic turn", 1),
            record("beta synthetic turn", 2),
        )

        val messages = ValidatedRecordAdapter.toChatMessages(records)

        assertEquals(
            listOf("alpha synthetic turn", "beta synthetic turn", "gamma synthetic turn"),
            messages.map { it.text },
        )
    }

    @Test
    fun onlyAcceptedRecordsCrossTheBoundary() {
        val messages = ValidatedRecordAdapter.toChatMessages(
            listOf(
                record("alpha synthetic turn", 1),
                record("ambiguous synthetic turn", null, status = MessageStatus.AMBIGUOUS),
                record("unplaced synthetic turn", null, status = MessageStatus.CANDIDATE),
                record("   ", 2),
            ),
        )

        assertEquals(1, messages.size)
        assertEquals("alpha synthetic turn", messages.single().text)
    }

    @Test
    fun anAlignmentTokenIsNeverRewrittenAsAPerson() {
        val messages = ValidatedRecordAdapter.toChatMessages(
            listOf(record("alpha synthetic turn", 1, speaker = "alignment:left")),
        )

        // The label is dropped, not converted: a layout side is not a sender.
        assertNull(messages.single().speaker)
        assertEquals("alpha synthetic turn", messages.single().text)
    }

    @Test
    fun aStructurallyEvidencedSpeakerLabelDoesCross() {
        val messages = ValidatedRecordAdapter.toChatMessages(
            listOf(record("alpha synthetic turn", 1, speaker = "Synthetic Name One")),
        )

        assertEquals("Synthetic Name One", messages.single().speaker)
    }

    @Test
    fun anUnknownTimestampStaysNullRatherThanBecomingZeroOrNow() {
        val messages = ValidatedRecordAdapter.toChatMessages(listOf(record("alpha synthetic turn", 1)))

        assertNull(messages.single().timestampMillis)
    }

    @Test
    fun aReadyHandoffValidatesWithoutInventingAnything() {
        val handoff = ValidatedRecordAdapter.handoff(
            reconstruction(
                ReconstructionStatus.RECONSTRUCTED,
                listOf(record("alpha synthetic turn", 1), record("beta synthetic turn", 2)),
            ),
        )

        assertEquals(HandoffOutcome.READY, handoff.outcome)
        assertEquals(2, handoff.messageCount)
        assertNotNull(handoff.validation)
        assertTrue(handoff.validation!!.ok)
        assertTrue(handoff.reasons.any { it.contains("no speaker label") })
        assertTrue(handoff.reasons.any { it.contains("No timestamp survived extraction") })
        // The model input is the two turns, in order, unlabeled — nothing added.
        assertEquals(2, handoff.validation!!.modelInput.lines().size)
    }

    @Test
    fun theAdapterNeverProducesAGroomingJudgement() {
        val handoff = ValidatedRecordAdapter.handoff(
            reconstruction(
                ReconstructionStatus.RECONSTRUCTED,
                listOf(record("alpha synthetic turn", 1)),
            ),
        )

        assertFalse(handoff.reasons.any { it.contains("grooming", ignoreCase = true) })
        // And the placeholder model still refuses to guess.
        assertTrue(PlaceholderGroomingModelAdapter.predict(handoff.validation!!.modelInput) is ModelPrediction.NotIntegrated)
        assertFalse(PlaceholderGroomingModelAdapter.isReady)
    }
}
