package com.example.parentalchild

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import kotlin.concurrent.thread

/**
 * Doimiy fon xizmati: har 1 soniyada
 *  1) heartbeat yuboradi (server "online" deb bilishi uchun)
 *  2) PENDING so'rovlarni so'raydi va ularga javob qaytaradi
 *
 * Diqqat: 1 soniyalik interval batareyani tez sarflaydi va Android Doze
 * rejimida baribir kechikishi mumkin. Foreground service + doimiy
 * notification shu tufayl ishlatiladi - shunda tizim xizmatni o'ldirmaydi.
 */
class MonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var api: Api
    private var deviceId: String = ""
    private var running = false

    private val tickRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            doTick()
            handler.postDelayed(this, 1000L) // 1 soniya
        }
    }

    override fun onCreate() {
        super.onCreate()
        api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
        deviceId = getSharedPreferences("default", 0).getString("deviceId", null)
            ?: getPreferences(0).getString("deviceId", null).orEmpty()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra("deviceId")?.let { if (it.isNotBlank()) deviceId = it }
        startForeground(
            101,
            NotificationCompat.Builder(this, "monitor")
                .setContentTitle("Family Guard")
                .setContentText("Monitoring faol")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setOngoing(true)
                .build()
        )
        if (!running) {
            running = true
            handler.post(tickRunnable)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        handler.removeCallbacks(tickRunnable)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "monitor",
                "Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun doTick() {
        if (deviceId.isBlank()) return
        thread {
            try {
                api.heartbeat(deviceId)
                val json = api.pending(deviceId)
                val requests = JSONArray(if (json.isBlank()) "[]" else
                    org.json.JSONObject(json).optJSONArray("requests")?.toString() ?: "[]"
                )
                for (i in 0 until requests.length()) {
                    val r = requests.getJSONObject(i)
                    handleRequest(r.getString("_id"), r.getString("type"))
                }
            } catch (_: Exception) {
                // tarmoq xatosi - keyingi tick'da qayta urinadi
            }
        }
    }

    private fun handleRequest(id: String, type: String) {
        try {
            when (type) {
                "SCREENSHOT", "SCREEN_SHARE" -> {
                    // Bu turdagi so'rovlar uchun MediaProjection ruxsati (ekranga chiqadigan
                    // tizim dialog oynasi) foydalanuvchidan oldindan olingan bo'lishi kerak.
                    // Ruxsat mavjud bo'lmasa, so'rovni FAILED deb belgilaymiz - yashirincha
                    // hech qanday capture qilinmaydi.
                    val hasPermission = ScreenCaptureService.hasActiveProjection()
                    if (hasPermission) {
                        api.status(id, "RUNNING")
                        val startIntent = Intent(this, ScreenCaptureService::class.java)
                            .putExtra("requestId", id)
                        startService(startIntent)
                    } else {
                        api.status(id, "FAILED", null)
                    }
                }
                "CAMERA_FRONT", "CAMERA_BACK" -> {
                    // Kamera capture uchun ham runtime CAMERA permission oldindan
                    // berilgan bo'lishi shart (MainActivity orqali).
                    api.status(id, "FAILED", null) // TODO: haqiqiy kamera capture implementatsiyasi
                }
                else -> api.status(id, "FAILED", null)
            }
        } catch (_: Exception) {
            try { api.status(id, "FAILED", null) } catch (_: Exception) {}
        }
    }
}
