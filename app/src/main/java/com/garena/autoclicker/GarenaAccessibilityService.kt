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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GarenaAccessibilityService : AccessibilityService() {

    companion object {
        var instance: GarenaAccessibilityService? = null
        var isClickEnabled = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var lastClickTime = 0L
    private val VIA_PACKAGES = setOf("mark.via.gp", "mark.via", "mark.via.sh")

    override fun onServiceConnected() {
        instance = this
        // Configure exactly like Smart-AutoClicker does at runtime
        val info = AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                          AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                     AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info

        mainHandler.post {
            Toast.makeText(applicationContext, "✅ Garena Auto Clicker ready!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isClickEnabled) return
        val pkg = event?.packageName?.toString() ?: return
        if (!VIA_PACKAGES.contains(pkg)) return

        val now = System.currentTimeMillis()
        if (now - lastClickTime < 2500) return
        lastClickTime = now // reserve slot

        serviceScope.launch {
            delay(600) // wait for Via UI to settle
            performClickOnVia()
        }
    }

    private suspend fun performClickOnVia() {
        if (!isClickEnabled) return
        val root = rootInActiveWindow ?: run {
            // Via window not in foreground yet, try gesture at default position
            doGestureClick(540, 1800)
            return
        }

        // 1. Try finding "Homepage" or "Home" text node (Smart-AutoClicker approach: get bounds, tap center)
        val clicked = tryTextClick(root, listOf("Homepage", "homepage", "Home", "home", "主页"))
        if (clicked) return

        // 2. Try content description
        val descClicked = tryDescClick(root, listOf("Homepage", "Home", "Go to homepage"))
        if (descClicked) return

        // 3. Fallback: raw gesture at Via's typical homepage button region (bottom nav)
        doGestureClick(540, 1800)
    }

    private suspend fun tryTextClick(root: AccessibilityNodeInfo, keywords: List<String>): Boolean {
        for (kw in keywords) {
            val nodes = root.findAccessibilityNodeInfosByText(kw)
            for (node in nodes) {
                val target = if (node.isClickable) node else findClickableParent(node)
                if (target != null) {
                    val bounds = Rect()
                    target.getBoundsInScreen(bounds)
                    if (bounds.width() > 0 && bounds.height() > 0) {
                        val success = doGestureClick(bounds.centerX(), bounds.centerY())
                        if (success) return true
                    }
                }
            }
        }
        return false
    }

    private suspend fun tryDescClick(root: AccessibilityNodeInfo, descs: List<String>): Boolean {
        fun search(node: AccessibilityNodeInfo, depth: Int = 0): AccessibilityNodeInfo? {
            if (depth > 6) return null
            val cd = node.contentDescription?.toString() ?: ""
            if (descs.any { cd.contains(it, ignoreCase = true) }) {
                return if (node.isClickable) node else findClickableParent(node)
            }
            for (i in 0 until node.childCount) {
                val found = search(node.getChild(i) ?: continue, depth + 1)
                if (found != null) return found
            }
            return null
        }
        val target = search(root) ?: return false
        val bounds = Rect()
        target.getBoundsInScreen(bounds)
        if (bounds.width() > 0) {
            return doGestureClick(bounds.centerX(), bounds.centerY())
        }
        return false
    }

    // ── Smart-AutoClicker GestureExecutor pattern ──────────────────────────────
    // Adapted from: GestureExecutor.kt — suspendCoroutine + both onCompleted/onCancelled = success
    suspend fun doGestureClick(x: Int, y: Int): Boolean {
        val path = Path().also { it.moveTo(x.toFloat(), y.toFloat()) }
        // Smart-AutoClicker: min 1ms, max 59999ms — use 100ms for a solid tap
        val stroke = GestureDescription.StrokeDescription(path, 0L, 100L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        return suspendCoroutine { continuation ->
            try {
                dispatchGesture(gesture, object : GestureResultCallback() {
                    // Smart-AutoClicker treats BOTH completed AND cancelled as success
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        isClickEnabled = false
                        mainHandler.post {
                            Toast.makeText(applicationContext, "✅ Clicked at ($x,$y)!", Toast.LENGTH_SHORT).show()
                        }
                        continuation.resume(true)
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        // Still counts as dispatched per Smart-AutoClicker logic
                        isClickEnabled = false
                        continuation.resume(true)
                    }
                }, null)
            } catch (e: RuntimeException) {
                // System too busy — Smart-AutoClicker logs warning and retries
                continuation.resume(false)
            }
        }
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
