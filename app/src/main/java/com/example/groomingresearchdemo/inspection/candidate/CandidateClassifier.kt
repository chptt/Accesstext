package com.example.groomingresearchdemo.inspection.candidate

/**
 * Rule-based candidate classifier. Transparent, deterministic, Android-free.
 *
 * WHY RULES AND NOT A MODEL
 * The spec is explicit that a classifier must be understood before it is trusted, and there is no
 * labelled message-boundary dataset in this project to train on. So every decision below is a named
 * rule that can be read, argued with, and unit-tested against a synthetic tree. A panel can ask why
 * one node became a candidate and the answer is a list of tokens, not a weight.
 *
 * WHAT THESE RULES ARE NOT
 * They do not prove a node is a message. A TextView holding "Active now" satisfies more of them
 * than a two-line message does. Every ACCEPTED record is therefore still only *candidate* evidence
 * for a reconstruction that the validation report must measure against ground truth before anyone
 * calls it a transcript.
 *
 * NO APP-SPECIAL CASES, ON PURPOSE
 * There is not one branch here keyed on a package name, a resource-name view id, or a string that
 * belongs to a particular product. The class names are Android SDK class names, which every app on
 * the device shares, and the phrase list below is generic platform vocabulary ("Search", "Send",
 * "Type a message") that the OS-level apps use too. That is the whole reason it is isolated in one
 * val with a documented failure mode: if this list is ever wrong for an app, the fix is to add a
 * rule and a test, not to special-case `com.whatsapp`.
 */
object CandidateClassifier {

    /**
     * Widget classes that hold interface chrome rather than conversational content.
     *
     * Kept as *substrings of Android class names* because apps subclass freely
     * (`androidx.appcompat.widget.AppCompatTextView` etc.), and because a strict equality test
     * silently stops matching the moment an app wraps a control.
     */
    private val CONTROL_CLASS_MARKERS = listOf(
        "Button",          // send, attach, back — never a message body
        "ImageButton",
        "EditText",        // the composer the researcher types into, not a received message
        "AutoCompleteTextView",
        "CheckBox",
        "RadioButton",
        "Switch",
        "ToggleButton",
        "SeekBar",
        "ProgressBar",     // loading indicators
        "Spinner",
        "Menu",
        "MenuItem",
        "Toolbar",
        "TabItem",
        "TabWidget",
        "TabLayout",
        "BottomNavigation",
        "SearchView",
        "SlidingPaneLayout",
        "ViewPager",
    )

    /**
     * Classes that positively suggest readable content rather than a control.
     * Absence from this list is not disqualifying — a custom-drawn message bubble will not appear
     * here, which is exactly why such a node lands in AMBIGUOUS and not REJECTED.
     */
    private val CONTENT_CLASS_MARKERS = listOf(
        "TextView",
        "Text",
    )

    /**
     * Generic, cross-app interface phrases. Comparison is on the trimmed, lower-cased whole node.
     *
     * FAILURE MODE, STATED SO IT IS NOT FORGOTTEN: a real message whose entire body happens to be
     * one of these phrases is discarded. Short conversational replies are deliberately NOT listed —
     * "ok", "yeah", "k" are things people actually say, and excluding them would delete real turns to
     * save a false positive. Each entry here is a control label a human would not call conversational
     * content, and the false-negative cost of the list is measured in the validation report rather
     * than assumed away.
     */
    private val CHROME_PHRASES = setOf(
        "search", "send", "post", "reply", "share", "more options", "menu", "back", "close",
        "cancel", "done", "next", "skip",
        "type a message", "type a message…", "write a message", "message", "start a chat",
        "send a message", "home", "search instagram", "explore", "reels", "shop", "profile",
        "activity", "settings", "delete", "block", "report", "mute", "unmute", "mark as unread",
        "mark all as read", "unread", "active now", "typing", "online", "last seen",
        "voice call", "video call", "info", "attachments", "camera", "gallery", "mic",
    )

    /**
     * Status-row shapes: a receipt/state marker, optionally trailed by a time.
     *
     * Matched as a whole phrase or a prefix of one, never as a substring, so "did you read it"
     * survives as message content while "read 10:45" does not. Substring matching here would
     * silently delete every short message containing a status word, and on a real chat screen those
     * words are common in ordinary sentences.
     */
    private val HEADER_PHRASE_MARKERS = listOf(
        "active now", "last seen", "typing", "online", "seen", "delivered", "read",
    )

    /** A single-character node is almost always an icon glyph or a bullet, not an utterance. */
    private const val MIN_PLAUSIBLE_MESSAGE_CHARS = 2

    /** Beyond this, a "message" is really a screen-filling blob — usually a container echo. */
    private const val MAX_PLAUSIBLE_MESSAGE_CHARS = 4000

