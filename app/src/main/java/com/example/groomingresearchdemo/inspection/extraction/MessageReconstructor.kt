package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.NodeBounds
import com.example.groomingresearchdemo.inspection.Traversal
import com.example.groomingresearchdemo.inspection.candidate.CandidateClassifier
import com.example.groomingresearchdemo.inspection.candidate.CandidateDecision
import com.example.groomingresearchdemo.inspection.candidate.CandidateJudgement
import com.example.groomingresearchdemo.inspection.candidate.EvidenceStrength
import com.example.groomingresearchdemo.inspection.candidate.NodeSample

/**
 * Turns a flat traversal into structured message records.
 *
 * Pure Kotlin, no Android, no UI: it takes [Traversal] values and returns [Reconstruction] values.
 * That is what makes the validation suite runnable on synthetic fixtures without an emulator — and
 * what keeps a real conversation from ever needing to be a unit-test input.
 *
 * THE RULES THIS CLASS IMPLEMENTS, AND THE ONES IT REFUSES
 *  1. Visible top-to-bottom order comes from geometry when every candidate has usable bounds.
 *  2. Otherwise order is labelled TRAVERSAL_UNVERIFIED and `orderEstablished` is false. Breadth-first
 *     queue order is the order the framework enumerated children, not the order a person reads.
 *  3. Repeated text from a parent and its child is suppressed by the container-echo rule. Two
 *     *sibling* nodes with identical text are NOT suppressed: two people saying "ok" is data.
 *  4. Adjacent text nodes are NEVER merged. Two texts in one row make that row ambiguous, which is a
 *     truthful outcome; merging them composes a sentence nobody typed.
 *  5. Messages are NEVER split on punctuation or line breaks. A multi-line bubble stays one record.
 *  6. Sender identity from alignment is OFF by default; when explicitly enabled it yields a token
 *     like `alignment:left`, never a name, and marks attribution as a heuristic.
 *  7. Timestamps are never invented. See [Reconstruction.timestampsRecovered].
 *  8. Ambiguity is preserved as AMBIGUOUS records and counted, not resolved by force.
 *  9. Partial samples are detected from truncation, clipping, an unavailable viewport, or a tree that
 *     changed mid-traversal, and are reported as partial.
 * 10. If boundaries cannot be established for every record, the result is INCOMPLETE with reasons.
 *     Guessed text is never handed on as a conversation.
 *
 * @param enableSpeakerLabelRule opt into rule S1. Off by default: it has not been validated against
 *   a single annotated real-app screen, so enabling it in a live inspection would report an
 *   unmeasured hypothesis as a finding.
 * @param enableAlignmentHeuristic opt into rule A1. Off by default for the same reason, plus the
 *   stronger one that a right-to-left locale inverts its meaning entirely.
 */
