package com.example.groomingresearchdemo.inspection

import com.example.groomingresearchdemo.AppConstants
import com.example.groomingresearchdemo.inspection.extraction.MessageLevelExtractor
import com.example.groomingresearchdemo.inspection.extraction.Reconstruction

/**
 * The one-shot state machine that governs every inspection.
 *
 * This class is deliberately free of any Android import. The AccessibilityService feeds it
 * events and the UI layer feeds it user actions, but the transitions themselves can be
 * exercised in a plain JUnit test.
 *
 * Guarantees encoded here:
 *   1. Arm requires consent AND an enabled service AND a chosen test app; each gate is checked
 *      separately so a missing one is reported distinctly rather than as a generic failure.
 *   2. The ARMED flag is consumed (set to EXECUTING) BEFORE the root tree is read, so an
 *      inspection is used exactly once and cannot be triggered twice by back-to-back events.
 *   3. The target package is captured at arm time. Changing the selected test app afterwards
 *      cannot redirect an inspection that is already waiting, and events from any other
 *      package are ignored WITHOUT consuming the arm.
 *   4. After an inspection produces a result, arm status returns to NOT_ARMED and stays there
 *      until the user presses Start again. No auto re-arm.
 *   5. Cancellation before an event clears the arm and records a CANCELLED outcome, without
 *      running any traversal.
 *   6. Consent revocation clears pending state AND local results.
 *
 * MESSAGE-LEVEL EXTRACTION, AND WHY IT RIDES THE SAME ARM
 * The extraction stage is not a second trigger. It is a second *pass over the same snapshot*, and
 * it only runs when the researcher ticked the extraction opt-in on the same deliberate Start press
 * that armed this inspection. There is no separate arm, no separate event path, and nothing that can
 * make the tree get walked when nobody asked. Both stages consume one `rootProvider()` call, so the
 * root is read exactly once per inspection either way.
 */
