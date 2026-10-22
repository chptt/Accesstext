package com.example.groomingresearchdemo.service

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.example.groomingresearchdemo.inspection.AccessibilityNodeView
import com.example.groomingresearchdemo.inspection.NodeBounds

/**
 * Production adapter that exposes an [android.view.accessibility.AccessibilityNodeInfo]
 * through the app's Android-free [AccessibilityNodeView] interface.
 *
 * NODE LIFETIME — WHY WE DO NOT CALL recycle()
 * AccessibilityNodeInfo recycling was deprecated at API 33 and became an outright no-op when
 * Android 14 (API 34) removed the underlying object pool. This project targets API 36, so
 * calling recycle() would either emit a deprecation warning or do nothing at all. The correct
 * modern practice — used here — is to simply not retain node references past the inspection:
 * this wrapper is a thin view over a node obtained during a single traversal, and it is only
 * reachable from the StructuralInspector's queue. Once handleEvent returns, the entire graph
 * (queue entries and every RealAccessibilityNodeView) goes out of scope and is garbage
 * collected. No node reference is ever stored on ResearchStore, InspectionController, or any
 * other app state.
 *
 * WHAT THAT SENTENCE MEANS NOW THAT EXTRACTION EXISTS
 * The graph is still dropped when the traversal ends. What survives is copied OUT of the node
 * before it dies: a String, a class name, a rectangle, four booleans. That distinction is the
 * whole retention policy — no AccessibilityNodeInfo reference is held by the candidate or
 * reconstruction layer, so a stale or recycled node cannot be dereferenced later, and the tree is
 * never re-opened after the one-shot inspection finishes.
 *
 * The text of the underlying node is only ever read to decide whether it is blank and to add
 * its length to an aggregate character count. The characters themselves are never kept: no
 * InspectionResult, report, log line, preference or file contains node text.
 */
class RealAccessibilityNodeView(
    private val node: AccessibilityNodeInfo,
) : AccessibilityNodeView {

    override fun text(): CharSequence? = node.text

    override fun contentDescription(): CharSequence? = node.contentDescription

    /** Structural metadata only — a widget class name such as `android.widget.TextView`. */
    override fun className(): CharSequence? = node.className

    override fun childCount(): Int = node.childCount

    override fun getChild(index: Int): AccessibilityNodeView? {
        val child = node.getChild(index) ?: return null
        return RealAccessibilityNodeView(child)
    }

    /**
     * Every accessor below is wrapped individually. A node is allowed to answer some properties and
     * refuse others — a cross-window or already-detached node does exactly that — and one refusal
     * must not cost us the rest of the node's structure, still less abort the inspection.
     */
    override fun boundsInScreen(): NodeBounds? = runCatching {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        NodeBounds(rect.left, rect.top, rect.right, rect.bottom)
    }.getOrNull()

    override fun isClickable(): Boolean = runCatching { node.isClickable }.getOrDefault(false)

    override fun isScrollable(): Boolean = runCatching { node.isScrollable }.getOrDefault(false)

    /**
     * null here means "the platform would not tell us", which the classifier treats as unknown and
     * never as "hidden". Collapsing those two would turn an unreadable node into an excluded node.
     */
    override fun isVisibleToUser(): Boolean? = runCatching { node.isVisibleToUser }.getOrNull()

    override fun parent(): AccessibilityNodeView? =
        runCatching { node.parent?.let(::RealAccessibilityNodeView) }.getOrNull()

    /*
     * NO nodeId OVERRIDE HERE, AND THAT IS A FINDING, NOT AN OMISSION.
     * AccessibilityNodeInfo's per-snapshot instance id is not in the public android-36 API surface
     * (verified with javap against platforms/android-36/android.jar: there is no getSourceNodeId()).
     * Getting it anyway would mean a hidden API, which this project has promised not to use.
     *
     * So on a real device nodeId() stays null and the traverser falls back to wrapper identity,
     * which for freshly-created wrappers suppresses nothing. That is survivable because
     * rootInActiveWindow is a proper tree — one parent per node — so a double visit cannot occur in
     * a single-root walk. The duplicate problem that DOES occur on real chat screens is a container
     * echoing its children's text, and that is handled by the container-echo rule instead, where it
     * is separately counted and separately tested.
     *
     * Recorded in docs/13 as "duplicate-visit suppression: implemented, exercised by fixtures, not
     * observable on device" rather than quietly reported as a working safeguard.
     */
}

