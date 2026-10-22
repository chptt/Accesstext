package com.example.groomingresearchdemo.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the one-shot state machine: consent gates, target gates, cancel-before-event, wrong
 * package, consume-once, no auto re-arm, null root, and consent revocation. Pure JVM — the
 * Android service layer simply forwards real events into [InspectionController.handleEvent].
 */
class InspectionControllerTest {

    private val target = "com.instagram.android"

    private fun controller(): InspectionController =
        InspectionController(clock = { 1_700_000_000_000L })

    private fun armedRootProvider(): () -> AccessibilityNodeView? = { FakeNode(textValue = "screen") }
    private fun nullRootProvider(): () -> AccessibilityNodeView? = { null }

    @Test
    fun armDeniedWithoutConsent() {
        // Test 1: consent declined / not given blocks arming.
        val c = controller()
        assertEquals(
            ArmResult.CONSENT_REQUIRED,
            c.arm(consentAccepted = false, serviceEnabled = true, targetPackage = target),
        )
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
    }

    @Test
    fun armDeniedWithoutService() {
        val c = controller()
        assertEquals(
            ArmResult.SERVICE_REQUIRED,
            c.arm(consentAccepted = true, serviceEnabled = false, targetPackage = target),
        )
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
    }

    @Test
    fun armSucceedsWhenConsentedAndEnabled() {
        val c = controller()
        assertEquals(
            ArmResult.ARMED,
            c.arm(consentAccepted = true, serviceEnabled = true, targetPackage = target),
        )
        assertEquals(ArmStatus.ARMED, c.armStatus)
    }

    @Test
    fun cannotDoubleArm() {
        val c = controller()
        c.arm(true, true, target)
        assertEquals(ArmResult.ALREADY_ARMED, c.arm(true, true, target))
    }

    @Test
    fun armDeniedWithoutATargetApp() {
        val c = controller()
        assertEquals(ArmResult.TARGET_REQUIRED, c.arm(true, true, null))
        assertEquals(ArmResult.TARGET_REQUIRED, c.arm(true, true, "   "))
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
        assertNull(c.armedTarget)
    }

    @Test
    fun cancelBeforeEventRecordsCancelledOutcome() {
        // Test 3: cancel an armed inspection before any qualifying event arrives.
        val c = controller()
        c.arm(true, true, target)
        assertTrue(c.cancel())
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
        assertEquals(ExperimentOutcome.CANCELLED, c.lastResult?.outcome)
        assertFalse(c.lastResult!!.eventReceived)
        assertEquals(0, c.completedCount)
    }

    @Test
    fun cancelWhenNotArmedIsNoop() {
        val c = controller()
        assertFalse(c.cancel())
    }

    @Test
    fun wrongPackageEventIsIgnoredAndStaysArmed() {
        // Test 4: an event from a different package must not consume the arm or inspect.
        val c = controller()
        c.arm(true, true, target)
        var treeOpened = false
        val result = c.handleEvent("com.whatever") { treeOpened = true; FakeNode() }
        assertNull(result)
        assertFalse("the non-target tree must never even be opened", treeOpened)
        assertEquals(ArmStatus.ARMED, c.armStatus)
        assertNull(c.lastResult)
    }

    @Test
    fun armedTargetEventCompletesAndDisarms() {
        val c = controller()
        c.arm(true, true, target)
        val result = c.handleEvent(target, armedRootProvider())
        assertNotNull(result)
        assertTrue(result!!.eventReceived)
        assertTrue(result.packageMatched)
        assertTrue(result.rootAvailable)
        assertEquals(ExperimentOutcome.COMPLETED, result.outcome)
        assertNotNull(result.stats)
        // One-shot: consumed and NOT auto re-armed.
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
        assertNull(c.armedTarget)
        assertEquals(1, c.completedCount)
    }

    @Test
    fun targetIsCapturedAtArmTimeAndIgnoresLaterSelectionChange() {
        // The researcher may switch the app under test while an inspection waits. The armed
        // request keeps the package it was armed for, so a different app cannot satisfy it.
        val c = controller()
        c.arm(true, true, "com.google.android.apps.messaging")
        assertNull(c.handleEvent("com.instagram.android") { FakeNode() })
        val result = c.handleEvent("com.google.android.apps.messaging") { FakeNode() }
        assertNotNull(result)
        assertEquals("com.google.android.apps.messaging", result!!.targetPackage)
    }

    @Test
    fun consumedArmDoesNotRefireOnNextEvent() {
        val c = controller()
        c.arm(true, true, target)
        c.handleEvent(target, armedRootProvider())
        // A second event arrives with no re-arm: must be ignored.
        val second = c.handleEvent(target, armedRootProvider())
        assertNull(second)
        assertEquals(1, c.completedCount)
    }

