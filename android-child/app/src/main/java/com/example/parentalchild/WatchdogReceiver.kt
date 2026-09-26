package com.example.parentalchild

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class WatchdogReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!ScreenCaptureService.isRunning) {
            val deviceId = context.getSharedPreferences("fg", Context.MODE_PRIVATE)
                .getString("deviceId", null) ?: return
            ContextCompat.startForegroundService(
                context,
                Intent(context, ScreenCaptureService::class.java)
                    .putExtra("deviceId", deviceId)
            )
        }
    }

    companion object {
        private const val RC = 77

        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            try {
                am.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + 10_000,
                    3 * 60 * 1000L,
                    getPi(context)
                )
            } catch (_: Exception) {}
        }

        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java).cancel(getPi(context))
        }

        private fun getPi(context: Context) = PendingIntent.getBroadcast(
            context, RC,
            Intent(context, WatchdogReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
