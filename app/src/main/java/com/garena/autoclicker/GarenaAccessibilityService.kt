package com.garena.autoclicker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

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
        // capabilities come from XML (canPerformGestures=true), just update event scope
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info
        handler.post {
            Toast.makeText(applicationContext, "✅ Garena Accessibility Ready!", Toast.LENGTH_SHORT).show()
        }
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
        if (now - lastClickTime < 2500) return

        handler.postDelayed({ tryClickHomepage() }, 800)
    }

    private fun tryClickHomepage() {
        if (!isClickEnabled) return
        val root = rootInActiveWindow ?: return

        val clicked = findAndClickByText(root) || findAndClickByClass(root)
        if (!clicked) {
            // Fallback: gesture tap on Via's typical Homepage button position (bottom nav area)
            performGestureClick(540, 1800)
        }
    }

    private fun findAndClickByText(root: AccessibilityNodeInfo): Boolean {
        val keywords = listOf("Homepage", "homepage", "Home", "home", "主页", "홈")
        for (kw in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                val target = if (node.isClickable) node else findClickableParent(node)
                if (target != null) {
                    val bounds = Rect()
                    target.getBoundsInScreen(bounds)
                    if (bounds.width() > 0) {
                        performGestureClick(bounds.centerX(), bounds.centerY())
                        lastClickTime = System.currentTimeMillis()
                        isClickEnabled = false
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun findAndClickByClass(root: AccessibilityNodeInfo): Boolean {
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (child.className?.contains("EditText") == true && child.isEditable) {
                val bounds = Rect()
                child.getBoundsInScreen(bounds)
                if (bounds.width() > 200) {
                    performGestureClick(bounds.centerX(), bounds.centerY())
                    lastClickTime = System.currentTimeMillis()
                    isClickEnabled = false
                    return true
                }
            }
            if (findAndClickByClass(child)) return true
        }
        return false
    }

    fun performGestureClick(x: Int, y: Int) {
        val path = Path()
        path.moveTo(x.toFloat(), y.toFloat())
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                lastClickTime = System.currentTimeMillis()
                isClickEnabled = false
                handler.post {
                    Toast.makeText(applicationContext, "✅ Clicked!", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                handler.post {
                    Toast.makeText(applicationContext, "⚠ Gesture cancelled", Toast.LENGTH_SHORT).show()
                }
            }
        }, null)
    }

    private fun findClickableParent(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node.parent
        repeat(6) {
            if (current?.isClickable == true) return current
            current = current?.parent
        }
        return null
    }

    override fun onInterrupt() {}
}
