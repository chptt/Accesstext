package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.ExperimentOutcome
import com.example.groomingresearchdemo.inspection.InspectionResult

/**
 * The five claims the results screen is allowed to make, held as five independent gates.
 *
 * They are deliberately NOT one enum and NOT one Boolean. A single "success" value would let level 1
 * satisfy level 5 in the UI, which is exactly the collapse the task forbids: an inspection that
 * completed is not a conversation that was reconstructed, and a reconstruction is not a validated
 * attribution. Each field below has its own criterion, and each is displayed only when that
 * criterion is met.
 *
 * Levels 4 and 5 also require evidence the app cannot produce by itself — a researcher's annotated
 * sample, or a researcher's attestation that the visible screen really was the whole conversation.
 * So in any unattended live run they read `not met`, and that is the correct answer rather than a
 * shortfall to design around.
 */
data class InspectionLadder(
    val inspectionCompleted: Boolean,
    val candidatesDetected: Boolean,
    val structureReconstructed: Boolean,
    val speakerAttributionValidated: Boolean,
    val completeConversationValidated: Boolean,
    /** Why each unmet gate is unmet, so "no" is never left looking like a silent failure. */
    val unmetReasons: List<String>,
) {
    /** 0 when nothing was met, 5 when everything was. Displayed as a number, never as "success". */
    val highestLevelReached: Int
        get() = listOf(
            inspectionCompleted,
            candidatesDetected,
            structureReconstructed,
            speakerAttributionValidated,
            completeConversationValidated,
        ).count { it }

    companion object {
        /**
         * Derives the ladder from what a run actually produced.
         *
         * @param attributionValidatedAgainstGroundTruth comes from the validation harness, not from
         *   the tree. It stays false until an annotated, consented sample says the attribution was
         *   right on that sample.
         * @param completeSampleAttestedByResearcher is a human statement that the inspected screen
         *   held the entire conversation. The service cannot know this and must not infer it from
         *   "the list happened to start at the top".
         */
        fun of(
            result: InspectionResult?,
            attributionValidatedAgainstGroundTruth: Boolean = false,
            completeSampleAttestedByResearcher: Boolean = false,
        ): InspectionLadder {
            if (result == null) {
                return InspectionLadder(
                    inspectionCompleted = false,
                    candidatesDetected = false,
                    structureReconstructed = false,
                    speakerAttributionValidated = false,
                    completeConversationValidated = false,
                    unmetReasons = listOf("No inspection has produced a result in this session."),
                )
            }
            val reasons = mutableListOf<String>()
            val reconstruction = result.extraction

            val level1 = result.outcome == ExperimentOutcome.COMPLETED && result.stats != null
            if (!level1) {
                reasons += "Level 1 not met: ${result.failureReason ?: "outcome was ${result.outcome.name}"}."
            }

            val level2 = reconstruction != null &&
                (reconstruction.candidateCount + reconstruction.ambiguousCount) > 0
            if (!level2) {
                reasons += when {
                    reconstruction == null ->
                        "Level 2 not met: message-level extraction did not run for this inspection."
                    reconstruction.status == ReconstructionStatus.UNSUPPORTED ->
                        "Level 2 not met: the extraction stage found no tree, or nothing message-shaped, to work from."
                    else -> "Level 2 not met: no node survived as a candidate."
                }
            }

            val level3 = reconstruction?.status == ReconstructionStatus.RECONSTRUCTED
            if (!level3) {
                reasons += when (reconstruction?.status) {
                    null -> "Level 3 not met: message-level extraction produced no reconstruction."
                    ReconstructionStatus.UNSUPPORTED -> "Level 3 not met: the screen exposed nothing message-shaped."
                    ReconstructionStatus.INCOMPLETE -> "Level 3 not met: boundaries were not established for every record."
                    ReconstructionStatus.RECONSTRUCTED -> "Level 3 not met: reason unavailable."
                }
            }

            // Reachable only when level 3 held, which is what makes the reconstruction non-null here.
            val reconstructed = if (level3) reconstruction else null

            val level4 = reconstructed != null &&
                reconstructed.speakerAttribution != SpeakerAttribution.NOT_ATTEMPTED &&
                attributionValidatedAgainstGroundTruth
            if (!level4) {
                reasons += when {
                    reconstructed == null -> "Level 4 not met: attribution was not attempted on an unreconstructed sample."
                    reconstructed.speakerAttribution == SpeakerAttribution.NOT_ATTEMPTED ->
                        "Level 4 not met: no speaker rule was enabled for this run."
                    else -> "Level 4 not met: attribution has no annotated ground-truth sample behind it."
                }
            }

            val level5 = reconstructed != null &&
                !reconstructed.isPartialVisibleSample &&
                completeSampleAttestedByResearcher
            if (!level5) {
                reasons += when {
                    reconstructed == null -> "Level 5 not met: no reconstructed structure to validate."
                    reconstructed.isPartialVisibleSample ->
                        "Level 5 not met: the sample is partial, so completeness cannot be claimed."
                    else -> "Level 5 not met: the researcher has not attested that this screen was the whole conversation."
                }
            }

            return InspectionLadder(
                inspectionCompleted = level1,
                candidatesDetected = level2,
                structureReconstructed = level3,
                speakerAttributionValidated = level4,
                completeConversationValidated = level5,
                unmetReasons = reasons,
            )
        }
    }
}