class InspectionController(
    private val inspector: StructuralInspector = StructuralInspector(),
    private val extractor: MessageLevelExtractor = MessageLevelExtractor(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    @Volatile
    var armStatus: ArmStatus = ArmStatus.NOT_ARMED
        private set

    /** The package this armed inspection will accept, or null when nothing is armed. */
    @Volatile
    var armedTarget: String? = null
        private set

    /**
     * Whether the armed inspection was requested WITH the message-level stage. Captured at arm time
     * for exactly the reason [armedTarget] is: changing the checkbox afterwards must not retroactively
     * widen an inspection the researcher already consented to.
     */
    @Volatile
    var armedExtraction: Boolean = false
        private set

    var lastResult: InspectionResult? = null
        private set

    /**
     * Bumped on every arm and every disarm. A caller that schedules work between the event and the
     * read (the service settles for a moment before opening the tree) captures this value and hands
     * it back; a stale value means the arm it belonged to is gone, so the read must not happen. This
     * is what stops a queued read from consuming a *new* arm that the researcher made in the meantime.
     */
    @Volatile
    var armToken: Long = 0L
        private set

    /**
     * Token for the current arm, but only if it is still waiting and matches [packageName]. Null
     * means "not armed for this package", so the caller should not schedule anything at all.
     */
    @Synchronized
    fun armTokenFor(packageName: String?): Long? {
        if (armStatus != ArmStatus.ARMED) return null
        if (packageName == null || packageName != armedTarget) return null
        return armToken
    }

    /**
     * Number of inspections that ran to a terminal outcome (COMPLETED or FAILED) in the current
     * app session. Cancelled attempts and never-attempted arms do not count. Reset on revoke.
     */
    var completedCount: Int = 0
        private set

    @Synchronized
    fun arm(
        consentAccepted: Boolean,
        serviceEnabled: Boolean,
        targetPackage: String?,
        messageLevelExtraction: Boolean = false,
    ): ArmResult {
        if (!consentAccepted) return ArmResult.CONSENT_REQUIRED
        if (!serviceEnabled) return ArmResult.SERVICE_REQUIRED
        val target = targetPackage?.trim()?.takeIf { it.isNotEmpty() } ?: return ArmResult.TARGET_REQUIRED
        if (armStatus == ArmStatus.ARMED || armStatus == ArmStatus.EXECUTING) {
            return ArmResult.ALREADY_ARMED
        }
        armedTarget = target
        armedExtraction = messageLevelExtraction
        armToken += 1
        armStatus = ArmStatus.ARMED
        return ArmResult.ARMED
    }

    @Synchronized
    fun cancel(): Boolean {
        if (armStatus != ArmStatus.ARMED) return false
        val result = InspectionResult(
            timestampMillis = clock(),
            targetPackage = armedTarget ?: AppConstants.DEFAULT_TARGET_PACKAGE,
            eventReceived = false,
            packageMatched = false,
            rootAvailable = false,
            stats = null,
            outcome = ExperimentOutcome.CANCELLED,
            failureReason = "Cancelled by the user before a qualifying accessibility event arrived.",
        )
        armStatus = ArmStatus.NOT_ARMED
        armedTarget = null
        armedExtraction = false
        armToken += 1
        lastResult = result
        return true
    }

    /**
     * Called by the AccessibilityService whenever the OS delivers an event.
     *
     * @param rootProvider a lazy supplier for the accessibility root. It is invoked ONLY when
     *   the controller has decided to actually inspect — meaning the tree is never even opened
     *   for an unarmed or non-target event.
     * @return the produced InspectionResult if this call consumed the armed state, else null.
     */
    @Synchronized
    fun handleEvent(
        packageName: String?,
        rootProvider: () -> AccessibilityNodeView?,
    ): InspectionResult? = handleEvent(packageName, null, rootProvider)

    /**
     * As above, but rejects the call when [expectedArmToken] belongs to an arm that has since been
     * cancelled, completed or replaced. The service reads the token when it first sees the event and
     * hands it back after settling, so a queued read cannot outlive the arm that asked for it.
     */
    @Synchronized
    fun handleEvent(
        packageName: String?,
        expectedArmToken: Long?,
        rootProvider: () -> AccessibilityNodeView?,
    ): InspectionResult? {
        val target = armedTarget
        if (armStatus != ArmStatus.ARMED || target == null) return null
        if (packageName != target) return null
        if (expectedArmToken != null && expectedArmToken != armToken) return null

        // Consume BEFORE inspection: a second event arriving during traversal cannot re-fire.
        armStatus = ArmStatus.EXECUTING
        val withExtraction = armedExtraction
        val now = clock()

        val root: AccessibilityNodeView? = try {
            rootProvider()
        } catch (t: Throwable) {
            null
        }

        val stats = inspector.inspect(root)
        val rootAvailable = root != null
        val outcome = if (rootAvailable) ExperimentOutcome.COMPLETED else ExperimentOutcome.FAILED
        val reason = if (rootAvailable) null
        else "Active accessibility root was unavailable at inspection time."

        // The structural pass above ran first and is untouched, so the compatibility numbers stay
        // produced by the same code that produced R1-R8. This second pass is additive.
        val extraction = if (withExtraction && root != null) {
            runCatching { extractor.extract(root, inspectionLabel = labelFor(now)) }
                .getOrElse {
                    Reconstruction.unsupported(
                        listOf("Extraction stage aborted: ${it.javaClass.simpleName}."),
                    )
                }
        } else if (withExtraction) {
            // Armed for extraction, but there was no tree to extract from. Recorded as an
            // unsupported reconstruction so the results screen can say which stage stopped, instead
            // of leaving null behind and reading as "the researcher never ticked the box".
            Reconstruction.unsupported(
                listOf("No accessibility root was available, so the extraction stage had nothing to traverse."),
                nodesSampled = stats?.nodesVisited ?: 0,
            )
        } else {
            null
        }

        val result = InspectionResult(
            timestampMillis = now,
            targetPackage = target,
            eventReceived = true,
            packageMatched = true,
            rootAvailable = rootAvailable,
            stats = stats,
            outcome = outcome,
            failureReason = reason,
            extraction = extraction,
        )

        lastResult = result
        completedCount += 1
        armedTarget = null
        armedExtraction = false
        armStatus = ArmStatus.NOT_ARMED
        return result
    }

    /** Clears stored results and the session counter without touching an active arm. */
    @Synchronized
    fun clearResults() {
        lastResult = null
        completedCount = 0
    }

    /** Consent revocation: cancels any pending arm AND wipes local results. */
    @Synchronized
    fun revokeAndClear() {
        armStatus = ArmStatus.NOT_ARMED
        armedTarget = null
        armedExtraction = false
        armToken += 1
        lastResult = null
        completedCount = 0
    }

    /** Called on process restart / service disconnect to make sure no orphan arm survives. */
    @Synchronized
    fun forceDisarm() {
        armedTarget = null
        armedExtraction = false
        armToken += 1
        if (armStatus != ArmStatus.NOT_ARMED) armStatus = ArmStatus.NOT_ARMED
    }

    /** Short, non-identifying id used only to prefix candidate ids inside one inspection. */
    private fun labelFor(millis: Long): String = "i${millis % 100_000}"
}
