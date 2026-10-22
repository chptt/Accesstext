package com.example.groomingresearchdemo.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.example.groomingresearchdemo.state.ResearchStore

/**
 * The AccessibilityService that performs the manually armed, one-shot structural inspection.
 *
 * It does exactly one job: when the user has armed an inspection AND the OS delivers an event
 * from the package that inspection was armed for, it walks the ACTIVE window's accessibility
 * tree and reports structural statistics. It never:
 *   - runs on a timer, polls, or reads continuously,
 *   - opens the tree for an unarmed or non-target event,
 *   - reads, stores, logs or transmits the text of any node,
 *   - scrolls, clicks, navigates, or otherwise drives the target app,
 *   - captures screenshots,
 *   - touches any application outside the messaging allowlist declared in the config XML.
 *
 * The read is delayed by [SETTLE_DELAY_MILLIS] after the qualifying event and still happens
 * exactly once. Reading in the same frame as the event was observed on device to land on a
 * transition frame and come back with no root at all (RC-04), so the delay buys the window time
 * to settle without ever reading twice.
 *
 * All state transitions and the consume-once logic live in ResearchStore / InspectionController,
 * so this class stays thin and the behaviour is unit-testable without an emulator.
 */
class ChatInspectionService : AccessibilityService() {

    private val settleHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        // The config XML already restricts us to the messaging allowlist. Narrow it further to
        // the single app the researcher selected, so the framework delivers no events at all for
        // the other candidates.
        applyTargetFilter(ResearchStore.uiState.value.targetPackage)
        ResearchStore.onTargetPackageChanged = { target -> applyTargetFilter(target) }
        ResearchStore.notifyServiceConnected()
        LogUtils.i("ChatInspectionService connected (target=${ResearchStore.uiState.value.targetPackage}).")
    }

    /**
     * Rewrites our own [android.accessibilityservice.AccessibilityServiceInfo] to listen to one
     * package. This NARROWS the static allowlist; it never adds capabilities, event types, or
     * windows beyond what the config XML already granted.
     */
    private fun applyTargetFilter(targetPackage: String) {
        val info = serviceInfo ?: return
        info.packageNames = arrayOf(targetPackage)
        runCatching { setServiceInfo(info) }
            .onFailure { LogUtils.w("Could not narrow the package filter; the config allowlist still applies.") }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        // Capture the arm this event belongs to. Null means nothing is waiting for this package,
        // so nothing is scheduled and the target's tree stays unopened.
        val token = ResearchStore.armTokenFor(packageName) ?: return

        // One queued read per qualifying event. The first one to run consumes the arm; any later
        // one, or one whose arm was cancelled or replaced while it waited, is rejected by the
        // token check inside the controller before the root is ever touched.
        settleHandler.postDelayed({
            val produced = ResearchStore.handleAccessibilityEvent(packageName, token) {
                // rootInActiveWindow may be null (e.g. transition frames); the inspector reports
                // that distinctly as "root unavailable" rather than treating it as a success.
                rootInActiveWindow?.let(::RealAccessibilityNodeView)
            }
            if (produced != null && packageName != produced.targetPackage) {
                // Defensive: with the package filter in place this should not occur, but we log the
                // mismatch rather than silently inspecting an unexpected window.
                LogUtils.w("Consumed an inspection for a package other than the armed target; ignoring structurally.")
            }
        }, SETTLE_DELAY_MILLIS)
    }

    override fun onInterrupt() {
        LogUtils.w("ChatInspectionService interrupted.")
    }

    override fun onDestroy() {
        settleHandler.removeCallbacksAndMessages(null)
        ResearchStore.onTargetPackageChanged = null
        ResearchStore.notifyServiceDisconnected()
        LogUtils.i("ChatInspectionService destroyed.")
        super.onDestroy()
    }

    companion object {
        /**
         * Time between the qualifying event and the single tree read. Not a polling interval: one
         * event schedules exactly one read. Chosen to clear the transition frame that produced the
         * observed null-root failure without making the researcher wait noticeably.
         */
        const val SETTLE_DELAY_MILLIS = 1_500L
    }
}
