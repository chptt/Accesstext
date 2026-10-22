package com.example.groomingresearchdemo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the authorized-import parser. Its contract is mostly about what it refuses to invent:
 * no fake speaker for an unprefixed line, and never a fabricated timestamp.
 */
class AuthorizedTextParserTest {

    @Test
    fun speakerPrefixIsSplitOnTheFirstColonSpace() {
        val parsed = AuthorizedTextParser.parse("Asha: are you there?")
        assertEquals(1, parsed.size)
        assertEquals("Asha", parsed[0].speaker)
        assertEquals("are you there?", parsed[0].text)
    }

    @Test
    fun onlyTheFirstSeparatorSplits() {
        val parsed = AuthorizedTextParser.parse("Asha: note: meeting at 5")
        assertEquals("Asha", parsed[0].speaker)
        assertEquals("note: meeting at 5", parsed[0].text)
    }

    @Test
    fun lineWithoutASeparatorHasNoSpeaker() {
        val parsed = AuthorizedTextParser.parse("just a message")
        assertNull(parsed[0].speaker)
        assertEquals("just a message", parsed[0].text)
    }

    @Test
    fun colonWithoutSpaceIsNotASpeakerSeparator() {
        // "12:30 ok" is a time mention in a body, not a speaker label.
        val parsed = AuthorizedTextParser.parse("12:30 ok")
        assertNull(parsed[0].speaker)
        assertEquals("12:30 ok", parsed[0].text)
    }

    @Test
    fun timestampsAreNeverInvented() {
        AuthorizedTextParser.parse("Asha: hi\nRavi: hello").forEach {
            assertNull(it.timestampMillis)
        }
    }

    @Test
    fun orderIsTheSuppliedOrder() {
        val parsed = AuthorizedTextParser.parse("A: one\nB: two\nA: three")
        assertEquals(listOf("one", "two", "three"), parsed.map { it.text })
    }

    @Test
    fun blankLinesSurviveParsingAndAreDroppedByCleaning() {
        val parsed = AuthorizedTextParser.parse("A: one\n\n   \nB: two")
        assertEquals(4, parsed.size)
        val cleaned = ConversationPreprocessor.cleanMessages(parsed)
        assertEquals(2, cleaned.size)
        assertEquals(listOf("one", "two"), cleaned.map { it.text })
    }

    @Test
    fun unicodeAndEmojiSurviveTheWholeImportPath() {
        val raw = "Asha: സുഖമാണോ? 🙂\nRavi: i am fine"
        val modelInput = ConversationPreprocessor.preprocess(AuthorizedTextParser.parse(raw))
        assertEquals("Asha: സുഖമാണോ? 🙂\nRavi: i am fine", modelInput)
    }

    @Test
    fun emptyImportProducesEmptyModelInput() {
        val validation = ConversationPreprocessor.validateModelInput(AuthorizedTextParser.parse(""))
        assertTrue(validation.ok)
        assertEquals(0, validation.cleanedMessageCount)
        assertEquals("", validation.modelInput)
    }
}
