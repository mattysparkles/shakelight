package com.example.shakelight

import android.content.Context

class ShakeLightPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("shakelight_prefs", Context.MODE_PRIVATE)

    fun saveSettings(threshold: Float, cooldownMs: Int, startOnBoot: Boolean) {
        prefs.edit()
            .putFloat(KEY_THRESHOLD, threshold)
            .putInt(KEY_COOLDOWN, cooldownMs)
            .putBoolean(KEY_START_ON_BOOT, startOnBoot)
            .apply()
    }

    fun threshold(): Float = prefs.getFloat(KEY_THRESHOLD, 11.5f)
    fun cooldownMs(): Int = prefs.getInt(KEY_COOLDOWN, 1200)
    fun startOnBoot(): Boolean = prefs.getBoolean(KEY_START_ON_BOOT, false)

    fun setServiceRunning(value: Boolean) {
        prefs.edit().putBoolean(KEY_RUNNING, value).apply()
    }

    fun isServiceRunning(): Boolean = prefs.getBoolean(KEY_RUNNING, false)

    companion object {
        private const val KEY_THRESHOLD = "threshold"
        private const val KEY_COOLDOWN = "cooldown"
        private const val KEY_START_ON_BOOT = "start_on_boot"
        private const val KEY_RUNNING = "running"
    }
}
