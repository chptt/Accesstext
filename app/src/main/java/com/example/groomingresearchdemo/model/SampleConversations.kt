package com.example.groomingresearchdemo.model

/**
 * A labelled bundle of synthetic messages used to demonstrate the preprocessing pipeline.
 *
 * ALL content here is invented for the demonstration. Nothing in these samples came from
 * Instagram, a real messaging app, or any real conversation. The UI labels each sample as
 * SYNTHETIC TEST DATA and the docs reiterate that this laboratory does not prove anything
 * about real message extraction.
 */
data class SampleConversation(
    val id: String,
    val title: String,
    val description: String,
    val messages: List<ChatMessage>,
)

object SampleConversations {

    private const val T0 = 1_700_000_000_000L
    private const val MIN = 60_000L

    val ALL: List<SampleConversation> = listOf(
        SampleConversation(
            id = "normal",
            title = "Normal friendly conversation",
            description = "A short, ordinary exchange between two friends. Baseline case.",
            messages = listOf(
                ChatMessage("A", "Hey, did you finish the assignment?", T0),
                ChatMessage("B", "Almost, two problems left.", T0 + MIN),
                ChatMessage("A", "Want to sit together in the library tonight?", T0 + 2 * MIN),
                ChatMessage("B", "Yes, six pm works.", T0 + 3 * MIN),
            ),
        ),
        SampleConversation(
            id = "whitespace",
            title = "Repeated whitespace",
            description = "Extra spaces, tabs and newlines inside turns that must be collapsed to single spaces.",
            messages = listOf(
                ChatMessage("A", "hi   there", T0),
                ChatMessage("B", "hello\n\n\nhow  are\n   you", T0 + MIN),
                ChatMessage("A", "\tI   am   fine   ", T0 + 2 * MIN),
            ),
        ),
        SampleConversation(
            id = "emoji",
            title = "Emoji",
            description = "Emoji and variation selectors must survive normalization untouched.",
            messages = listOf(
                ChatMessage("A", "Happy birthday 🙂🎉", T0),
                ChatMessage("B", "Thank you ❤️", T0 + MIN),
                ChatMessage("A", "🙏 see you tomorrow", T0 + 2 * MIN),
            ),
        ),
        SampleConversation(
            id = "malayalam",
            title = "Malayalam and English mix",
            description = "Unicode Indic script mixed with English must pass through byte-for-byte.",
            messages = listOf(
                ChatMessage("A", "സുഖമാണോ? how are you?", T0),
                ChatMessage("B", "ഞാൻ സുഖത്തിലാണ്, നീ പറയൂ", T0 + MIN),
                ChatMessage("A", "ശരി, പിന്നെ സംസാരിക്കാം. talk later.", T0 + 2 * MIN),
            ),
        ),
        SampleConversation(
            id = "empty",
            title = "Empty messages",
            description = "Blank, whitespace-only and null turns. Preprocessing drops these and keeps the rest.",
            messages = listOf(
                ChatMessage("A", "", T0),
                ChatMessage("B", "   ", T0 + MIN),
                ChatMessage("A", null, T0 + 2 * MIN),
                ChatMessage("B", "Are you still there?", T0 + 3 * MIN),
                ChatMessage("A", "Yes.", T0 + 4 * MIN),
            ),
        ),
        SampleConversation(
            id = "duplicates",
            title = "Repeated legitimate messages",
            description = "Two speakers repeating the same word for real. Preprocessing must NOT deduplicate.",
            messages = listOf(
                ChatMessage("A", "ok", T0),
                ChatMessage("B", "ok", T0 + MIN),
                ChatMessage("A", "ok", T0 + 2 * MIN),
                ChatMessage("B", "let's go", T0 + 3 * MIN),
                ChatMessage("A", "let's go", T0 + 4 * MIN),
            ),
        ),
        SampleConversation(
            id = "order",
            title = "Out-of-order turns (chronological repair)",
            description = "Messages listed with shuffled timestamps. preserveMessageOrder must sort them by time.",
            messages = listOf(
                ChatMessage("A", "Third turn (timestamp later)", T0 + 2 * MIN),
                ChatMessage("B", "First turn (earliest timestamp)", T0),
                ChatMessage("A", "Fourth turn", T0 + 3 * MIN),
                ChatMessage("B", "Second turn", T0 + MIN),
            ),
        ),
    )

    fun byId(id: String): SampleConversation? = ALL.firstOrNull { it.id == id }
}