class MessageReconstructor(
    private val enableSpeakerLabelRule: Boolean = false,
    private val enableAlignmentHeuristic: Boolean = false,
    private val inspectionLabel: String = "insp",
) {

    fun reconstruct(traversal: Traversal): Reconstruction {
        if (traversal.samples.isEmpty()) {
            return Reconstruction.unsupported(
                reasons = listOf("No node was sampled, so no message-shaped evidence exists.") +
                    traversal.notes,
                nodesSampled = 0,
            )
        }

        val rows = RowGrouper(traversal.samples)
        val judged = traversal.samples.map { sample ->
            sample to CandidateClassifier.classify(
                sample = sample,
                inSameRowOtherCandidate = rows.sharedRowWithAnotherTextBearingSibling(sample),
            )
        }

        val rejected = judged.filter { it.second.decision == CandidateDecision.REJECTED }
        val ambiguous = judged.filter { it.second.decision == CandidateDecision.AMBIGUOUS }
        val candidates = judged.filter { it.second.decision == CandidateDecision.CANDIDATE }
        val containerEchoes = rejected.count { it.second.objection == "container-echoes-descendant-text" }

        val orderBasis = when {
            candidates.isEmpty() -> OrderBasis.NOT_APPLICABLE
            candidates.all { it.first.hasUsableBounds } -> OrderBasis.GEOMETRY
            else -> OrderBasis.TRAVERSAL_UNVERIFIED
        }
        val orderEstablished = orderBasis == OrderBasis.GEOMETRY
        val ordered = if (orderEstablished) geometryOrder(candidates) else candidates

        val reasons = mutableListOf<String>()
        reasons += traversal.notes
        if (candidates.isEmpty() && ambiguous.isNotEmpty()) {
            reasons += "${ambiguous.size} text node(s) survived as ambiguous but none as a candidate."
        }
        if (!orderEstablished && candidates.isNotEmpty()) {
            reasons += "At least one candidate has no usable screen rectangle, so reading order cannot be established."
        }
        if (traversal.truncated) {
            reasons += "Traversal hit the node cap, so the visible sample is necessarily partial."
        }
        if (traversal.mutatedDuringInspection) {
            reasons += "The tree changed during inspection; records near the mutation point are unreliable."
        }

        val acceptedRecords = ordered.mapIndexed { index, pair ->
            MessageRecord(
                candidateId = "$inspectionLabel-${"%04d".format(index + 1)}",
                text = pair.first.text,
                speakerLabel = null,
                timestampMillis = null,
                bounds = pair.first.bounds,
                confidence = pair.second.strength,
                evidence = pair.second.evidence,
                // Without geometry there is no defensible reading order, so these stay candidates
                // and the run reports INCOMPLETE. Promoting them anyway is how a guessed transcript
                // gets born.
                status = if (orderEstablished) MessageStatus.ACCEPTED else MessageStatus.CANDIDATE,
                visibleOrderIndex = if (orderEstablished) index + 1 else null,
                boundaryUncertain = false,
            )
        }

        val ambiguousRecords = ambiguous.mapIndexed { index, pair ->
            MessageRecord(
                candidateId = "$inspectionLabel-a${"%04d".format(index + 1)}",
                text = pair.first.text,
                speakerLabel = null,
                timestampMillis = null,
                bounds = pair.first.bounds,
                confidence = pair.second.strength,
                evidence = pair.second.evidence,
                status = MessageStatus.AMBIGUOUS,
                visibleOrderIndex = null,
                boundaryUncertain = true,
            )
        }

        var records = acceptedRecords
        var attribution = SpeakerAttribution.NOT_ATTEMPTED
        if (enableSpeakerLabelRule) {
            val outcome = applySpeakerLabelRule(records)
            records = outcome.records
            attribution = if (outcome.labelsFound > 0) {
                SpeakerAttribution.NODE_LABEL_EVIDENCE
            } else {
                SpeakerAttribution.ATTEMPTED_NOT_ESTABLISHED
            }
            if (outcome.labelsFound > 0) {
                reasons += "Speaker-label rule S1 reassigned ${outcome.labelsFound} record(s); " +
                    "unvalidated against annotated real-app data."
            }
        } else if (enableAlignmentHeuristic) {
            records = applyAlignmentHeuristic(records, traversal.viewport)
            attribution = SpeakerAttribution.ALIGNMENT_HEURISTIC
            reasons += "Alignment heuristic enabled: speaker labels are left/right rendering tokens, not identities."
        }

        val clipped = traversal.viewport?.let { viewport ->
            records.count { it.bounds?.isClippedBy(viewport) == true }
        } ?: 0
        if (clipped > 0) {
            reasons += "$clipped record(s) are clipped by the viewport, so their on-screen text may be cut off."
        }

        val boundariesEstablished = orderEstablished &&
            ambiguousRecords.isEmpty() &&
            records.any { it.isUsable }

        val status = when {
            records.isEmpty() && ambiguousRecords.isEmpty() -> ReconstructionStatus.UNSUPPORTED
            boundariesEstablished -> ReconstructionStatus.RECONSTRUCTED
            else -> ReconstructionStatus.INCOMPLETE
        }
        if (status == ReconstructionStatus.UNSUPPORTED) {
            reasons += "Nothing message-shaped survived classification on this screen."
        }
        if (status == ReconstructionStatus.INCOMPLETE) {
            reasons += "Boundaries could not be established for every record, so this is not a transcript."
        }

        val isPartial = traversal.truncated ||
            traversal.viewport == null ||
            traversal.mutatedDuringInspection ||
            clipped > 0 ||
            ambiguousRecords.isNotEmpty()

        return Reconstruction(
            status = status,
            records = records + ambiguousRecords,
            nodesSampled = traversal.nodesVisited,
            candidateCount = candidates.size,
            acceptedCount = records.count { it.isUsable },
            ambiguousCount = ambiguousRecords.size,
            rejectedCount = rejected.size,
            duplicateVisitsSuppressed = traversal.duplicatesSuppressed,
            containerEchoesSuppressed = containerEchoes,
            orderBasis = orderBasis,
            orderEstablished = orderEstablished,
            speakerAttribution = attribution,
            timestampsRecovered = false,
            isPartialVisibleSample = isPartial,
            boundariesEstablished = boundariesEstablished,
            reasons = reasons.distinct().take(MAX_REASONS),
        )
    }

    /**
     * Rule 1 / rule 2. Sorted by `top`, then `left`, then depth — a stable total order over the
     * boxes the framework actually reported. No row-band clustering: pixel tops jitter between app
     * builds, and a quantisation step would be a second unmeasured heuristic sitting on top of the
     * first. Nodes that merely overlap vertically are separated elsewhere, by [RowGrouper].
     */
    private fun geometryOrder(
        pairs: List<Pair<NodeSample, CandidateJudgement>>,
    ): List<Pair<NodeSample, CandidateJudgement>> = pairs.sortedWith(
        compareBy({ it.first.bounds!!.top }, { it.first.bounds!!.left }, { it.first.depth }),
    )

    /**
     * Rule S1, off by default.
     *
     * A short accepted record that is immediately followed in geometry order by one or more records
     * starting below its own bottom edge, all sharing its parent, is read as a sender label for that
     * run; the label stops being a message and the following records gain its text as
     * [MessageRecord.speakerLabel].
     *
     * WHY IT IS OPT-IN: nothing in R1-R8 shows this rule firing correctly on a real app, because
     * those runs recorded counts and never attempted attribution. A rule with no annotated evidence
     * is a hypothesis, and enabling it by default would publish the hypothesis as a result.
     */
    private fun applySpeakerLabelRule(records: List<MessageRecord>): LabelOutcome {
        if (records.size < 2) return LabelOutcome(records, 0)
        val out = ArrayList<MessageRecord>(records.size)
        var labelsFound = 0
        var index = 0
        while (index < records.size) {
            val head = records[index]
            val run = ArrayList<MessageRecord>()
            var cursor = index + 1
            while (cursor < records.size) {
                val next = records[cursor]
                if (next.bounds != null && head.bounds != null && next.bounds.top >= head.bounds.bottom - 1) {
                    run += next
                    cursor++
                } else {
                    break
                }
            }
            val labelShaped = head.text != null &&
                head.text.length in 1..40 &&
                run.isNotEmpty() &&
                head.confidence != EvidenceStrength.HIGH
            if (labelShaped) {
                labelsFound++
                out += run.map { it.copy(speakerLabel = head.text) }
                index = cursor
            } else {
                out += head
                index++
            }
        }
        return LabelOutcome(out, labelsFound)
    }

    /**
     * Rule A1, off by default. Assigns `alignment:left` / `alignment:right` / `alignment:center` /
     * `alignment:full-width` from horizontal position against the viewport midline.
     *
     * The tokens are deliberately not names and deliberately awkward, so a screenshot of a record
     * carrying one cannot be mistaken for a recovered sender identity.
     */
    private fun applyAlignmentHeuristic(
        records: List<MessageRecord>,
        viewport: NodeBounds?,
    ): List<MessageRecord> {
        if (viewport == null || !viewport.isValid) return records
        val midline = viewport.centerX
        return records.map { record ->
            val bounds = record.bounds
            if (bounds == null || !bounds.isValid) record
            else {
                val side = when {
                    bounds.width >= viewport.width * 0.85 -> "full-width"
                    bounds.centerX < midline - viewport.width * 0.1 -> "left"
                    bounds.centerX > midline + viewport.width * 0.1 -> "right"
                    else -> "center"
                }
                record.copy(speakerLabel = "alignment:$side")
            }
        }
    }

    private data class LabelOutcome(val records: List<MessageRecord>, val labelsFound: Int)

    companion object {
        private const val MAX_REASONS = 6
    }
}

