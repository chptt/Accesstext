package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.NodeBounds
import com.example.groomingresearchdemo.inspection.candidate.EvidenceStrength

/**
 * Where a record sits in the ladder. These are NOT interchangeable, and the results screen is not
 * allowed to render a later stage on the strength of an earlier one.
 *
 * CANDIDATE  — the classifier kept this node as message-shaped, but reading order could not be
 *              established, so it has not become a reconstructed message yet.
 * ACCEPTED   — it survived dedup and classification AND sits in a geometrically ordered sequence.
 * AMBIGUOUS  — the tree does not settle it. Kept visible on purpose: never silently promoted,
 *              never silently dropped.
 * REJECTED   — chrome, container echo, invisible, or unreadable. Counted, never rendered.
 */
enum class MessageStatus { CANDIDATE, ACCEPTED, AMBIGUOUS, REJECTED }

/**
 * How the ordering of the reconstructed records was arrived at. The distinction matters more than
 * the order itself: a transcript presented without its ordering basis is a transcript that claims
 * more than it knows.
 */
enum class OrderBasis {
    /** Every candidate had usable bounds, so top-to-bottom reading order came from geometry. */
    GEOMETRY,

    /** Bounds were missing or degenerate. Order is traversal order, which is NOT reading order. */
    TRAVERSAL_UNVERIFIED,

    /** Nothing was kept, so there is no order to discuss. */
    NOT_APPLICABLE,
}

/** Whether sender identity was recovered — and by what, or why not. */
enum class SpeakerAttribution {
    /** The default. No sender claim of any kind is made. */
    NOT_ATTEMPTED,

    /** Rule S1 fired: a node acting as a sender label was positively identified structurally. */
    NODE_LABEL_EVIDENCE,

    /** Rule A1 fired: left/right alignment used as a proxy. A rendering artefact, not an identity. */
    ALIGNMENT_HEURISTIC,

    /** A rule was enabled and reached no defensible result. */
    ATTEMPTED_NOT_ESTABLISHED,
}

/**
 * The overall verdict on whether a conversation was reconstructed.
 *
 * UNSUPPORTED   — nothing usable was exposed, or the screen is outside what this pipeline can read.
 * INCOMPLETE    — candidates exist but message boundaries could not be established reliably.
 *                 This is a *result*, not a failure to be hidden.
 * RECONSTRUCTED — bounded, ordered, de-duplicated records were produced for the visible screen.
 *                 Still says nothing about senders, and still says nothing about whether the
 *                 conversation was complete.
 */
enum class ReconstructionStatus { UNSUPPORTED, INCOMPLETE, RECONSTRUCTED }

/**
 * One reconstructed message.
 *
 * [text] is the only field in this codebase that carries content FROM the target app, which is why
 * the retention rule is enforced by a lifetime rather than a comment: instances live in a
 * memory-only buffer, are cleared when the inspection finishes or is cancelled, never enter
 * `StructuralStats`, never enter the Logcat line, never enter the compatibility report, and never
 * leave the process — there is no `INTERNET` permission to leave through and no file writer either.
 *
 * [speakerLabel] and [timestampMillis] are nullable because the tree usually cannot support filling
 * them, and the project's rule is that an unverifiable field stays null rather than becoming a
 * plausible-looking guess.
 */
data class MessageRecord(
    /** Scoped to one inspection. Not derived from node ids, so it cannot outlive the run. */
    val candidateId: String,
    val text: String?,
    val speakerLabel: String?,
    val timestampMillis: Long?,
    val bounds: NodeBounds?,
    val confidence: EvidenceStrength,
    /** Structural rule tokens only — never a quotation of [text]. */
    val evidence: List<String>,
    val status: MessageStatus,
    /** Position in the reconstructed sequence, or null when ordering was not established. */
    val visibleOrderIndex: Int?,
    /** Set when the tree could not settle whether this row is one message or several. */
    val boundaryUncertain: Boolean = false,
) {
    /** True when a downstream consumer may treat this record as conversation content. */
    val isUsable: Boolean get() = status == MessageStatus.ACCEPTED && !text.isNullOrBlank()

    /**
     * NOT the generated one, and that is a privacy control rather than a style choice. A data-class
     * `toString()` prints every property, so the default would put message text into any log line,
     * exception message or debugger read of this object — the one channel this project promises not
     * to use. Text is reported as present-or-absent only.
     */
    override fun toString(): String =
        "MessageRecord(candidateId=$candidateId, status=$status, confidence=$confidence, " +
            "textPresent=${text != null}, speakerLabelPresent=${speakerLabel != null}, " +
            "timestampMillis=$timestampMillis, visibleOrderIndex=$visibleOrderIndex, " +
            "boundaryUncertain=$boundaryUncertain, bounds=$bounds, evidence=$evidence)"
}

