package com.example.parentalchild

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val deviceId = context.getSharedPreferences("fg", Context.MODE_PRIVATE)
            .getString("deviceId", null) ?: return

        // Serviceni ishga tushir
        val si = Intent(context, ScreenCaptureService::class.java)
            .putExtra("deviceId", deviceId)
        ContextCompat.startForegroundService(context, si)

        // Watchdog alarmni ham o'rnat
        WatchdogReceiver.schedule(context)
    }
}
