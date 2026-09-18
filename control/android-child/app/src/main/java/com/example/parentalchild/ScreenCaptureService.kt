package com.example.parentalchild

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.concurrent.thread

/**
 * Network/command foreground service.
 *
 * It starts only as DATA_SYNC. MediaProjection is added only after the user has
 * granted screen-capture consent. Camera commands are intentionally removed.
 */
class ScreenCaptureService : Service() {
    companion object {
        private const val CHANNEL_ID = "family_guard_monitor"
        private const val NOTIFICATION_ID = 1001

        @Volatile var isRunning = false
        @Volatile var instance: ScreenCaptureService? = null
        fun captureScreen(): String? = instance?.capture()
    }

    private var projection: MediaProjection? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var w = 1080
    private var h = 1920
    private var dpi = 320

    private lateinit var api: Api
    private lateinit var deviceId: String
    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        instance = this
        isRunning = true
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Family Guard monitoring", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val devId = intent?.getStringExtra("deviceId")
            ?: getSharedPreferences("fg", MODE_PRIVATE).getString("deviceId", null)

        if (devId != null) {
            deviceId = devId
            api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
            getSharedPreferences("fg", MODE_PRIVATE).edit().putString("deviceId", devId).apply()
        }

        startAsDataSyncForeground()

        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("code", Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent?.getParcelableExtra("code")
        }

        if (intent?.getBooleanExtra("enableProjection", false) == true &&
            code == Activity.RESULT_OK && data != null) {
            enableProjection(code, data)
        }

        if (!running && ::deviceId.isInitialized) {
            running = true
            startHeartbeatLoop()
            startPollLoop()
        }
        return START_STICKY
    }

    private fun notification(): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Family Guard")
            .setContentText("Monitoring faol")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun startAsDataSyncForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                @Suppress("DEPRECATION") startForeground(NOTIFICATION_ID, notification())
            }
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun enableProjection(resultCode: Int, data: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    notification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }

            val wm = getSystemService(WindowManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = wm.currentWindowMetrics.bounds
                w = bounds.width()
                h = bounds.height()
            } else {
                val dm = resources.displayMetrics
                w = dm.widthPixels
                h = dm.heightPixels
            }
            dpi = resources.displayMetrics.densityDpi

            projection?.stop()
            display?.release()
            reader?.close()

            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            display = projection?.createVirtualDisplay(
                "FamilyGuard", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, null
            )
        } catch (_: Exception) {
            projection?.stop()
            projection = null
            display?.release()
            display = null
            reader?.close()
            reader = null
        }
    }

    private fun startHeartbeatLoop() {
        thread(name = "family-guard-heartbeat") {
            try { api.register(deviceId, "Child device") } catch (_: Exception) {}
            while (running) {
                try {
                    val battery = getSystemService(BatteryManager::class.java)
                        .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    api.heartbeat(deviceId, battery)
                } catch (_: Exception) {}
                Thread.sleep(15_000)
            }
        }
    }

    private fun startPollLoop() {
        thread(name = "family-guard-poll") {
            Thread.sleep(2_000)
            while (running) {
                try {
                    val req = api.pending(deviceId)
                    if (req != null) handleRequest(req)
                } catch (_: Exception) {}
                Thread.sleep(5_000)
            }
        }
    }

    private fun handleRequest(req: JSONObject) {
        val id = req.optString("_id")
        val type = req.optString("type")
        if (id.isBlank()) return

        thread(name = "request-$type") {
            try {
                when (type) {
                    "SCREENSHOT" -> {
                        val b64 = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                                FamilyGuardAccessibilityService.isEnabled() ->
                                FamilyGuardAccessibilityService.takeShot()
                            projection != null && reader != null -> capture()
                            else -> null
                        }
                        if (b64 != null) api.updateStatus(id, "DONE", api.uploadImage(b64))
                        else api.updateStatus(id, "FAILED")
                    }
                    "LOCATION" -> {
                        val location = LocationHelper(this).getLocation()
                        if (location != null) api.updateStatus(id, "DONE", "${location.first},${location.second}")
                        else api.updateStatus(id, "FAILED")
                    }
                    "APP_LIST" -> {
                        val json = AppHelper(this).getInstalledApps()
                        api.updateStatus(id, "DONE", api.uploadJson(json))
                    }
                    "APP_USAGE" -> {
                        val json = AppHelper(this).getAppUsage()
                        if (json == "[]") api.updateStatus(id, "FAILED")
                        else api.updateStatus(id, "DONE", api.uploadJson(json))
                    }
                    else -> api.updateStatus(id, "FAILED")
                }
            } catch (_: Exception) {
                try { api.updateStatus(id, "FAILED") } catch (_: Exception) {}
            }
        }
    }

    fun capture(): String? = try {
        Thread.sleep(250)
        val image = reader?.acquireLatestImage() ?: return null
        val plane = image.planes[0]
        val paddedWidth = w + (plane.rowStride - plane.pixelStride * w) / plane.pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, h, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(plane.buffer)
        image.close()

        val crop = Bitmap.createBitmap(bitmap, 0, 0, w, h)
        bitmap.recycle()
        val out = ByteArrayOutputStream()
        crop.compress(Bitmap.CompressFormat.JPEG, 70, out)
        crop.recycle()
        Base64.getEncoder().encodeToString(out.toByteArray())
    } catch (_: Exception) {
        null
    }

    override fun onDestroy() {
        running = false
        isRunning = false
        instance = null
        display?.release()
        reader?.close()
        projection?.stop()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onTimeout(startId: Int, fgsType: Int) {
        running = false
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
