package com.example.parentalchild

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.util.Log

object AppHelper {

    private var heartbeatScope: CoroutineScope? = null

    // Heartbeat siklini doimiy ishga tushirish
    fun startHeartbeatLoop(deviceId: String) {
        if (heartbeatScope != null) return

        heartbeatScope = CoroutineScope(Dispatchers.IO)
        heartbeatScope?.launch {
            while (isActive) {
                try {
                    // Serverga heartbeat yuborish (Api sinfingizdagi mos metodni yozing)
                    val response = Api.sendHeartbeat(deviceId) 
                    if (response.isSuccessful) {
                        Log.d("Heartbeat", "Online status yuborildi")
                    } else {
                        Log.e("Heartbeat", "Server xatosi: ${response.code()}")
                    }
                } catch (e: Exception) {
                    // Internet yo'qolsa yoki xatolik bo'lsa, tsikl sinib qolmaydi, davom etadi
                    Log.e("Heartbeat", "Tarmoq xatosi: ${e.message}")
                }

                // Har 10 sekundda qayta yuborib turadi
                delay(10_000)
            }
        }
    }

    fun stopHeartbeatLoop() {
        heartbeatScope = null
    }
}
