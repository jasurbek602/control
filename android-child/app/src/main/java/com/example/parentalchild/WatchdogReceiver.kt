package com.example.parentalchild

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Navbatdagi alarmni qayta rejalashtirish (Android 12+ talabi uchun)
        schedule(context)

        // Service o'chib qolgan bo'lsa — qayta ishga tushirish
        if (!ScreenCaptureService.isRunning) {
            val deviceId = context.getSharedPreferences("fg", Context.MODE_PRIVATE)
                .getString("deviceId", null) ?: return

            val si = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra("deviceId", deviceId)
            }

            try {
                ContextCompat.startForegroundService(context, si)
            } catch (e: Exception) {
                // Background start restriction istisnolarini ushlash
                e.printStackTrace()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 77
        private const val INTERVAL_MS = 3 * 60 * 1000L // 3 daqiqa

        // Har 3 daqiqada bir tekshiruvchi alarm o'rnatish
        fun schedule(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val pi = getPendingIntent(context)
            val triggerAtMs = System.currentTimeMillis() + INTERVAL_MS

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // Doze mode (so'nish rejimi) paytida ham uyg'otish uchun
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                }
            } catch (_: Exception) {
                // Exact alarm permission yetishmovchiligi bo'lsa fallback
                try {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAtMs, pi)
                } catch (_: Exception) {}
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            am.cancel(getPendingIntent(context))
        }

        private fun getPendingIntent(context: Context): PendingIntent {
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            return PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                Intent(context, WatchdogReceiver::class.java),
                flags
            )
        }
    }
}
