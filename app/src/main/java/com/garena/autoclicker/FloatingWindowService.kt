package com.garena.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View
    private val handler = Handler(Looper.getMainLooper())
    private val VIA_PACKAGES = listOf("mark.via.gp", "mark.via", "mark.via.sh")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        showFloatingWindow()
    }

    private fun startForegroundNotification() {
        val channelId = "garena_overlay"
        val channel = NotificationChannel(channelId, "Garena Auto Clicker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notif: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Garena Auto Clicker")
            .setContentText("Overlay active — tap START to auto-click Via")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()
        startForeground(1, notif)
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
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 100; y = 300 }

        var ix = 0; var iy = 0; var itx = 0f; var ity = 0f; var dragging = false
        floatingView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix = params.x; iy = params.y; itx = e.rawX; ity = e.rawY; dragging = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - itx).toInt(); val dy = (e.rawY - ity).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) dragging = true
                    if (dragging) { params.x = ix + dx; params.y = iy + dy; windowManager.updateViewLayout(floatingView, params) }
                    true
                }
                else -> false
            }
        }

        val btnStartStop = floatingView.findViewById<Button>(R.id.btnStartStop)
        btnStartStop.setOnClickListener {
            val svc = GarenaAccessibilityService.instance
            if (!GarenaAccessibilityService.isClickEnabled) {
                if (svc == null) {
                    Toast.makeText(this, "⚠ Enable Accessibility first!\nSettings → Accessibility → Garena Auto Clicker → ON", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                GarenaAccessibilityService.isClickEnabled = true
                btnStartStop.text = "■ STOP"
                btnStartStop.setBackgroundColor(0xFFE53935.toInt())
                launchViaApp()
                Toast.makeText(this, "▶ Auto-click started! Opening Via...", Toast.LENGTH_SHORT).show()
                // Auto-stop after 10 seconds if no click happened
                handler.postDelayed({
                    if (GarenaAccessibilityService.isClickEnabled) {
                        GarenaAccessibilityService.isClickEnabled = false
                        btnStartStop.text = "▶ START"
                        btnStartStop.setBackgroundColor(0xFF43A047.toInt())
                    }
                }, 10000)
            } else {
                GarenaAccessibilityService.isClickEnabled = false
                btnStartStop.text = "▶ START"
                btnStartStop.setBackgroundColor(0xFF43A047.toInt())
                Toast.makeText(this, "Stopped.", Toast.LENGTH_SHORT).show()
            }
        }

        floatingView.findViewById<Button>(R.id.btnClose).setOnClickListener { stopSelf() }
        windowManager.addView(floatingView, params)
    }

    private fun launchViaApp() {
        val pkg = VIA_PACKAGES.firstOrNull { packageManager.getLaunchIntentForPackage(it) != null }
        if (pkg != null) {
            val intent = packageManager.getLaunchIntentForPackage(pkg)!!
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Via app not found!", Toast.LENGTH_SHORT).show()
            GarenaAccessibilityService.isClickEnabled = false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GarenaAccessibilityService.isClickEnabled = false
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
