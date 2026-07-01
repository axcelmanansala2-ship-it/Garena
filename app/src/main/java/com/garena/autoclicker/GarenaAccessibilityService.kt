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
        var debugMode = false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var lastClickTime = 0L
    private val VIA_PACKAGES = setOf("mark.via.gp", "mark.via", "mark.via.sh")

    override fun onServiceConnected() {
        instance = this
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                          AccessibilityEvent.TYPE_VIEW_SCROLLED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.capabilities = AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES
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

        // Try ALL methods in order
        val clicked = findAndClickByText(root) || findAndClickByClass(root)

        if (!clicked) {
            // Fallback: toast what we found + try gesture at bottom bar
            if (debugMode) {
                val found = collectAllText(root)
                handler.post {
                    Toast.makeText(applicationContext,
                        "Via nodes: $found", Toast.LENGTH_LONG).show()
                }
            }
            // Try gesture tap on likely positions (bottom nav bar at ~y=90% of screen)
            performGestureClick(540, 1850) // typical Via homepage button position
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
                    performGestureClick(bounds.centerX(), bounds.centerY())
                    lastClickTime = System.currentTimeMillis()
                    isClickEnabled = false
                    return true
                }
            }
        }
        return false
    }

    private fun findAndClickByClass(root: AccessibilityNodeInfo): Boolean {
        // Try address bar / EditText — click it and type homepage
        val editTexts = root.findAccessibilityNodeInfosByText("")
        for (node in editTexts) {
            if (node.className?.contains("EditText") == true && node.isEditable) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)
                if (bounds.width() > 200) {
                    performGestureClick(bounds.centerX(), bounds.centerY())
                    lastClickTime = System.currentTimeMillis()
                    isClickEnabled = false
                    return true
                }
            }
        }
        return false
    }

    fun performGestureClick(x: Int, y: Int) {
        val path = Path().also { it.moveTo(x.toFloat(), y.toFloat()) }
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

    private fun collectAllText(node: AccessibilityNodeInfo, depth: Int = 0): String {
        if (depth > 4) return ""
        val sb = StringBuilder()
        val txt = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val cls = node.className?.toString()?.substringAfterLast(".")
        if (!txt.isNullOrBlank() || !desc.isNullOrBlank())
            sb.append("[$cls:${txt ?: desc}]")
        for (i in 0 until node.childCount)
            sb.append(collectAllText(node.getChild(i) ?: continue, depth + 1))
        return sb.toString()
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
