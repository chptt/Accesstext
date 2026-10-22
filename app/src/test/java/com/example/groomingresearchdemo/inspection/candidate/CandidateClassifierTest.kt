package com.example.groomingresearchdemo.inspection.candidate

import com.example.groomingresearchdemo.inspection.NodeBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rule-based classifier, tested rule by rule.
 *
 * The direction every one of these assertions is checking: a rule may only ever make the pipeline
 * LESS certain, never more. A node the rules cannot place must land in AMBIGUOUS and stay visible,
 * because a silently-wrong REJECTED is a missing message and a silently-wrong CANDIDATE is an
 * invented one.
 */
class CandidateClassifierTest {

    private fun sample(
        text: String?,
        className: String? = "android.widget.TextView",
        bounds: NodeBounds? = NodeBounds(80, 100, 900, 160),
        visible: Boolean? = true,
        leaf: Boolean = true,
        desc: Boolean = false,
        aggregates: Boolean = false,
        depth: Int = 3,
    ) = NodeSample(
        nodeId = 1L,
        parentNodeId = 2L,
        depth = depth,
        className = className,
        text = text,
        hasText = text != null,
        hasContentDescription = desc,
        isClickable = false,
        isScrollable = false,
        isVisibleToUser = visible,
        bounds = bounds,
        childCount = if (leaf) 0 else 2,
        aggregatesDescendantText = aggregates,
    )

    @Test
    fun aPlainLeafBubbleIsACandidateWithStructuralEvidenceOnly() {
        val judgement = CandidateClassifier.classify(sample("the first synthetic message line"))

        assertEquals(CandidateDecision.CANDIDATE, judgement.decision)
        assertEquals(EvidenceStrength.HIGH, judgement.strength)
        assertTrue(judgement.evidence.containsAll(listOf("text-present", "not-control-class", "leaf-node", "valid-bounds")))
        assertNull(judgement.objection)
    }

    @Test
    fun noEvidenceTokenEverQuotesTheNodesText() {
        val marker = "PRIVATESAMPLESTRING-9d3a"
        val judgement = CandidateClassifier.classify(sample(marker))

        assertTrue(judgement.evidence.none { it.contains(marker) })
        assertFalse(judgement.objection?.contains(marker) == true)
    }

    @Test
    fun absentEmptyAndBlankTextAreRejectedApart() {
        val absent = CandidateClassifier.classify(sample(null, desc = true))
        assertEquals("description-only-node", absent.objection)
        assertEquals(CandidateDecision.REJECTED, absent.decision)

        val empty = CandidateClassifier.classify(sample(""))
        assertEquals("empty-or-unavailable-text", empty.objection)
    }

    @Test
    fun controlClassesNeverBecomeCandidates() {
        for (className in listOf("android.widget.Button", "android.widget.EditText", "android.widget.ImageButton")) {
            val judgement = CandidateClassifier.classify(sample("a control holding text", className = className))
            assertEquals("class $className", CandidateDecision.REJECTED, judgement.decision)
            assertEquals("control-class", judgement.objection)
        }
        assertTrue(CandidateClassifier.isControlClass("androidx.appcompat.widget.AppCompatEditText"))
    }

    @Test
    fun aContainerThatEchoesItsChildTextIsSuppressedButAWrapperIsNot() {
        val echo = CandidateClassifier.classify(sample("shared string", leaf = false, aggregates = true))
        assertEquals("container-echoes-descendant-text", echo.objection)

        // A non-leaf node whose text is its own is still message-shaped; child-hood alone is not an objection.
        val wrapper = CandidateClassifier.classify(sample("shared string", leaf = false, aggregates = false))
        assertEquals(CandidateDecision.CANDIDATE, wrapper.decision)
    }

    @Test
    fun composerAndNavigationPhrasesAreRejectedAsChrome() {
        for (phrase in listOf("Type a message", "More options", "Active now", "Search")) {
            val judgement = CandidateClassifier.classify(sample(phrase))
            assertEquals("\"$phrase\"", CandidateDecision.REJECTED, judgement.decision)
        }
    }

