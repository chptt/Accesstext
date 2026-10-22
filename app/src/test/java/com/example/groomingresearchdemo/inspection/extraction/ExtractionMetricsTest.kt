package com.example.groomingresearchdemo.inspection.extraction

import com.example.groomingresearchdemo.inspection.FakeNode
import com.example.groomingresearchdemo.inspection.NodeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The measurement half of the extraction stage: every metric the task lists, computed over annotated
 * synthetic screens with its denominator stated in the same expression.
 *
 * WHY THE DENOMINATOR TRAVELS WITH THE NUMBER
 * "Precision 1.0" and "precision: nothing was measured" look identical in a table and mean opposite
 * things. Every metric below is therefore a ratio over a named denominator, or it is reported as
 * having no denominator at all. A genuine zero — the pipeline looked for timestamps and turned none
 * into a record field — is written `0/1`, never as `not measured`, and never the other way round.
 *
 * THESE NUMBERS ARE SYNTHETIC-ONLY. They describe how the rules behave on trees built for this file.
 * They are not accuracy on Instagram, on Google Messages, or on any real conversation, and no real
 * conversation was used to produce them. The device side of the claim lives in
 * docs/13_MESSAGE_RECONSTRUCTION_VALIDATION_REPORT.md, where the same metrics read NOT MEASURED
 * because no consented, annotated real sample exists yet.
 */
class ExtractionMetricsTest {

    /** A ratio that was actually computed, with what it was computed over. */
    data class Ratio(val numerator: Int, val denominator: Int) {
        /** Null when there was nothing to divide by — the `not measured` case, kept distinct. */
        val value: Double? get() = if (denominator == 0) null else numerator.toDouble() / denominator
        override fun toString(): String = "$numerator/$denominator"
    }

    private data class Annotation(
        val id: String,
        /** The conversation turns a human put on this screen, in reading order. */
        val turns: List<String>,
        /** Non-message interface text a human identified, for the false-positive denominator. */
        val interfaceLabels: List<String> = emptyList(),
        /** How many nodes render a timestamp. The denominator for timestamp identification. */
        val timestampNodes: Int = 0,
        /** Which speaker a human says typed each turn, where that is knowable at all. */
        val speakers: Map<String, String> = emptyMap(),
        val root: FakeNode,
    )

    private data class Matched(
        val turnIndices: List<Int>,
        val placed: List<MessageRecord>,
    ) {
        val count: Int get() = turnIndices.size
    }

