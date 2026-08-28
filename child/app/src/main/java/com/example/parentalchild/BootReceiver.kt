package com.example.parentalchild

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Telefon yoqilganda YOKI internet ulanishida ishga tushadi
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.LOCKED_BOOT_COMPLETED" ||
            action == "android.net.conn.CONNECTIVITY_CHANGE"
        ) {
            // MainActivity.getPreferences(0) == getSharedPreferences("MainActivity", 0)
            val prefs = context.getSharedPreferences("MainActivity", Context.MODE_PRIVATE)
            val deviceId = prefs.getString("deviceId", null)

            // deviceId bo'lmasa — foydalanuvchi hali ilovani ochmagani demak
            if (deviceId.isNullOrEmpty()) return

            context.startForegroundService(
                Intent(context, ScreenCaptureService::class.java)
                    .putExtra("deviceId", deviceId)
            )
        }
    }
}
