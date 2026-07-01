package com.garena.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GarenaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GarenaAccessibilityService? = null
    }

    private var autoClickEnabled = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun startAutoClick() {
        autoClickEnabled = true
    }

    fun stopAutoClick() {
        autoClickEnabled = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!autoClickEnabled) return
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // Detect Via browser is open
        if (packageName == "mark.via.gp" || packageName == "mark.via") {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

                handler.postDelayed({
                    tryClickHomepage()
                }, 800)
            }
        }
    }

    private fun tryClickHomepage() {
        val root = rootInActiveWindow ?: return

        // Try finding "Homepage" text node
        val homepageNode = findNodeByText(root, "Homepage")
            ?: findNodeByText(root, "homepage")
            ?: findNodeByContentDescription(root, "Homepage")

        if (homepageNode != null) {
            homepageNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            homepageNode.recycle()
            return
        }

        // Fallback: find the search bar (EditText or clickable near top)
        val searchNode = findNodeByClass(root, "android.widget.EditText")
            ?: findNodeByClass(root, "android.widget.SearchView")
        if (searchNode != null) {
            searchNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            searchNode.recycle()
        }
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull()
    }

    private fun findNodeByContentDescription(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (child.contentDescription?.toString()?.contains(desc, ignoreCase = true) == true) {
                return child
            }
            val found = findNodeByContentDescription(child, desc)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findNodeByClass(root: AccessibilityNodeInfo, className: String): AccessibilityNodeInfo? {
        if (root.className?.toString() == className && root.isClickable) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = findNodeByClass(child, className)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    override fun onInterrupt() {}
}
