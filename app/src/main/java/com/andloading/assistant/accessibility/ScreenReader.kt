package com.andloading.assistant.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

data class UiElement(
    val index: Int,
    val text: String,
    val className: String,
    val bounds: Rect,
    val clickable: Boolean,
    val editable: Boolean,
)

class ScreenReader(private val service: AssistantAccessibilityService) {

    @Volatile
    var lastElements: List<UiElement> = emptyList()
        private set

    @Volatile
    var lastReadPackage: String? = null
        private set

    fun foregroundPackage(): String? = service.foregroundPackage

    private class Root(val node: AccessibilityNodeInfo, val owned: Boolean)

    private fun activeRoot(): Root? {
        val self = service.packageName
        val active = service.rootInActiveWindow
        if (active != null && active.packageName?.toString() != self) return Root(active, owned = true)
        try {
            for (w in service.windows) {
                if (w.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                val r = w.root ?: continue
                if (r.packageName?.toString() != self) return Root(r, owned = false)
            }
        } catch (_: Exception) {
        }
        return if (active != null) Root(active, owned = true) else null
    }

    fun readTree(): List<UiElement> {
        val root = activeRoot() ?: run {
            lastReadPackage = null
            return emptyList()
        }
        lastReadPackage = root.node.packageName?.toString()
        try {
            val elements = mutableListOf<UiElement>()
            walk(root.node) { node, rect ->
                if (isInteractive(node) && !rect.isEmpty && node.isVisibleToUser && elements.size < MAX_ELEMENTS) {
                    elements += UiElement(
                        index = elements.size,
                        text = (node.text ?: node.contentDescription)?.toString()?.trim()?.take(60).orEmpty(),
                        className = node.className?.toString()?.substringAfterLast('.') ?: "View",
                        bounds = Rect(rect),
                        clickable = node.isClickable,
                        editable = node.isEditable,
                    )
                }
                false
            }
            lastElements = elements
            return elements
        } finally {
            if (root.owned) {
                @Suppress("DEPRECATION")
                root.node.recycle()
            }
        }
    }

    fun nodeAt(index: Int): AccessibilityNodeInfo? {
        if (index < 0 || index >= MAX_ELEMENTS) return null
        val root = activeRoot() ?: return null
        var counter = -1
        var found: AccessibilityNodeInfo? = null
        try {
            walk(root.node) { node, rect ->
                if (found == null && isInteractive(node) && !rect.isEmpty && node.isVisibleToUser) {
                    counter++
                    if (counter == index) {
                        found = node
                        return@walk true
                    }
                }
                false
            }
            return found
        } finally {
            if (root.owned) {
                @Suppress("DEPRECATION")
                root.node.recycle()
            }
        }
    }

    private fun isInteractive(node: AccessibilityNodeInfo): Boolean =
        node.isClickable || node.isEditable || node.isScrollable || node.isCheckable

    private fun walk(root: AccessibilityNodeInfo, visit: (AccessibilityNodeInfo, Rect) -> Boolean) {
        val stack = ArrayDeque<AccessibilityNodeInfo>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && visited < MAX_NODES) {
            visited++
            val node = stack.removeFirst()
            val rect = Rect()
            node.getBoundsInScreen(rect)
            val shouldStop = visit(node, rect)
            if (shouldStop) {
                @Suppress("DEPRECATION")
                stack.forEach { it.recycle() }
                return
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let(stack::add)
            }
        }
    }

    companion object {
        const val MAX_ELEMENTS = 80
        const val MAX_NODES = 800
    }
}
