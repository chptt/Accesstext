package com.example.groomingresearchdemo.inspection

import com.example.groomingresearchdemo.AppConstants

/**
 * Breadth-first traversal of an accessibility tree that produces STRUCTURAL statistics only.
 *
 * It counts nodes, how many carry text or only an accessibility label, the deepest level
 * reached, which widget classes the app exposes, and where the tree could not be read.
 * It NEVER returns, stores, or logs the text of any node. That is the whole point of the
 * compatibility experiment: measure how much of a screen is even exposed to accessibility
 * tooling without collecting the private content itself.
 *
 * Pure Kotlin (depends only on [AccessibilityNodeView]) so it is unit-testable with fake trees.
 */
class StructuralInspector(private val maxNodes: Int = AppConstants.MAX_TRAVERSAL_NODES) {

    /** @return null when [root] is null, so the caller can report "root unavailable" distinctly. */
    fun inspect(root: AccessibilityNodeView?): StructuralStats? {
        if (root == null) return null

        var nodesVisited = 0
        var textBearingNodes = 0
        var nodesWithText = 0
        var nodesWithDescriptionOnly = 0
        var totalTextCharacters = 0
        var maxDepth = 0
        var truncated = false
        var unreadableChildNodes = 0

        val classCounts = LinkedHashMap<String, Int>()
        val seenClasses = HashSet<String>()
        val notes = mutableListOf<String>()

        // Explicit queue instead of recursion: deep messaging-app view hierarchies could
        // otherwise blow the stack. Pair node with its 1-based depth (root == depth 1).
        val queue = ArrayDeque<Pair<AccessibilityNodeView, Int>>()
        queue.addLast(root to 1)

        while (queue.isNotEmpty()) {
            if (nodesVisited >= maxNodes) {
                truncated = true
                addNote(notes, "Traversal stopped at the $maxNodes node cap; deeper subtrees were not visited.")
                break
            }
            val (node, depth) = queue.removeFirst()

            nodesVisited++
            if (depth > maxDepth) maxDepth = depth

            val className = readClassName(node)
            seenClasses.add(className)
            if (classCounts.containsKey(className) || classCounts.size < AppConstants.MAX_CLASS_HISTOGRAM_KEYS) {
                classCounts.merge(className, 1, Int::plus)
            }

            val text = readText(node)
            val description = readDescription(node)
            if (text != null) {
                nodesWithText++
                totalTextCharacters += text.length
            } else if (description != null) {
                nodesWithDescriptionOnly++
            }
            if (text != null || description != null) textBearingNodes++

            try {
                val childCount = node.childCount()
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) queue.addLast(child to depth + 1)
                }
            } catch (t: Throwable) {
                // A single unreadable subtree must not crash the inspection or lose already
                // counted nodes; we simply stop descending from this node and record that it
                // happened, because "the app hid this subtree" is itself a experiment result.
                unreadableChildNodes++
                addNote(notes, "Children of a depth-$depth '$className' node could not be read (${t.javaClass.simpleName}).")
            }
        }

        if (unreadableChildNodes > 0 && nodesVisited == 1) {
            addNote(notes, "Only the root node was reachable.")
        }
        if (textBearingNodes == 0) {
            addNote(notes, "No node in the visited tree exposed text or an accessibility label.")
        }

        return StructuralStats(
            nodesVisited = nodesVisited,
            textBearingNodes = textBearingNodes,
            emptyOrUnavailableTextNodes = nodesVisited - textBearingNodes,
            maxDepth = maxDepth,
            truncated = truncated,
            nodesWithText = nodesWithText,
            nodesWithContentDescriptionOnly = nodesWithDescriptionOnly,
            totalTextCharacters = totalTextCharacters,
            distinctNodeClasses = seenClasses.size,
            nodeClassCounts = classCounts.toMap(),
            unreadableChildNodes = unreadableChildNodes,
            notes = notes.toList(),
        )
    }

    private fun readText(node: AccessibilityNodeView): String? =
        runCatching { node.text()?.toString()?.takeIf { it.isNotBlank() }?.trim() }.getOrNull()

    private fun readDescription(node: AccessibilityNodeView): String? =
        runCatching { node.contentDescription()?.toString()?.takeIf { it.isNotBlank() }?.trim() }.getOrNull()

    private fun readClassName(node: AccessibilityNodeView): String =
        runCatching { node.className()?.toString()?.takeIf { it.isNotBlank() } }.getOrNull()
            ?: "<unknown-class>"

    private fun addNote(notes: MutableList<String>, note: String) {
        if (notes.size < AppConstants.MAX_NOTES && note !in notes) notes.add(note)
    }
}
