package com.garena.autoclicker

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
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
        binding.btnGrantUsage.setOnClickListener { requestUsageStatsPermission() }
        binding.btnLaunchOverlay.setOnClickListener { launchFloatingWindow() }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val overlayOk = Settings.canDrawOverlays(this)
        val usageOk = hasUsageStatsPermission()

        binding.tvOverlayStatus.text = if (overlayOk) "✅ Overlay: Granted" else "❌ Overlay: Not Granted"
        binding.tvUsageStatus.text = if (usageOk) "✅ Usage Access: Granted" else "❌ Usage Access: Not Granted"
        binding.btnLaunchOverlay.isEnabled = overlayOk
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        } else {
            Toast.makeText(this, "Overlay already granted!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestUsageStatsPermission() {
        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        Toast.makeText(this, "Enable usage access for Garena Auto Clicker", Toast.LENGTH_LONG).show()
    }

    private fun launchFloatingWindow() {
        startForegroundService(Intent(this, FloatingWindowService::class.java))
        Toast.makeText(this, "Floating window started!", Toast.LENGTH_SHORT).show()
        finish()
    }
}
