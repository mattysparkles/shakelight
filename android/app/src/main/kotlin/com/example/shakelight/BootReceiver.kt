package com.example.shakelight

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }

        val prefs = ShakeLightPrefs(context)
        if (prefs.startOnBoot()) {
            ContextCompat.startForegroundService(context, Intent(context, ShakeLightService::class.java))
        }
    }
}
