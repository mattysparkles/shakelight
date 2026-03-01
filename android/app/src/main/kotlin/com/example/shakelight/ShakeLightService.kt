package com.example.shakelight

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ShakeLightService : Service(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var cameraManager: CameraManager
    private lateinit var prefs: ShakeLightPrefs

    private var sensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var threshold = 11.5f
    private var cooldownMs = 1200
    private var filteredMagnitude = 0.0
    private var gravity = FloatArray(3)
    private var lastToggleAt = 0L
    private var torchOn = false
    private var torchCameraId: String? = null

    override fun onCreate() {
        super.onCreate()
        prefs = ShakeLightPrefs(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        loadSettings()
        torchCameraId = selectTorchCameraId()
        createNotificationChannel()
        startForegroundSafely(buildNotification("ShakeLight running"))
        acquireWakeLock()

        if (!registerSensor()) {
            updateNotification("No accelerometer available; service stopped")
            stopSelf()
            return
        }

        prefs.setServiceRunning(true)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_UPDATE_SETTINGS -> {
                loadSettings()
                updateNotification("ShakeLight running · th=${"%.1f".format(threshold)} cd=${cooldownMs}ms")
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        releaseWakeLock()
        prefs.setServiceRunning(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onSensorChanged(event: SensorEvent) {
        val values = event.values

        val linear = if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            values
        } else {
            gravity[0] = 0.8f * gravity[0] + 0.2f * values[0]
            gravity[1] = 0.8f * gravity[1] + 0.2f * values[1]
            gravity[2] = 0.8f * gravity[2] + 0.2f * values[2]
            floatArrayOf(values[0] - gravity[0], values[1] - gravity[1], values[2] - gravity[2])
        }

        val magnitude = sqrt(
            (linear[0] * linear[0] + linear[1] * linear[1] + linear[2] * linear[2]).toDouble(),
        )

        filteredMagnitude = 0.8 * filteredMagnitude + 0.2 * magnitude

        val now = SystemClock.elapsedRealtime()
        if (filteredMagnitude >= threshold && now - lastToggleAt >= cooldownMs) {
            lastToggleAt = now
            toggleTorch()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun registerSensor(): Boolean {
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val selected = sensor ?: return false
        return sensorManager.registerListener(this, selected, SensorManager.SENSOR_DELAY_GAME)
    }

    private fun toggleTorch() {
        val cameraId = torchCameraId ?: return
        try {
            torchOn = !torchOn
            cameraManager.setTorchMode(cameraId, torchOn)
        } catch (_: CameraAccessException) {
            torchOn = !torchOn
        } catch (_: IllegalArgumentException) {
            torchOn = !torchOn
        } catch (_: SecurityException) {
            torchOn = !torchOn
        }
    }

    private fun selectTorchCameraId(): String? {
        return try {
            cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ShakeLight::ShakeService").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun loadSettings() {
        threshold = prefs.threshold()
        cooldownMs = prefs.cooldownMs()
    }

    private fun startForegroundSafely(notification: Notification) {
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Android 13+ can throw if notifications are denied; keep running best-effort.
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            "ShakeLight Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Keeps ShakeLight running while phone is locked"

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(message: String): Notification {
        val stopIntent = Intent(this, ShakeLightService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val appPendingIntent = PendingIntent.getActivity(
            this,
            1,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("ShakeLight running")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(appPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(message: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(message))
    }

    companion object {
        const val ACTION_STOP = "com.example.shakelight.ACTION_STOP"
        const val ACTION_UPDATE_SETTINGS = "com.example.shakelight.ACTION_UPDATE_SETTINGS"
        const val CHANNEL_ID = "shakelight_channel"
        const val NOTIFICATION_ID = 7
    }
}
