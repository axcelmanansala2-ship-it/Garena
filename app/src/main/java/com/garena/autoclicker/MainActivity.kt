package com.garena.autoclicker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.garena.autoclicker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this))
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            else Toast.makeText(this, "Already granted!", Toast.LENGTH_SHORT).show()
        }

        binding.btnGrantAccessibility.setOnClickListener {
            // Deep-link directly to this service's settings page
            try {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    val bundle = Bundle()
                    val component = "$packageName/.GarenaAccessibilityService"
                    bundle.putString(":settings:fragment_args_key", component)
                    putExtra(":settings:fragment_args_key", component)
                    putExtra(":settings:show_fragment_args", bundle)
                }
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Toast.makeText(this,
                "Find 'Garena Auto Clicker' → tap it → turn ON",
                Toast.LENGTH_LONG).show()
        }

        binding.btnAllowRestricted.setOnClickListener {
            // Open app info page where user can tap ⋮ → Allow restricted settings
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName"))
                startActivity(intent)
                Toast.makeText(this,
                    "Tap ⋮ (3-dot menu) → Allow restricted settings → go back to Accessibility",
                    Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Go to Settings → Apps → Garena Auto Clicker", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnLaunchOverlay.setOnClickListener {
            startForegroundService(Intent(this, FloatingWindowService::class.java))
            Toast.makeText(this, "Floating window started!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        // Poll every second while on screen
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isFinishing) { updateStatus(); handler.postDelayed(this, 1000) }
            }
        }, 1000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessOk = isAccessibilityEnabled()

        binding.tvOverlayStatus.text = if (overlayOk) "✅ Overlay: Granted" else "❌ Overlay: Not Granted"
        binding.tvAccessibilityStatus.text = if (accessOk) "✅ Accessibility: Enabled ← READY!" else "❌ Accessibility: Disabled"
        binding.tvAccessibilityStatus.setTextColor(if (accessOk) 0xFF4CAF50.toInt() else 0xFFFF5252.toInt())
        binding.btnLaunchOverlay.isEnabled = overlayOk
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        return am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { it.id.contains(packageName) }
    }
}