/**
 * Groups samples into "rows" for the single question the classifier is allowed to ask about them:
 * does another text-bearing node from the same parent sit in the same vertical band?
 *
 * That one boolean is the difference between "a bubble with a body" and "a row with a name, a body
 * and a timestamp", and the classifier must not pretend to know which it is looking at. So this
 * computes the fact from geometry plus parentage and hands over a boolean. Nothing else — and no
 * merging.
 */
class RowGrouper(samples: List<NodeSample>) {

    private val textBearing: List<NodeSample> = samples.filter { !it.text.isNullOrBlank() }

    fun sharedRowWithAnotherTextBearingSibling(sample: NodeSample): Boolean {
        if (sample.text.isNullOrBlank()) return false
        val bounds = sample.bounds?.takeIf { it.isValid } ?: return false
        return textBearing.any {
            it !== sample &&
                it.parentNodeId == sample.parentNodeId &&
                it.bounds?.isValid == true &&
                verticallyOverlapping(bounds, it.bounds!!)
        }
    }

    /**
     * Overlap is judged against the *shorter* box, so a one-line timestamp inside a tall message row
     * counts as same-row, while two genuinely separate rows — which do not overlap at all — never do.
     */
    private fun verticallyOverlapping(a: NodeBounds, b: NodeBounds): Boolean {
        val overlapTop = maxOf(a.top, b.top)
        val overlapBottom = minOf(a.bottom, b.bottom)
        if (overlapBottom <= overlapTop) return false
        val shorter = minOf(a.height, b.height).coerceAtLeast(1)
        return (overlapBottom - overlapTop) >= shorter * 0.5
    }
}