/**
 * The full output of reconstructing one visible screen.
 *
 * The count fields are what the results screen shows by default. [records] is what the separate,
 * deliberate, temporary review action reads. They are different surfaces precisely because a
 * summary must be safe to leave on screen and message text is not.
 */
data class Reconstruction(
    val status: ReconstructionStatus,
    val records: List<MessageRecord>,
    val nodesSampled: Int,
    val candidateCount: Int,
    val acceptedCount: Int,
    val ambiguousCount: Int,
    val rejectedCount: Int,
    val duplicateVisitsSuppressed: Int,
    val containerEchoesSuppressed: Int,
    val orderBasis: OrderBasis,
    val orderEstablished: Boolean,
    val speakerAttribution: SpeakerAttribution,
    /**
     * Always false from this pipeline, and stated as a field rather than assumed, because "were
     * timestamps found?" is a question the results screen has to answer explicitly. The accessibility
     * path never populates [MessageRecord.timestampMillis]: a rendered clock glyph in a chat row is
     * not evidence reliable enough to write a number into a research record, and an invented
     * timestamp would poison the ordering invariant the preprocessor depends on.
     */
    val timestampsRecovered: Boolean,
    /** True when the sample cannot be assumed to be the whole conversation. */
    val isPartialVisibleSample: Boolean,
    /** True when message boundaries were determinable at all. Gates RECONSTRUCTED. */
    val boundariesEstablished: Boolean,
    /** Content-free explanations of why a run stopped where it did. */
    val reasons: List<String>,
) {
    /** Classifier-stage records: everything kept as message-shaped, placed or not. */
    val candidateStageRecords: Int get() = records.count { it.status == MessageStatus.CANDIDATE }

    val ambiguousBoundaryRecords: Int get() = records.count { it.boundaryUncertain }

    /**
     * A copy that carries every count, status, reason and flag but holds no [MessageRecord] at all.
     *
     * This is what is stored on [com.example.groomingresearchdemo.inspection.InspectionResult] and
     * therefore in the in-memory session report list, which can hold up to twenty entries. Without it,
     * twenty armed inspections would mean twenty retained sets of message text — a retention surface
     * that the data-minimisation rule refuses. Exactly one full reconstruction is retained at a time,
     * in [com.example.groomingresearchdemo.state.ResearchStore], for the deliberate review action.
     */
    fun withoutRecords(): Reconstruction = copy(records = emptyList())

    /**
     * Identical text kept as separate records rather than collapsed. Chat says the same thing twice;
     * a reconstructor that deduped by content would erase that turn and never tell anyone.
     */
    val repeatedTextRecords: Int
        get() = records.filter { it.isUsable }.groupBy { it.text }.values.count { it.size > 1 }

    /** Content-free by construction, for the same reason [MessageRecord.toString] is. */
    override fun toString(): String =
        "Reconstruction(status=$status, records=${records.size}, nodesSampled=$nodesSampled, " +
            "candidates=$candidateCount, accepted=$acceptedCount, ambiguous=$ambiguousCount, " +
            "rejected=$rejectedCount, duplicateVisitsSuppressed=$duplicateVisitsSuppressed, " +
            "containerEchoesSuppressed=$containerEchoesSuppressed, orderBasis=$orderBasis, " +
            "orderEstablished=$orderEstablished, speakerAttribution=$speakerAttribution, " +
            "timestampsRecovered=$timestampsRecovered, isPartialVisibleSample=$isPartialVisibleSample, " +
            "boundariesEstablished=$boundariesEstablished, reasons=$reasons)"

    companion object {
        fun unsupported(reasons: List<String>, nodesSampled: Int = 0): Reconstruction = Reconstruction(
            status = ReconstructionStatus.UNSUPPORTED,
            records = emptyList(),
            nodesSampled = nodesSampled,
            candidateCount = 0,
            acceptedCount = 0,
            ambiguousCount = 0,
            rejectedCount = 0,
            duplicateVisitsSuppressed = 0,
            containerEchoesSuppressed = 0,
            orderBasis = OrderBasis.NOT_APPLICABLE,
            orderEstablished = false,
            speakerAttribution = SpeakerAttribution.NOT_ATTEMPTED,
            timestampsRecovered = false,
            isPartialVisibleSample = false,
            boundariesEstablished = false,
            reasons = reasons,
        )
    }
}