    /**
     * Classify one sampled node.
     *
     * @param inSameRowOtherCandidate true when another text-bearing node shares this node's vertical
     *   band and its parent, which is the shape of a row that carries a label and a body. Two
     *   candidates in one row is a reason to be unsure, not a reason to merge them.
     */
    fun classify(
        sample: NodeSample,
        inSameRowOtherCandidate: Boolean = false,
    ): CandidateJudgement {
        val evidence = mutableListOf<String>()
        val classLabel = sample.className ?: "<unknown-class>"

        val text = sample.text
        if (text == null || text.isBlank()) {
            return CandidateJudgement(
                decision = CandidateDecision.REJECTED,
                strength = EvidenceStrength.LOW,
                evidence = listOf("no-text-value"),
                objection = if (sample.hasContentDescription) {
                    "description-only-node"
                } else {
                    "empty-or-unavailable-text"
                },
            )
        }

        evidence += "text-present"

        if (sample.hasContentDescription) evidence += "also-content-described"

        if (isControlClass(classLabel)) {
            return CandidateJudgement(
                decision = CandidateDecision.REJECTED,
                strength = EvidenceStrength.LOW,
                evidence = evidence + "class=$classLabel",
                objection = "control-class",
            )
        }
        evidence += "not-control-class"

        if (sample.aggregatesDescendantText) {
            return CandidateJudgement(
                decision = CandidateDecision.REJECTED,
                strength = EvidenceStrength.LOW,
                evidence = evidence + listOf("depth=${sample.depth}", "child-count=${sample.childCount}"),
                objection = "container-echoes-descendant-text",
            )
        }

        val trimmedLower = text.trim().lowercase()
        if (trimmedLower in CHROME_PHRASES) {
            return CandidateJudgement(
                decision = CandidateDecision.REJECTED,
                strength = EvidenceStrength.LOW,
                evidence = evidence,
                objection = "chrome-vocabulary",
            )
        }

        val headerShaped = text.trim().length <= 28 && HEADER_PHRASE_MARKERS.any { marker ->
            trimmedLower == marker || trimmedLower.startsWith("$marker ")
        }
        if (headerShaped) {
            return CandidateJudgement(
                decision = CandidateDecision.REJECTED,
                strength = EvidenceStrength.LOW,
                evidence = evidence,
                objection = "status-or-header-phrase",
            )
        }

        val visible = sample.isVisibleToUser
        if (visible == false) {
            return CandidateJudgement(
                decision = CandidateDecision.REJECTED,
                strength = EvidenceStrength.LOW,
                evidence = evidence,
                objection = "not-visible-to-user",
            )
        }
        if (visible == true) evidence += "visible-confirmed"

        if (text.trim().length < MIN_PLAUSIBLE_MESSAGE_CHARS) {
            return CandidateJudgement(
                decision = CandidateDecision.AMBIGUOUS,
                strength = EvidenceStrength.LOW,
                evidence = evidence,
                objection = "below-minimum-message-length",
            )
        }
        if (text.length > MAX_PLAUSIBLE_MESSAGE_CHARS) {
            return CandidateJudgement(
                decision = CandidateDecision.AMBIGUOUS,
                strength = EvidenceStrength.LOW,
                evidence = evidence,
                objection = "above-maximum-message-length",
            )
        }

        if (sample.isLeaf) evidence += "leaf-node"
        if (sample.hasUsableBounds) {
            evidence += "valid-bounds"
        } else {
            evidence += "geometry-missing"
        }
        val contentShaped = CONTENT_CLASS_MARKERS.any { classLabel.contains(it, ignoreCase = true) }
        if (contentShaped) evidence += "text-shaped-class"

        if (inSameRowOtherCandidate) {
            return CandidateJudgement(
                decision = CandidateDecision.AMBIGUOUS,
                strength = EvidenceStrength.LOW,
                evidence = evidence,
                objection = "multiple-text-nodes-in-one-row",
            )
        }

        val roleUnresolved = !(sample.isLeaf || contentShaped)
        val strength = when {
            !sample.hasUsableBounds -> EvidenceStrength.LOW
            sample.isLeaf && contentShaped && visible == true -> EvidenceStrength.HIGH
            !roleUnresolved -> EvidenceStrength.MEDIUM
            else -> EvidenceStrength.LOW
        }

        // WHY MISSING GEOMETRY DOES NOT MAKE A NODE AMBIGUOUS HERE
        // This classifier answers "is this node message-shaped?". It does not answer "where does it
        // go?", because placement is the reconstructor's decision and the reconstructor has the whole
        // tree. A text node with no rectangle is still message-shaped; it is simply unplaceable, and
        // the reconstructor turns the entire run into CANDIDATE records with an INCOMPLETE verdict
        // when that happens. Calling it AMBIGUOUS here would bury an ordering problem among
        // same-row collisions and make the stated reason for the run's incompleteness wrong.
        // The certainty still only goes down: a geometry-less candidate can never reach ACCEPTED.
        return CandidateJudgement(
            decision = if (roleUnresolved) CandidateDecision.AMBIGUOUS else CandidateDecision.CANDIDATE,
            strength = strength,
            evidence = evidence,
            objection = if (roleUnresolved) "role-unresolved" else null,
        )
    }

    /**
     * Public because the validation report needs to state which rule fired without re-deriving it,
     * and because a reviewer must be able to check that a rejected node was rejected by a *named*
     * rule rather than by silence.
     */
    fun isControlClass(className: String): Boolean =
        CONTROL_CLASS_MARKERS.any { className.contains(it, ignoreCase = true) }
}
