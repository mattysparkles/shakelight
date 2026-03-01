package com.example.shakelight

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private val channelName = "shakelight/service"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, channelName)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startService" -> {
                        startShakeService()
                        result.success(true)
                    }

                    "stopService" -> {
                        stopService(Intent(this, ShakeLightService::class.java))
                        result.success(true)
                    }

                    "isRunning" -> {
                        result.success(ShakeLightPrefs(this).isServiceRunning())
                    }

                    "updateSettings" -> {
                        val threshold = call.argument<Double>("threshold") ?: 11.5
                        val cooldownMs = call.argument<Int>("cooldownMs") ?: 1200
                        val startOnBoot = call.argument<Boolean>("startOnBoot") ?: false

                        val prefs = ShakeLightPrefs(this)
                        prefs.saveSettings(threshold.toFloat(), cooldownMs, startOnBoot)

                        val updateIntent = Intent(this, ShakeLightService::class.java).apply {
                            action = ShakeLightService.ACTION_UPDATE_SETTINGS
                        }
                        startService(updateIntent)
                        result.success(true)
                    }

                    "requestPermissions" -> {
                        requestPermissionsIfNeeded()
                        result.success(true)
                    }

                    "requestIgnoreBatteryOptimizations" -> {
                        openIgnoreBatteryOptimizationSettings()
                        result.success(true)
                    }

                    else -> result.notImplemented()
                }
            }
    }

    private fun startShakeService() {
        val intent = Intent(this, ShakeLightService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestPermissionsIfNeeded() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 1001)
        }
    }

    private fun openIgnoreBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            return
        }

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        intent.data = Uri.parse("package:$packageName")
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}