    @Test
    fun aStatusWordInsideARealMessageDoesNotDeleteTheMessage() {
        // The regression this exists to prevent: substring matching on "read" would reject this line.
        val judgement = CandidateClassifier.classify(sample("did you read it yet"))

        assertEquals(CandidateDecision.CANDIDATE, judgement.decision)
        assertTrue(judgement.objection == null)
    }

    @Test
    fun receiptShapesAreRejectedAsHeaders() {
        for (phrase in listOf("Read 10:45", "delivered", "Last seen 2 hours ago")) {
            val judgement = CandidateClassifier.classify(sample(phrase))
            assertEquals("\"$phrase\"", CandidateDecision.REJECTED, judgement.decision)
            assertEquals("status-or-header-phrase", judgement.objection)
        }
    }

    @Test
    fun shortConversationalRepliesSurviveTheChromeList() {
        // "ok" is something people say. The chrome list holds control labels, not replies.
        val judgement = CandidateClassifier.classify(sample("ok"))

        assertEquals(CandidateDecision.CANDIDATE, judgement.decision)
        assertFalse(judgement.objection == "chrome-vocabulary")
    }

    @Test
    fun aOneCharacterNodeIsAmbiguousRatherThanDropped() {
        val judgement = CandidateClassifier.classify(sample("x"))

        assertEquals(CandidateDecision.AMBIGUOUS, judgement.decision)
        assertEquals("below-minimum-message-length", judgement.objection)
    }

    @Test
    fun invisibilityIsRejectedButUnknownVisibilityIsOnlyWeakened() {
        val hidden = CandidateClassifier.classify(sample("an offscreen synthetic line", visible = false))
        assertEquals("not-visible-to-user", hidden.objection)

        val unknown = CandidateClassifier.classify(sample("an offscreen synthetic line", visible = null))
        assertEquals(CandidateDecision.CANDIDATE, unknown.decision)
        assertFalse(unknown.evidence.contains("visible-confirmed"))
        assertEquals(EvidenceStrength.MEDIUM, unknown.strength)
    }

    @Test
    fun missingGeometryIsKeptAsACandidateButCannotRaiseConfidence() {
        // Placement is the reconstructor's decision, not this one. The certainty still only drops:
        // a geometry-less candidate can never become an ACCEPTED record.
        val judgement = CandidateClassifier.classify(sample("a line with no rectangle", bounds = null))

        assertEquals(CandidateDecision.CANDIDATE, judgement.decision)
        assertEquals(EvidenceStrength.LOW, judgement.strength)
        assertTrue(judgement.evidence.contains("geometry-missing"))
    }

    @Test
    fun twoTextNodesInOneRowMakeTheRowAmbiguousInsteadOfMergingIt() {
        val judgement = CandidateClassifier.classify(
            sample("a row body with another label beside it"),
            inSameRowOtherCandidate = true,
        )

        assertEquals(CandidateDecision.AMBIGUOUS, judgement.decision)
        assertEquals("multiple-text-nodes-in-one-row", judgement.objection)
    }

    @Test
    fun aCustomAppClassIsJudgedOnStructureNotOnItsPackageName() {
        // No branch in the classifier keys on a product: an app's own view class is judged by shape.
        val judgement = CandidateClassifier.classify(
            sample("a message drawn by an app-specific bubble", className = "com.shrug.bubble.CustomMessageText"),
        )

        assertEquals(CandidateDecision.CANDIDATE, judgement.decision)
        assertEquals(EvidenceStrength.HIGH, judgement.strength)
    }

    @Test
    fun anUnknownClassWithNoRoleSignalStaysAmbiguous() {
        val judgement = CandidateClassifier.classify(
            sample("text with no class and no children", className = null, leaf = false),
        )

        assertEquals(CandidateDecision.AMBIGUOUS, judgement.decision)
        assertEquals("role-unresolved", judgement.objection)
    }

    @Test
    fun anAbsurdlyLongNodeIsAmbiguousBecauseThatIsNotHowBubblesLook() {
        val judgement = CandidateClassifier.classify(sample("long".repeat(2000)))

        assertEquals(CandidateDecision.AMBIGUOUS, judgement.decision)
        assertEquals("above-maximum-message-length", judgement.objection)
    }
}
