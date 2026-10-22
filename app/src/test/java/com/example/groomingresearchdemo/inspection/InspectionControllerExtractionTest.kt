package com.example.groomingresearchdemo.inspection

import com.example.groomingresearchdemo.inspection.extraction.ReconstructionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the message-level stage attaches to the one-shot state machine.
 *
 * The rule being proved here is that extraction adds a *pass*, not a *trigger*. It rides the arm the
 * researcher already pressed, it is captured at arm time like the target package is, it is refused by
 * every gate that refuses an inspection, and it dies with the arm.
 */
class InspectionControllerExtractionTest {

    private val target = "com.google.android.apps.messaging"

    private fun controller() = InspectionController(clock = { 1_700_000_000_000L })

    private fun chatScreen(): FakeNode {
        val list = FakeNode(classValue = "androidx.recyclerview.widget.RecyclerView", boundsValue = FakeNode.viewport())
        list.addChild(FakeNode.messageRow("alpha synthetic turn", top = 100))
        list.addChild(FakeNode.messageRow("beta synthetic turn", top = 200))
        return FakeNode(classValue = "android.widget.FrameLayout", boundsValue = FakeNode.viewport()).addChild(list)
    }

    @Test
    fun everyGateThatRefusesAnInspectionAlsoRefusesTheExtractionStage() {
        val consentless = controller()
        assertEquals(
            ArmResult.CONSENT_REQUIRED,
            consentless.arm(consentAccepted = false, serviceEnabled = true, targetPackage = target, messageLevelExtraction = true),
        )
        assertFalse(consentless.armedExtraction)

        val offline = controller()
        assertEquals(
            ArmResult.SERVICE_REQUIRED,
            offline.arm(consentAccepted = true, serviceEnabled = false, targetPackage = target, messageLevelExtraction = true),
        )
        assertFalse(offline.armedExtraction)
    }

    @Test
    fun theExtractionChoiceIsCapturedAtArmTimeNotAtEventTime() {
        val controller = controller()
        controller.arm(true, true, target, messageLevelExtraction = true)

        assertTrue(controller.armedExtraction)
        assertEquals(ArmResult.ALREADY_ARMED, controller.arm(true, true, target, messageLevelExtraction = false))
        assertTrue("a second press must not silently narrow an armed inspection", controller.armedExtraction)
    }

    @Test
    fun anEventFromAnotherPackageLeavesBothTheArmAndTheExtractionFlagIntact() {
        val controller = controller()
        controller.arm(true, true, target, messageLevelExtraction = true)
        var treeWasOpened = false

        assertNull(
            controller.handleEvent("com.instagram.lite") {
                treeWasOpened = true
                chatScreen()
            },
        )

        assertFalse("a non-target event must not even open a tree", treeWasOpened)
        assertEquals(ArmStatus.ARMED, controller.armStatus)
        assertTrue(controller.armedExtraction)
    }

    @Test
    fun oneArmedPressProducesExactlyOneExtraction() {
        val controller = controller()
        controller.arm(true, true, target, messageLevelExtraction = true)

        val first = controller.handleEvent(target) { chatScreen() }
        val second = controller.handleEvent(target) { chatScreen() }

        assertNotNull(first?.extraction)
        assertNull(second)
        assertNull(second?.extraction)
        assertEquals(1, controller.completedCount)
        assertFalse(controller.armedExtraction)
        assertEquals(ArmStatus.NOT_ARMED, controller.armStatus)
    }

    @Test
    fun theStructuralStageRunsIdenticallyWhetherOrNotExtractionIsArmed() {
        val structuralOnly = InspectionController(clock = { 1_700_000_000_000L }).apply {
            arm(true, true, target)
        }.handleEvent(target) { chatScreen() }
        val withExtraction = InspectionController(clock = { 1_700_000_000_000L }).apply {
            arm(true, true, target, messageLevelExtraction = true)
        }.handleEvent(target) { chatScreen() }

        // The compatibility numbers must stay produced by the same code that produced R1-R8.
        assertEquals(structuralOnly?.stats, withExtraction?.stats)
        assertEquals(structuralOnly?.timestampMillis, withExtraction?.timestampMillis)
        assertNull(structuralOnly?.extraction)
        assertNotNull(withExtraction?.extraction)
    }

    @Test
    fun anArmedExtractionWithNoTreeReportsUnsupportedRatherThanNothing() {
        val controller = controller()
        controller.arm(true, true, target, messageLevelExtraction = true)

        val result = controller.handleEvent(target) { null }

        assertEquals(ExperimentOutcome.FAILED, result?.outcome)
        assertEquals(ReconstructionStatus.UNSUPPORTED, result?.extraction?.status)
        assertTrue(result?.extraction?.reasons.orEmpty().any { it.contains("No accessibility root was available") })
    }

    @Test
    fun revokingConsentDisarmsTheExtractionStageToo() {
        val controller = controller()
        controller.arm(true, true, target, messageLevelExtraction = true)

        controller.revokeAndClear()

        assertFalse(controller.armedExtraction)
        assertEquals(ArmStatus.NOT_ARMED, controller.armStatus)
        assertNull(controller.lastResult)
        assertEquals(0, controller.completedCount)
    }

    @Test
    fun extractionFailuresAreRecordedAsReasonsAndNeverAsCrashes() {
        val controller = InspectionController(
            extractor = object : com.example.groomingresearchdemo.inspection.extraction.MessageLevelExtractor() {
                override fun extract(root: AccessibilityNodeView?, viewport: NodeBounds?, inspectionLabel: String) =
                    throw IllegalStateException("simulated stage failure")
            },
            clock = { 1_700_000_000_000L },
        )
        controller.arm(true, true, target, messageLevelExtraction = true)

        val result = controller.handleEvent(target) { chatScreen() }

        assertEquals(ExperimentOutcome.COMPLETED, result?.outcome)
        assertEquals(ReconstructionStatus.UNSUPPORTED, result?.extraction?.status)
        assertTrue(result?.extraction?.reasons.orEmpty().any { it.contains("Extraction stage aborted") })
    }
}
