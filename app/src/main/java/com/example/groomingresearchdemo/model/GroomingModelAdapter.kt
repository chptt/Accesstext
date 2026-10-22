package com.example.groomingresearchdemo.model

/**
 * Placeholder for the future BTP grooming-detection classifier.
 *
 * WHY THIS EXISTS
 * The demo must not fabricate predictions. Instead the pipeline hands the model adapter a
 * fully preprocessed string and the adapter says, honestly, that no classifier is wired in yet.
 *
 * WHERE TO WIRE THE REAL MODEL
 * Implement this interface once (say `BertGroomingModelAdapter`) inside
 * `com.example.groomingresearchdemo.model.inference` and:
 *   1. Load the fine-tuned weights + the SAME tokenizer the model was trained with. Do not
 *      invent one. If training used SentencePiece, use SentencePiece at inference.
 *   2. Apply the SAME preprocessing you used at training. The functions in
 *      [ConversationPreprocessor] must match, character for character, whatever was done in
 *      the training script. Any drift silently degrades model accuracy.
 *   3. Return a Scored prediction with a per-turn and/or conversation-level label.
 * Then inject that implementation where [PlaceholderGroomingModelAdapter] is used today.
 *
 * NO ASSUMED SCHEMA
 * Nothing in this file assumes the model uses a specific context window, sequence length,
 * label vocabulary, or confidence scale. Fill those in from your own training setup when you
 * integrate.
 */
interface GroomingModelAdapter {

    /** True only when a real classifier is wired in and can produce scored predictions. */
    val isReady: Boolean

    /** Predict on a preprocessed conversation. Never fabricate a value. */
    fun predict(modelInput: String): ModelPrediction
}

/** Result of an inference call. */
sealed interface ModelPrediction {
    /** Returned while no real model is connected. UI must render this as "Model not integrated". */
    data class NotIntegrated(val message: String) : ModelPrediction

    /**
     * Reserved for a real classifier. When you plug one in, populate [label] and [confidence]
     * from actual inference. This class exists so the UI does not have to change shape when
     * the placeholder is swapped out.
     */
    data class Scored(val label: String, val confidence: Double) : ModelPrediction
}

/**
 * The default adapter shipped with this project. It has no weights, no tokenizer, and no
 * classifier. Its only job is to be truthful: "Model not integrated".
 *
 * It MUST NOT return random predictions, MUST NOT return fake confidence values, and MUST
 * NOT label a synthetic sample as a confirmed grooming incident. Any prediction you see in
 * this demo either is NotIntegrated or comes from a real model you have wired in yourself.
 */
object PlaceholderGroomingModelAdapter : GroomingModelAdapter {
    private const val MESSAGE = "Model not integrated"

    override val isReady: Boolean = false

    override fun predict(modelInput: String): ModelPrediction = NotIntegratedHolder

    private val NotIntegratedHolder = ModelPrediction.NotIntegrated(MESSAGE)
}
