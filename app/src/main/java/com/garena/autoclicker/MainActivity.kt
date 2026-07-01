package com.garena.autoclicker

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.garena.autoclicker.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGrantOverlay.setOnClickListener { requestOverlayPermission() }
        binding.btnGrantAccessibility.setOnClickListener { requestAccessibilityPermission() }
        binding.btnLaunchOverlay.setOnClickListener { launchFloatingWindow() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val accessibilityOk = isAccessibilityEnabled()

        binding.tvOverlayStatus.text = if (overlayOk) "✅ Overlay: Granted" else "❌ Overlay: Not Granted"
        binding.tvAccessibilityStatus.text = if (accessibilityOk) "✅ Accessibility: Enabled" else "❌ Accessibility: Disabled"
        binding.btnLaunchOverlay.isEnabled = overlayOk && accessibilityOk
    }

    private fun isAccessibilityEnabled(): Boolean {
        val am = getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabled = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        return enabled.any { it.id.contains(packageName) }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivity(intent)
        } else {
            Toast.makeText(this, "Overlay already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestAccessibilityPermission() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
        Toast.makeText(this, "Enable 'Garena Auto Clicker' in Accessibility", Toast.LENGTH_LONG).show()
    }

    private fun launchFloatingWindow() {
        val intent = Intent(this, FloatingWindowService::class.java)
        startForegroundService(intent)
        Toast.makeText(this, "Floating window started!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