    private fun bubbleScreen(vararg texts: Pair<String, Int>): FakeNode {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = FakeNode.viewport())
        texts.forEach { (text, top) -> list.addChild(FakeNode.messageRow(text, top = top)) }
        return FakeNode(classValue = "android.widget.FrameLayout", boundsValue = FakeNode.viewport()).addChild(list)
    }

    private fun labelledTurn(top: Int, text: String, nodeId: Long, visible: Boolean? = true) =
        FakeNode.messageRow(text, top = top, nodeId = nodeId, visible = visible)

    private val screens: List<Annotation> by lazy {
        val multi = "first synthetic line\nsecond synthetic line\nthird synthetic line"

        // S6 is the shape the sender-label rule claims to recognise: a short label node above the
        // turns it belongs to. Its visibility is unknown, which makes it MEDIUM rather than HIGH —
        // and the rule only ever fires on non-HIGH records.
        val labelRow = FakeNode(classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(0, 80, 1080, 480), nodeIdValue = 5)
        labelRow.addChild(
            FakeNode(
                textValue = "Synthetic Name One",
                classValue = "android.widget.TextView",
                boundsValue = NodeBounds(80, 100, 400, 160),
                nodeIdValue = 6,
            ),
        )
        labelRow.addChild(labelledTurn(200, "alpha synthetic turn", 7))
        labelRow.addChild(labelledTurn(300, "beta synthetic turn", 8))
        val labelScreen = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = FakeNode.viewport())
            .addChild(labelRow)

        // S4 is one row holding a name, a body and a rendered stamp in the same vertical band.
        val crowded = FakeNode(classValue = "android.widget.LinearLayout", boundsValue = NodeBounds(0, 100, 1080, 190))
        crowded.addChild(FakeNode(textValue = "Synthetic Name Two", classValue = "android.widget.TextView", boundsValue = NodeBounds(80, 100, 400, 160)))
        crowded.addChild(FakeNode(textValue = "gamma synthetic turn", classValue = "android.widget.TextView", boundsValue = NodeBounds(420, 100, 900, 160)))
        crowded.addChild(FakeNode(textValue = "10:45 synthetic stamp", classValue = "android.widget.TextView", boundsValue = NodeBounds(920, 100, 1060, 160)))

        listOf(
            Annotation(
                id = "S1",
                turns = listOf("alpha synthetic turn", "beta synthetic turn", "gamma synthetic turn", "delta synthetic turn"),
                root = bubbleScreen(
                    "alpha synthetic turn" to 100,
                    "beta synthetic turn" to 200,
                    "gamma synthetic turn" to 300,
                    "delta synthetic turn" to 400,
                ),
            ),
            Annotation(
                id = "S2",
                turns = listOf("alpha synthetic turn", "beta synthetic turn", "gamma synthetic turn"),
                interfaceLabels = listOf("Type a message", "Send"),
                root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = FakeNode.viewport())
                    .addChild(FakeNode.messageRow("alpha synthetic turn", top = 100))
                    .addChild(FakeNode.messageRow("beta synthetic turn", top = 200))
                    .addChild(FakeNode.messageRow("gamma synthetic turn", top = 300))
                    .addChild(FakeNode(textValue = "Type a message", classValue = "android.widget.EditText", boundsValue = NodeBounds(80, 2300, 900, 2380)))
                    .addChild(FakeNode(textValue = "Send", classValue = "android.widget.Button", boundsValue = NodeBounds(940, 2300, 1060, 2380))),
            ),
            Annotation(
                id = "S3",
                turns = listOf(multi, "same synthetic turn", "same synthetic turn"),
                root = bubbleScreen(multi to 100, "same synthetic turn" to 300, "same synthetic turn" to 500),
            ),
            Annotation(
                id = "S4",
                turns = listOf("gamma synthetic turn"),
                timestampNodes = 1,
                speakers = mapOf("gamma synthetic turn" to "Synthetic Name Two"),
                root = crowded,
            ),
            Annotation(
                id = "S5",
                turns = emptyList(),
                interfaceLabels = listOf("Search", "More options", "Type a message", "Send"),
                root = FakeNode(classValue = "android.widget.FrameLayout", boundsValue = FakeNode.viewport())
                    .addChild(FakeNode(textValue = "Search", classValue = "android.widget.TextView", boundsValue = NodeBounds(200, 40, 800, 100)))
                    .addChild(FakeNode(textValue = "More options", classValue = "android.widget.TextView", boundsValue = NodeBounds(900, 40, 1060, 100)))
                    .addChild(FakeNode(textValue = "Type a message", classValue = "android.widget.EditText", boundsValue = NodeBounds(80, 2300, 900, 2380)))
                    .addChild(FakeNode(textValue = "Send", classValue = "android.widget.Button", boundsValue = NodeBounds(940, 2300, 1060, 2380))),
            ),
            Annotation(
                id = "S6",
                turns = listOf("alpha synthetic turn", "beta synthetic turn"),
                speakers = mapOf("alpha synthetic turn" to "Synthetic Name One", "beta synthetic turn" to "Synthetic Name One"),
                root = labelScreen,
            ),
        )
    }

    private fun extract(screen: Annotation, extractor: MessageLevelExtractor = MessageLevelExtractor()) =
        extractor.extract(screen.root, FakeNode.viewport(), screen.id)

    /** Verbatim matching of placed records against the annotated turns, in placement order. */
    private fun match(screen: Annotation, result: Reconstruction): Matched {
        val used = HashSet<Int>()
        val indices = mutableListOf<Int>()
        val placed = mutableListOf<MessageRecord>()
        result.records.filter { it.isUsable }.sortedBy { it.visibleOrderIndex }.forEach { record ->
            val index = screen.turns.indices.firstOrNull { it !in used && screen.turns[it] == record.text }
            if (index != null) {
                used.add(index)
                indices += index
                placed += record
            }
        }
        return Matched(indices, placed)
    }

    @Test
    fun candidateMessagePrecisionAndRecallAreComputedOverTheAnnotatedTurns() {
        val results = screens.map { it to extract(it) }
        val accepted = results.sumOf { it.second.acceptedCount }
        val matched = results.sumOf { match(it.first, it.second).count }
        val annotated = screens.sumOf { it.turns.size }

        // Per screen: S1 4/4, S2 3/3, S3 3/3, S4 0 accepted, S5 0 accepted, S6 2 of 3.
        // The S6 miss is the sender label being kept as a message — the cost the label rule pays for.
        assertEquals(13, accepted)
        assertEquals(13, annotated)
        assertEquals(Ratio(12, 13), Ratio(matched, accepted))
        assertEquals(Ratio(12, 13), Ratio(matched, annotated))
    }

    @Test
    fun messageReconstructionPrecisionIsOnlyDefinedWhereRecordsWerePlaced() {
        val results = screens.map { it to extract(it) }
        val placed = results.sumOf { it.second.records.count { record -> record.visibleOrderIndex != null } }
        val placedAndCorrect = results.sumOf { match(it.first, it.second).count }
        val s4 = extract(screens.first { it.id == "S4" })

        assertEquals(Ratio(12, 13), Ratio(placedAndCorrect, placed))
        // S4 placed nothing, so reconstruction precision has no denominator on that screen.
        assertEquals(0, s4.acceptedCount)
        assertEquals(0, s4.records.count { it.visibleOrderIndex != null })
    }

    @Test
    fun exactTextMatchRateCountsOnlyVerbatimAgreement() {
        val verbatim = screens.map { it to extract(it) }.sumOf { (screen, result) ->
            result.records.count { it.isUsable && screen.turns.contains(it.text) }
        }
        val annotated = screens.sumOf { it.turns.size }

        // Nothing was rewritten, normalised or case-folded, so verbatim agreement equals the match.
        assertEquals(Ratio(12, 13), Ratio(verbatim, annotated))
    }

    @Test
    fun messageOrderAccuracyIsMeasuredOverConsecutiveAnnotatedPairsThatWereBothPlaced() {
        var correct = 0
        var pairs = 0
        screens.forEach { screen ->
            val matched = match(screen, extract(screen))
            matched.turnIndices.zipWithNext().forEach { (earlier, later) ->
                pairs++
                if (earlier < later) correct++
            }
        }

        // S1 3 pairs, S2 2, S3 2, S6 1; S4 and S5 place no pair at all.
        assertEquals(8, pairs)
        assertEquals(Ratio(8, 8), Ratio(correct, pairs))
    }

    @Test
    fun speakerAttributionIsNotMeasuredWhenNoRuleWasEnabled() {
        val results = screens.map { it to extract(it) }
        val attempted = results.count { it.second.speakerAttribution != SpeakerAttribution.NOT_ATTEMPTED }
        val annotatedWithSpeaker = screens.sumOf { it.speakers.size }

        // The denominator is real (3 turns have a known speaker), but nothing was attempted. Printing
        // 0/3 would report a failed measurement instead of an unmade one, so the report says
        // NOT MEASURED and states why.
        assertEquals(0, attempted)
        assertEquals(3, annotatedWithSpeaker)
        assertTrue(results.all { (_, result) -> result.records.none { it.speakerLabel != null } })
    }

    @Test
    fun speakerAttributionBecomesMeasurableOnceTheLabelRuleIsEnabled() {
        val screen = screens.first { it.id == "S6" }
        val result = extract(screen, MessageLevelExtractor(enableSpeakerLabelRule = true))
        val attributed = result.records.count { it.speakerLabel != null }
        val correct = result.records.count { it.speakerLabel != null && screen.speakers[it.text] == it.speakerLabel }

        assertEquals(SpeakerAttribution.NODE_LABEL_EVIDENCE, result.speakerAttribution)
        assertEquals(Ratio(2, 2), Ratio(correct, attributed))
        // The label node itself stops being a turn, which is why 12/13 precision becomes 12/12 here.
        assertEquals(2, result.acceptedCount)
    }

    @Test
    fun timestampIdentificationIsAGenuineZeroRatherThanAnAbsentMetric() {
        val screen = screens.first { it.id == "S4" }
        val result = extract(screen)

        // A rendered timestamp really is on this screen, so the denominator exists and the numerator
        // is honestly zero: the pipeline never turns a clock glyph into a record field.
        assertEquals(Ratio(0, screen.timestampNodes), Ratio(result.records.count { it.timestampMillis != null }, screen.timestampNodes))
        assertEquals(1, screen.timestampNodes)
        assertFalse(result.timestampsRecovered)
    }

    @Test
    fun ambiguousResultRateIsPerScreenOverTheWholePool() {
        val results = screens.map { extract(it) }
        val ambiguousScreens = results.count { it.ambiguousCount > 0 }

        assertEquals(1, ambiguousScreens)
        assertEquals(Ratio(1, 6), Ratio(ambiguousScreens, results.size))
    }

    @Test
    fun falsePositiveRateOnInterfaceTextIsMeasuredOverLabelledChromeOnly() {
        val chromeScreens = screens.filter { it.interfaceLabels.isNotEmpty() }
        val labels = chromeScreens.sumOf { it.interfaceLabels.size }
        val promoted = chromeScreens.sumOf { screen ->
            extract(screen).records.count { it.isUsable && screen.interfaceLabels.contains(it.text) }
        }

        assertEquals(6, labels)
        assertEquals(Ratio(0, 6), Ratio(promoted, labels))
    }

    @Test
    fun anUnplaceableScreenIsReportedAsIncompleteRatherThanAsAnEmptyConversation() {
        val result = extract(screens.first { it.id == "S4" })

        assertEquals(ReconstructionStatus.INCOMPLETE, result.status)
        assertEquals(3, result.ambiguousCount)
        assertTrue(result.reasons.any { it.contains("Boundaries could not be established") })
    }

    @Test
    fun aScreenWithNoAnnotatedTurnsHasNoRecallDenominator() {
        val screen = screens.first { it.id == "S5" }

        // Zero annotated turns and zero accepted records: recall on this screen is undefined, and the
        // Ratio type says so instead of inventing 0.0.
        assertEquals(0, screen.turns.size)
        assertEquals(0, extract(screen).acceptedCount)
        assertNull(Ratio(0, screen.turns.size).value)
    }
}
