package com.example.groomingresearchdemo.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers spec test items 10–17 for the preprocessing pipeline. */
class ConversationPreprocessorTest {

    private val p = ConversationPreprocessor

    @Test
    fun whitespaceNormalizationCollapsesAndTrims() {
        // Test 10.
        assertEquals("a b c", p.normalizeWhitespace("  a   b\n\n c  "))
        assertEquals("single", p.normalizeWhitespace("single"))
        assertEquals("", p.normalizeWhitespace("      "))
        assertEquals("", p.normalizeWhitespace(null))
    }

    @Test
    fun blankMessagesAreRemoved() {
        // Test 11.
        val input = listOf(
            ChatMessage("A", "", 1L),
            ChatMessage("B", "   ", 2L),
            ChatMessage("A", null, 3L),
            ChatMessage("B", "keep me", 4L),
        )
        val cleaned = p.cleanMessages(input)
        assertEquals(1, cleaned.size)
        assertEquals("keep me", cleaned.single().text)
    }

    @Test
    fun legitimateRepeatedMessagesArePreserved() {
        // Test 12: deduplication would erase real conversational signal, so repeats survive.
        val input = listOf(
            ChatMessage("A", "ok", 1L),
            ChatMessage("B", "ok", 2L),
            ChatMessage("A", "ok", 3L),
        )
        val cleaned = p.cleanMessages(input)
        assertEquals(3, cleaned.size)
        cleaned.forEach { assertEquals("ok", it.text) }
    }

    @Test
    fun malayalamAndEmojiArePreservedExactly() {
        // Test 13.
        val malayalam = "സുഖമാണോ? how are you?"
        val emoji = "Happy birthday 🙂🎉 ❤️"
        assertEquals(malayalam, p.normalizeWhitespace(malayalam))
        assertEquals(emoji, p.normalizeWhitespace(emoji))

        val msgs = listOf(
            ChatMessage("A", malayalam, 1L),
            ChatMessage("B", emoji, 2L),
        )
        val input = p.toModelInput(msgs)
        assertTrue(input.contains(malayalam))
        assertTrue(input.contains(emoji))
    }

    @Test
    fun punctuationIsPreserved() {
        val withPunct = "Wait... really?! Yes, it's 3:30."
        assertEquals(withPunct, p.normalizeWhitespace(withPunct))
    }

    @Test
    fun chronologicalOrderIsRepairedWhenAllTimestampsPresent() {
        // Test 14.
        val shuffled = listOf(
            ChatMessage("A", "third", 3L),
            ChatMessage("B", "first", 1L),
            ChatMessage("A", "second", 2L),
        )
        val ordered = p.preserveMessageOrder(shuffled)
        assertEquals(listOf("first", "second", "third"), ordered.map { it.text })
    }

    @Test
    fun orderIsUntouchedWhenAnyTimestampMissing() {
        // No invented ordering: if a timestamp is absent we keep the supplied order.
        val mixed = listOf(
            ChatMessage("A", "one", 5L),
            ChatMessage("B", "two", null),
            ChatMessage("A", "three", 1L),
        )
        val ordered = p.preserveMessageOrder(mixed)
        assertEquals(listOf("one", "two", "three"), ordered.map { it.text })
    }

    @Test
    fun missingSpeakerProducesUnlabeledLine() {
        // Test 15.
        val msgs = listOf(
            ChatMessage(null, "no speaker here", 1L),
            ChatMessage("A", "with speaker", 2L),
        )
        val input = p.toModelInput(msgs)
        val lines = input.split("\n")
        assertEquals("no speaker here", lines[0])
        assertEquals("A: with speaker", lines[1])
    }

    @Test
    fun missingTimestampIsNotFabricated() {
        // Test 16: cleaning must not invent a timestamp value.
        val msg = ChatMessage("A", "hello", null)
        val cleaned = p.cleanMessages(listOf(msg)).single()
        assertEquals(null, cleaned.timestampMillis)
    }

    @Test
    fun modelInputGenerationIsDeterministic() {
        // Test 17: same input -> identical output on repeated runs.
        val sample = SampleConversations.byId("malayalam")!!.messages
        val first = p.preprocess(sample)
        val second = p.preprocess(sample)
        assertEquals(first, second)
    }

    @Test
    fun lineCountMatchesCleanedCount() {
        val validation = p.validateModelInput(SampleConversations.byId("empty")!!.messages)
        assertTrue("expected ok, got ${validation.problems}", validation.ok)
        val lines = if (validation.modelInput.isEmpty()) 0 else validation.modelInput.count { it == '\n' } + 1
        assertEquals(validation.cleanedMessageCount, lines)
    }

    @Test
    fun validateRejectsNothingValidButDetectsConsistency() {
        val ok = p.validateModelInput(SampleConversations.byId("normal")!!.messages)
        assertTrue(ok.ok)
        assertFalse(ok.modelInput.isBlank())
        assertTrue(ok.problems.isEmpty())
    }

    @Test
    fun allSamplesValidateCleanly() {
        // Guardrail across the whole synthetic corpus.
        SampleConversations.ALL.forEach { sample ->
            val v = p.validateModelInput(sample.messages)
            assertTrue("sample ${sample.id}: ${v.problems}", v.ok)
        }
    }
}
