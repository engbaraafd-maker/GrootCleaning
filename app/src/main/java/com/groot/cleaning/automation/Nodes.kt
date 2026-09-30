package com.groot.cleaning.automation

import android.view.accessibility.AccessibilityNodeInfo

/** Reusable Accessibility-tree helpers. */
object Nodes {
    fun all(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val out = ArrayList<AccessibilityNodeInfo>()
        val q = ArrayDeque<AccessibilityNodeInfo>()
        q.add(root)
        while (q.isNotEmpty()) {
            val n = q.removeFirst()
            out.add(n)
            for (i in 0 until n.childCount) n.getChild(i)?.let { q.add(it) }
        }
        return out
    }

    fun text(n: AccessibilityNodeInfo): String? = n.text?.toString() ?: n.contentDescription?.toString()

    fun findByTexts(root: AccessibilityNodeInfo, cands: List<String>): AccessibilityNodeInfo? =
        all(root).firstOrNull { it.isVisibleToUser && Matcher.matches(text(it), cands) }

    fun findByTextContains(root: AccessibilityNodeInfo, needle: String): AccessibilityNodeInfo? =
        all(root).firstOrNull { it.isVisibleToUser && Matcher.containsAny(text(it), listOf(needle)) }

    fun hasText(root: AccessibilityNodeInfo, cands: List<String>): Boolean = findByTexts(root, cands) != null

    fun hasTextContaining(root: AccessibilityNodeInfo, cands: List<String>): Boolean =
        all(root).any { Matcher.containsAny(text(it), cands) }

    fun findByViewId(root: AccessibilityNodeInfo, id: String): AccessibilityNodeInfo? =
        root.findAccessibilityNodeInfosByViewId(id)?.firstOrNull { it.isVisibleToUser }

    fun clickableParent(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var c: AccessibilityNodeInfo? = n
        var d = 0
        while (c != null && d < 6) {
            if (c.isClickable) return c
            c = c.parent
            d++
        }
        return null
    }

    fun scrollForward(root: AccessibilityNodeInfo): Boolean =
        all(root).firstOrNull { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) == true
}
