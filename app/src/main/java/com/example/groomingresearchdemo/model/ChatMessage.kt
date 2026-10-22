package com.example.groomingresearchdemo.model

/**
 * One turn in a synthetic conversation.
 *
 * Every field is nullable because the spec forbids inventing missing speakers or timestamps:
 * if real training data lacked one of them, the pipeline must represent that absence honestly
 * rather than silently filling in a placeholder.
 */
data class ChatMessage(
    val speaker: String?,
    val text: String?,
    val timestampMillis: Long?,
)
