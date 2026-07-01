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
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: android.view.View
    private var isRunning = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        showFloatingWindow()
    }

    private fun startForegroundNotification() {
        val channelId = "garena_overlay"
        val channel = NotificationChannel(channelId, "Garena Auto Clicker", NotificationManager.IMPORTANCE_LOW)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Garena Auto Clicker")
            .setContentText("Floating overlay is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .build()

        startForeground(1, notification)
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
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 300
        }

        // Drag support
        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        val btnStartStop = floatingView.findViewById<Button>(R.id.btnStartStop)
        btnStartStop.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                btnStartStop.text = "■ STOP"
                btnStartStop.setBackgroundColor(0xFFE53935.toInt())
                GarenaAccessibilityService.instance?.startAutoClick()
                launchViaApp()
                Toast.makeText(this, "Auto-click started!", Toast.LENGTH_SHORT).show()
            } else {
                isRunning = false
                btnStartStop.text = "▶ START"
                btnStartStop.setBackgroundColor(0xFF43A047.toInt())
                GarenaAccessibilityService.instance?.stopAutoClick()
                Toast.makeText(this, "Auto-click stopped.", Toast.LENGTH_SHORT).show()
            }
        }

        val btnClose = floatingView.findViewById<Button>(R.id.btnClose)
        btnClose.setOnClickListener {
            stopSelf()
        }

        windowManager.addView(floatingView, params)
    }

    private fun launchViaApp() {
        val intent = packageManager.getLaunchIntentForPackage("mark.via.gp")
            ?: packageManager.getLaunchIntentForPackage("mark.via")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Via app not found!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
