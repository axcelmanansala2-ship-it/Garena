package com.garena.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class GarenaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GarenaAccessibilityService? = null
        var isClickEnabled = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L
    private val VIA_PACKAGES = setOf("mark.via.gp", "mark.via", "mark.via.sh")

    override fun onServiceConnected() {
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isClickEnabled) return
        val pkg = event?.packageName?.toString() ?: return
        if (!VIA_PACKAGES.contains(pkg)) return

        val now = System.currentTimeMillis()
        if (now - lastClickTime < 2000) return // debounce

        handler.postDelayed({ tryClickHomepage() }, 600)
    }

    private fun tryClickHomepage() {
        if (!isClickEnabled) return
        val root = rootInActiveWindow ?: return

        val clicked = tryByText(root, "Homepage")
            ?: tryByText(root, "homepage")
            ?: tryByText(root, "Home")
            ?: tryByContentDesc(root, "Homepage")
            ?: tryByContentDesc(root, "Home")
            ?: tryByClass(root, "android.widget.EditText")

        if (clicked != null) {
            clicked.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            lastClickTime = System.currentTimeMillis()
            isClickEnabled = false // stop after one successful click
            clicked.recycle()
        }
    }

    private fun tryByText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val nodes = root.findAccessibilityNodeInfosByText(text)
        return nodes.firstOrNull { it.isClickable || it.isEnabled }
            ?: nodes.firstOrNull()?.let { findClickableParent(it) }
    }

    private fun tryByContentDesc(root: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (child.contentDescription?.contains(desc, ignoreCase = true) == true)
                return if (child.isClickable) child else findClickableParent(child)
            val found = tryByContentDesc(child, desc)
            if (found != null) return found
        }
        return null
    }

    private fun tryByClass(root: AccessibilityNodeInfo, cls: String): AccessibilityNodeInfo? {
        if (root.className?.toString() == cls) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val found = tryByClass(child, cls)
            if (found != null) return found
        }
        return null
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        var depth = 0
        while (current != null && depth < 5) {
            if (current.isClickable) return current
            current = current.parent
            depth++
        }
        return null
    }

    override fun onInterrupt() {}
}