    @Test
    fun nullRootReportsFailedDistinctly() {
        // Test 5 end-to-end through the controller: event received but root unavailable is FAILED,
        // never a fake success.
        val c = controller()
        c.arm(true, true, target)
        val result = c.handleEvent(target, nullRootProvider())!!
        assertTrue(result.eventReceived)
        assertTrue(result.packageMatched)
        assertFalse(result.rootAvailable)
        assertNull(result.stats)
        assertEquals(ExperimentOutcome.FAILED, result.outcome)
        assertNotNull(result.failureReason)
        assertEquals(1, c.completedCount)
    }

    @Test
    fun emptyTreeStillCompletesWithZeroTextNodes() {
        // Test 6 via the controller: a root that exists but has no text is a COMPLETED inspection
        // with zero text-bearing nodes — the correct, non-overclaiming result.
        val c = controller()
        c.arm(true, true, target)
        val result = c.handleEvent(target) { FakeNode() }!!
        assertEquals(ExperimentOutcome.COMPLETED, result.outcome)
        assertEquals(1, result.stats!!.nodesVisited)
        assertEquals(0, result.stats!!.textBearingNodes)
    }

    @Test
    fun consentRevocationClearsArmAndResults() {
        // Test 2: revoking consent cancels a pending arm AND clears stored results/counter.
        val c = controller()
        c.arm(true, true, target)
        c.handleEvent(target, armedRootProvider())
        assertNotNull(c.lastResult)
        c.revokeAndClear()
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
        assertNull(c.armedTarget)
        assertNull(c.lastResult)
        assertEquals(0, c.completedCount)
    }

    @Test
    fun forceDisarmPreventsStrayArmsOnRestart() {
        val c = controller()
        c.arm(true, true, target)
        c.forceDisarm()
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
        assertNull(c.handleEvent(target, armedRootProvider()))
    }

    @Test
    fun armTokenIsOnlyIssuedForTheArmedTarget() {
        val c = controller()
        assertNull(c.armTokenFor(target))
        c.arm(true, true, target)
        assertNull(c.armTokenFor("com.google.android.apps.messaging"))
        assertNull(c.armTokenFor(null))
        assertNotNull(c.armTokenFor(target))
    }

    @Test
    fun queuedReadRejectedAfterCancelAndNeverOpensTree() {
        // The service reads the token when the event arrives and settles for a moment before
        // opening the tree. If the researcher cancels inside that window, the queued read must be
        // dropped: no traversal, no second result, and the CANCELLED outcome stands.
        val c = controller()
        c.arm(true, true, target)
        val token = c.armTokenFor(target)!!
        assertTrue(c.cancel())

        var treeOpened = false
        val result = c.handleEvent(target, token) { treeOpened = true; FakeNode() }
        assertNull(result)
        assertFalse("a cancelled arm must not open the tree when its queued read fires", treeOpened)
        assertEquals(ArmStatus.NOT_ARMED, c.armStatus)
        assertEquals(ExperimentOutcome.CANCELLED, c.lastResult?.outcome)
        assertEquals(0, c.completedCount)
    }

    @Test
    fun staleTokenCannotConsumeAFreshArm() {
        // Cancel and re-arm both bump the token. A read queued for the first arm must not be able
        // to consume the arm the researcher made afterwards.
        val c = controller()
        c.arm(true, true, target)
        val stale = c.armTokenFor(target)!!
        c.cancel()
        c.arm(true, true, target)
        val fresh = c.armTokenFor(target)!!

        var treeOpened = false
        assertNull(c.handleEvent(target, stale) { treeOpened = true; FakeNode() })
        assertFalse(treeOpened)
        assertEquals(ArmStatus.ARMED, c.armStatus)
        assertEquals(0, c.completedCount)

        val result = c.handleEvent(target, fresh) { FakeNode(textValue = "screen") }
        assertNotNull(result)
        assertEquals(ExperimentOutcome.COMPLETED, result!!.outcome)
        assertEquals(1, c.completedCount)
    }

    @Test
    fun completedArmTokenCannotConsumeARearmedInspection() {
        // Completing an inspection disarms but leaves the token where the next arm will advance it;
        // a read left over from the completed arm is rejected once a new arm exists.
        val c = controller()
        c.arm(true, true, target)
        val first = c.armTokenFor(target)!!
        assertNotNull(c.handleEvent(target, first, armedRootProvider()))

        c.arm(true, true, target)
        var treeOpened = false
        assertNull(c.handleEvent(target, first) { treeOpened = true; FakeNode() })
        assertFalse(treeOpened)
        assertEquals(ArmStatus.ARMED, c.armStatus)
        assertEquals(1, c.completedCount)
    }
}
