package com.example.groomingresearchdemo.model

import com.example.groomingresearchdemo.inspection.extraction.MessageRecord
import com.example.groomingresearchdemo.inspection.extraction.MessageStatus
import com.example.groomingresearchdemo.inspection.extraction.Reconstruction
import com.example.groomingresearchdemo.inspection.extraction.ReconstructionStatus

/**
 * What the handoff decided, and — just as importantly — what it refused to do.
 *
 * REFUSED is a real outcome with a reason attached. The task is explicit that the model must not be
 * run on malformed or unsupported records, and a reconstructor that guesses its way to "ready" is
 * the malformation being refused.
 */
enum class HandoffOutcome {
    /** Records were converted, ordered, and validated. Model input is available. */
    READY,

    /** Nothing was usable: no accepted records, or an unsupported/incomplete reconstruction. */
    REFUSED,
}

/**
 * Adapter from validated extraction records to the existing preprocessing input format.
 *
 * THIS IS THE WHOLE BOUNDARY, AND IT IS ONE-WAY ON PURPOSE
 * `inspection` produces [MessageRecord]s; `model` consumes [ChatMessage]s. This object is the only
 * code that knows both shapes, which is what keeps the extraction stage independent of the
 * prediction stage: neither imports the other's decision logic, and deleting this file would leave
 * both intact and simply unconnected.
 *
 * THE FIVE RULES IT ENFORCES
 *  1. **Order is preserved.** Records are taken in `visibleOrderIndex` order and, because the
 *     preprocessor sorts only when *every* timestamp is present, the reconstructed sequence survives
 *     into the model input untouched. Geometry order in, geometry order out.
 *  2. **Speaker labels cross the boundary only when the reconstruction says they were established.**
 *     A heuristic token like `alignment:left` is a rendering observation, not a person, and pasting
 *     it into `ChatMessage.speaker` would launder a layout guess into training-shaped data.
 *  3. **Unknown stays unknown.** Unknown speakers become `null`, which the preprocessor already
 *     renders as an unlabeled line. Unknown timestamps become `null`, never `0L`, never "now".
 *  4. **Nothing is filled in.** No placeholder name, no synthetic timestamp, no padding turn, no
 *     invented conversation start. If a record is blank, it is not converted.
 *  5. **Unreliable structure is refused, not warned about.** An INCOMPLETE or UNSUPPORTED
 *     reconstruction produces no model input at all. A downstream screen cannot accidentally run a
 *     classifier on a guessed transcript, because there is no transcript to run it on.
 *
 * WHAT THIS CLASS DELIBERATELY DOES NOT DO
 * It never calls a model, never labels anything as grooming, and never imports a prediction type.
 * A suspicious-looking string found by an accessibility heuristic is not evidence of anything, and
 * the closest this file comes to a judgement is `HandoffOutcome.REFUSED`.
 *
 * It also never logs. There is no `LogUtils` import here: the input to this adapter is message text,
 * and the output is a model-ready string, so a single careless log line would put conversation
 * content into Logcat. That is exactly the mistake this boundary exists to make impossible.
 */
object ValidatedRecordAdapter {

    /** Speaker-label values that name a rendering position rather than a person. */
    private const val ALIGNMENT_LABEL_PREFIX = "alignment:"

    fun toChatMessages(records: List<MessageRecord>): List<ChatMessage> =
        records
            .asSequence()
            .filter { it.status == MessageStatus.ACCEPTED && !it.text.isNullOrBlank() }
            .sortedBy { it.visibleOrderIndex ?: Int.MAX_VALUE }
            .map { record ->
                ChatMessage(
                    speaker = record.speakerLabel
                        ?.takeIf { it.isNotBlank() }
                        ?.takeUnless { it.startsWith(ALIGNMENT_LABEL_PREFIX) },
                    text = record.text,
                    timestampMillis = record.timestampMillis,
                )
            }
            .toList()

    /**
     * The one call screens make. Returns a refusal with a stated reason whenever the reconstruction
     * is not fit to become a conversation.
     */
    fun handoff(reconstruction: Reconstruction): ExtractionHandoff {
        if (reconstruction.status != ReconstructionStatus.RECONSTRUCTED) {
            return ExtractionHandoff(
                outcome = HandoffOutcome.REFUSED,
                messageCount = 0,
                reasons = listOf(
                    "Reconstruction status was ${reconstruction.status.name}; " +
                        "only a RECONSTRUCTED sample may enter preprocessing.",
                ) + reconstruction.reasons,
                validation = null,
            )
        }

        val messages = toChatMessages(reconstruction.records)
        if (messages.isEmpty()) {
            return ExtractionHandoff(
                outcome = HandoffOutcome.REFUSED,
                messageCount = 0,
                reasons = listOf("No ACCEPTED record carried non-blank text."),
                validation = null,
            )
        }

        val validation = ConversationPreprocessor.validateModelInput(messages)
        if (!validation.ok) {
            return ExtractionHandoff(
                outcome = HandoffOutcome.REFUSED,
                messageCount = messages.size,
                reasons = listOf("Preprocessing validation failed.") + validation.problems,
                validation = validation,
            )
        }

        val unlabeled = messages.count { it.speaker.isNullOrBlank() }
        val untimed = messages.count { it.timestampMillis == null }
        val reasons = mutableListOf<String>()
        if (unlabeled > 0) {
            reasons += "$unlabeled of ${messages.size} message(s) carry no speaker label; " +
                "they are represented as unlabeled lines, not assigned a name."
        }
        if (untimed == messages.size) {
            reasons += "No timestamp survived extraction, so turn order came from the reconstruction, " +
                "not from the preprocessor's timestamp sort."
        }

        return ExtractionHandoff(
            outcome = HandoffOutcome.READY,
            messageCount = messages.size,
            reasons = reasons,
            validation = validation,
        )
    }
}

/**
 * Result of the extraction → preprocessing handoff.
 *
 * [validation] is null whenever the handoff was refused, so a caller cannot read a model input out
 * of a rejection. That is the "do not run the model on malformed records" rule expressed as a type:
 * there is nothing to pass to `predict()`.
 */
data class ExtractionHandoff(
    val outcome: HandoffOutcome,
    val messageCount: Int,
    val reasons: List<String>,
    val validation: PreprocessValidation?,
)
