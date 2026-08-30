package com.example.parentalchild

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Service o'chib qolgan bo'lsa — qayta ishga tushir
        if (!ScreenCaptureService.isRunning) {
            val deviceId = context.getSharedPreferences("fg", Context.MODE_PRIVATE)
                .getString("deviceId", null) ?: return

            val si = Intent(context, ScreenCaptureService::class.java)
                .putExtra("deviceId", deviceId)
            ContextCompat.startForegroundService(context, si)
        }
    }

    companion object {
        private const val REQUEST_CODE = 77

        // Har 3 daqiqada bir tekshiruvchi alarm o'rnatish
        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = getPendingIntent(context)
            try {
                am.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 10_000,
                    3 * 60 * 1000L, // har 3 daqiqa
                    pi
                )
            } catch (_: Exception) {}
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            am.cancel(getPendingIntent(context))
        }

        private fun getPendingIntent(context: Context) =
            PendingIntent.getBroadcast(
                context, REQUEST_CODE,
                Intent(context, WatchdogReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
    }
}
