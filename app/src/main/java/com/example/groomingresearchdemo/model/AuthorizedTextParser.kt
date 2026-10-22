package com.example.groomingresearchdemo.model

/**
 * Parses pasted, research-authorized conversation text into [ChatMessage]s.
 *
 * This exists for the "Authorized text import" screen, where the researcher supplies content they
 * are permitted to process. It is NOT part of the accessibility path: nothing here is ever
 * observed from another app, and nothing here is ever persisted or logged.
 *
 * Deliberate limits, so the parser cannot invent structure the input does not have:
 *  - a line of the form `Name: body` yields speaker "Name"; anything else yields a null speaker;
 *  - timestamps are ALWAYS null — a bare pasted line carries no reliable time, and the
 *    preprocessor refuses to order messages by invented timestamps.
 */
object AuthorizedTextParser {

    /** Speaker and body are separated by the first `: ` on the line, if present. */
    private const val SPEAKER_SEPARATOR = ": "

    fun parse(raw: String): List<ChatMessage> =
        raw.lines().map { line ->
            val separator = line.indexOf(SPEAKER_SEPARATOR)
            if (separator > 0) {
                val speaker = line.substring(0, separator).trim().takeIf { it.isNotEmpty() }
                val body = line.substring(separator + SPEAKER_SEPARATOR.length)
                ChatMessage(speaker = speaker, text = body, timestampMillis = null)
            } else {
                ChatMessage(speaker = null, text = line, timestampMillis = null)
            }
        }
}
