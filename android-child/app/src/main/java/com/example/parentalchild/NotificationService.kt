package com.example.parentalchild

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONArray
import org.json.JSONObject

class NotificationService : NotificationListenerService() {

    companion object {
        var instance: NotificationService? = null

        // Oxirgi 50 ta bildirishnomani saqlab turadi
        private val buffer = ArrayDeque<JSONObject>(50)

        fun getRecent(): String {
            synchronized(buffer) {
                val arr = JSONArray()
                buffer.forEach { arr.put(it) }
                return arr.toString()
            }
        }
    }

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            val extras = sbn.notification?.extras ?: return
            val title  = extras.getCharSequence("android.title")?.toString() ?: return
            val text   = extras.getCharSequence("android.text")?.toString() ?: ""

            val obj = JSONObject().apply {
                put("pkg",   sbn.packageName ?: "")
                put("title", title)
                put("text",  text)
                put("time",  sbn.postTime)
            }

            synchronized(buffer) {
                if (buffer.size >= 50) buffer.removeFirst()
                buffer.addLast(obj)
            }
        } catch (_: Exception) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
