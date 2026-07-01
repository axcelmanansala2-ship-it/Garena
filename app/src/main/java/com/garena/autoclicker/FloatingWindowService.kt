package com.garena.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val VIA_PACKAGES = listOf("mark.via.gp", "mark.via", "mark.via.sh")
    private var btnStartStop: Button? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        showFloatingWindow()
    }

    private fun startForegroundNotification() {
        val ch = NotificationChannel("garena", "Garena Auto Clicker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        startForeground(1, NotificationCompat.Builder(this, "garena")
            .setContentTitle("Garena Auto Clicker")
            .setContentText("Running — tap START to auto-click Via Homepage")
            .setSmallIcon(android.R.drawable.ic_media_play).build())
    }

    private fun showFloatingWindow() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }

        // Drag support
        var ix = 0; var iy = 0; var rx = 0f; var ry = 0f; var dragging = false
        floatingView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; rx = e.rawX; ry = e.rawY; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - rx) > 5 || Math.abs(e.rawY - ry) > 5) dragging = true
                    if (dragging) { params.x = ix + (e.rawX - rx).toInt(); params.y = iy + (e.rawY - ry).toInt(); windowManager.updateViewLayout(floatingView, params) }
                    true
                }
                else -> false
            }
        }

        btnStartStop = floatingView.findViewById(R.id.btnStartStop)
        btnStartStop?.setOnClickListener {
            val svc = GarenaAccessibilityService.instance
            if (svc == null) {
                Toast.makeText(this, "⚠ Open Garena app → enable Accessibility first", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!GarenaAccessibilityService.isClickEnabled) {
                startAutoClick(svc)
            } else {
                GarenaAccessibilityService.isClickEnabled = false
                setButtonStopped()
            }
        }

        floatingView.findViewById<Button>(R.id.btnClose).setOnClickListener { stopSelf() }
        windowManager.addView(floatingView, params)
    }

    private fun startAutoClick(svc: GarenaAccessibilityService) {
        GarenaAccessibilityService.isClickEnabled = true
        btnStartStop?.text = "■ STOP"
        btnStartStop?.setBackgroundColor(0xFFE53935.toInt())

        serviceScope.launch {
            // Open Via app
            val pkg = VIA_PACKAGES.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
            if (pkg == null) {
                Toast.makeText(this@FloatingWindowService, "⚠ Via not found!", Toast.LENGTH_LONG).show()
                GarenaAccessibilityService.isClickEnabled = false
                setButtonStopped()
                return@launch
            }
            val intent = packageManager.getLaunchIntentForPackage(pkg)!!
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(intent)
            Toast.makeText(this@FloatingWindowService, "▶ Opening Via...", Toast.LENGTH_SHORT).show()

            // Wait for Via to fully open (Smart-AutoClicker approach: wait then gesture)
            delay(2500)

            if (GarenaAccessibilityService.isClickEnabled) {
                // Direct gesture click using Smart-AutoClicker's suspendCoroutine pattern
                val result = svc.doGestureClick(540, 1800)
                if (!result) {
                    // Retry once
                    delay(1000)
                    svc.doGestureClick(540, 1750)
                }
                setButtonStopped()
            }
        }
    }

    private fun setButtonStopped() {
        btnStartStop?.text = "▶ START"
        btnStartStop?.setBackgroundColor(0xFF43A047.toInt())
    }

    override fun onDestroy() {
        super.onDestroy()
        GarenaAccessibilityService.isClickEnabled = false
        serviceScope.cancel()
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
