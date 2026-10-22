package com.example.groomingresearchdemo.model

/** Result of [ConversationPreprocessor.validateModelInput]. */
data class PreprocessValidation(
    val ok: Boolean,
    val modelInput: String,
    val cleanedMessageCount: Int,
    val problems: List<String>,
)

/**
 * The synthetic-conversation preprocessing pipeline.
 *
 * Design rules, all straight from the project constraints:
 *   - Normalization is CONSERVATIVE. We collapse whitespace runs and trim, but we do not
 *     lowercase, strip punctuation, remove emoji, or transliterate. There is no evidence yet
 *     that the eventual BTP model was trained on more aggressive cleanup, and inference-time
 *     preprocessing must mirror training-time preprocessing.
 *   - Unicode is preserved. Kotlin [String] is UTF-16, and the only regex we use matches
 *     whitespace, so Malayalam, Devanagari-adjacent clusters, emoji and punctuation survive
 *     byte-for-byte.
 *   - Repeated legitimate messages are kept. Two people saying "ok" in a row is a real
 *     conversational pattern; a dedup pass here would erase the signal the model needs.
 *   - Blank messages ARE dropped, because they carry no information at all after trimming.
 *   - Chronological order is the one true source of turn order. We sort by timestamp ONLY when
 *     every message has one; if any timestamp is missing we keep the supplied order and do not
 *     invent a position.
 *   - Missing speakers are represented as unlabeled lines rather than a fake name.
 *
 * Everything here is pure Kotlin and deterministic. The same input always yields the same
 * output, which is exactly what a preprocessing unit test needs to assert.
 */
object ConversationPreprocessor {

    private val WHITESPACE_RUN = Regex("\\s+")

    /**
     * Collapse every run of whitespace (space, tab, newline, carriage return, form feed,
     * vertical tab) inside [text] into a single ASCII space, then trim the ends. Null maps to
     * the empty string so callers can be uniform.
     */
    fun normalizeWhitespace(text: String?): String {
        if (text == null) return ""
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replace(WHITESPACE_RUN, " ")
    }

    /**
     * Normalise the text of every message and drop the ones that become blank. Speaker and
     * timestamp are carried through untouched. Duplicate messages are preserved on purpose.
     */
    fun cleanMessages(messages: List<ChatMessage>): List<ChatMessage> {
        return messages
            .map { it.copy(text = normalizeWhitespace(it.text)) }
            .filter { !it.text.isNullOrEmpty() }
    }

    /**
     * Make chronological order explicit. When every message has a timestamp, sort ascending by
     * it using a stable sort so equal timestamps keep their supplied order. When any timestamp
     * is missing, return the list as-is — we do not invent positions for unknown-time turns.
     */
    fun preserveMessageOrder(messages: List<ChatMessage>): List<ChatMessage> {
        val everyTimestamped = messages.isNotEmpty() && messages.all { it.timestampMillis != null }
        return if (everyTimestamped) messages.sortedBy { it.timestampMillis } else messages.toList()
    }

    /**
     * Serialise the cleaned, ordered messages into a single string the future model can be
     * pointed at. One message per line. If a speaker is present the line reads
     * `Speaker: body`, otherwise it is just the body. Newlines inside a message cannot survive
     * because [normalizeWhitespace] already collapsed them, so line count == message count.
     */
    fun toModelInput(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        messages.forEachIndexed { index, message ->
            if (index > 0) sb.append('\n')
            val speaker = message.speaker?.takeIf { it.isNotBlank() }
            val body = normalizeWhitespace(message.text)
            if (speaker != null) {
                sb.append(speaker).append(": ").append(body)
            } else {
                sb.append(body)
            }
        }
        return sb.toString()
    }

    /** End-to-end convenience: clean, then order, then serialise. */
    fun preprocess(messages: List<ChatMessage>): String {
        val cleaned = cleanMessages(messages)
        val ordered = preserveMessageOrder(cleaned)
        return toModelInput(ordered)
    }

    /**
     * Sanity check the generated model input. Verifies that a non-empty cleaned conversation
     * produced non-blank text and that line count matches message count — the two invariants
     * that, if broken, would indicate the serialiser drifted from the pipeline.
     */
    fun validateModelInput(messages: List<ChatMessage>): PreprocessValidation {
        val cleaned = preserveMessageOrder(cleanMessages(messages))
        val input = toModelInput(cleaned)
        val problems = mutableListOf<String>()

        if (cleaned.isNotEmpty() && input.isBlank()) {
            problems += "Model input is blank despite at least one non-empty cleaned message."
        }
        val lineCount = if (input.isEmpty()) 0 else input.count { it == '\n' } + 1
        if (lineCount != cleaned.size) {
            problems += "Model input line count ($lineCount) does not match cleaned message count (${cleaned.size})."
        }
        return PreprocessValidation(
            ok = problems.isEmpty(),
            modelInput = input,
            cleanedMessageCount = cleaned.size,
            problems = problems,
        )
    }
}
