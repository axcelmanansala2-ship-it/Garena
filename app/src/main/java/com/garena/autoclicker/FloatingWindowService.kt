package com.garena.autoclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
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
    private var isRunning = false
    private var detectionRunnable: Runnable? = null
    private var viaLaunched = false

    companion object {
        private val VIA_PACKAGES = listOf("mark.via.gp", "mark.via", "mark.via.sh")
    }

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
        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Garena Auto Clicker")
            .setContentText("Running — detecting Via app")
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
            x = 100; y = 300
        }

        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var isDragging = false
        var dragStartTime = 0L

        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    dragStartTime = System.currentTimeMillis()
                    isDragging = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx; params.y = initialY + dy
                        windowManager.updateViewLayout(floatingView, params)
                    }; true
                }
                else -> false
            }
        }

        val btnStartStop = floatingView.findViewById<Button>(R.id.btnStartStop)
        btnStartStop.setOnClickListener {
            if (!isRunning) {
                isRunning = true
                viaLaunched = false
                btnStartStop.text = "■ STOP"
                btnStartStop.setBackgroundColor(0xFFE53935.toInt())
                startSmartDetection()
                Toast.makeText(this, "Auto-click started!", Toast.LENGTH_SHORT).show()
            } else {
                stopAutoClick(btnStartStop)
            }
        }

        floatingView.findViewById<Button>(R.id.btnClose).setOnClickListener { stopSelf() }
        windowManager.addView(floatingView, params)
    }

    private fun startSmartDetection() {
        // Step 1: Launch Via immediately
        launchViaApp()

        // Step 2: Smart polling — detect when Via is in foreground, then navigate
        detectionRunnable = object : Runnable {
            override fun run() {
                if (!isRunning) return
                val foregroundPkg = getForegroundApp()
                if (foregroundPkg != null && VIA_PACKAGES.any { it == foregroundPkg }) {
                    if (!viaLaunched) {
                        viaLaunched = true
                        // Via is now in foreground — navigate to homepage directly
                        navigateViaToHomepage(foregroundPkg)
                        handler.postDelayed({ stopAutoClickIfDone() }, 2000)
                        return
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(detectionRunnable!!, 1500)
    }

    private fun getForegroundApp(): String? {
        return try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 5000, now)
            stats?.filter { it.lastTimeUsed > 0 }
                ?.maxByOrNull { it.lastTimeUsed }
                ?.packageName
        } catch (e: Exception) { null }
    }

    private fun launchViaApp() {
        val viaPkg = VIA_PACKAGES.firstOrNull { pkg ->
            packageManager.getLaunchIntentForPackage(pkg) != null
        }
        if (viaPkg != null) {
            val intent = packageManager.getLaunchIntentForPackage(viaPkg)!!
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        } else {
            Toast.makeText(this, "Via app not found!", Toast.LENGTH_SHORT).show()
            isRunning = false
        }
    }

    private fun navigateViaToHomepage(pkg: String) {
        // Open Via's homepage using intent — no click needed, bypasses all restrictions
        try {
            val homepageIntent = Intent(Intent.ACTION_VIEW, Uri.parse("about:blank")).apply {
                setPackage(pkg)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(homepageIntent)
        } catch (_: Exception) {
            // Fallback: re-launch the app main activity
            packageManager.getLaunchIntentForPackage(pkg)?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(it)
            }
        }
        Toast.makeText(this, "✅ Via detected! Homepage opened.", Toast.LENGTH_SHORT).show()
    }

    private fun stopAutoClickIfDone() {
        floatingView.findViewById<Button>(R.id.btnStartStop)?.let { btn ->
            stopAutoClick(btn)
        }
    }

    private fun stopAutoClick(btn: Button) {
        isRunning = false
        viaLaunched = false
        detectionRunnable?.let { handler.removeCallbacks(it) }
        btn.text = "▶ START"
        btn.setBackgroundColor(0xFF43A047.toInt())
        Toast.makeText(this, "Stopped.", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        detectionRunnable?.let { handler.removeCallbacks(it) }
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
