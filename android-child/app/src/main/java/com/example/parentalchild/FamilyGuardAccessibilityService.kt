package com.example.parentalchild

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class FamilyGuardAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FamilyGuardAccessibilityService? = null

        // ✅ Notificationlar saqlanadigan joy (xotirada, max 500 ta)
        private val notifications = mutableListOf<org.json.JSONObject>()

        fun isEnabled(): Boolean = instance != null

        fun takeShot(): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val svc = instance ?: return null
            return svc.captureScreen()
        }

        // ✅ ScreenCaptureService "NOTIFICATION_LOGS" so'rovida shu funksiyani chaqiradi
        fun getNotifications(): String {
            val arr = org.json.JSONArray()
            synchronized(notifications) {
                notifications.takeLast(100).forEach { arr.put(it) }
            }
            return arr.toString()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    // ✅ Har bir notification kelganda bu yerda ushlanadi
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        val notification = event.parcelableData as? Notification ?: return
        try {
            val extras    = notification.extras
            val title     = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
            val text      = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()  ?: ""
            val pkg       = event.packageName?.toString() ?: ""
            val appName   = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            } catch (_: Exception) { pkg }
            val postTime  = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date())

            val obj = org.json.JSONObject().apply {
                put("packageName", pkg)
                put("appName",     appName)
                put("title",       title)
                put("text",        text)
                put("postTime",    postTime)
            }

            synchronized(notifications) {
                notifications.add(obj)
                // 500 dan oshsa eng eskisini o'chiramiz
                if (notifications.size > 500) notifications.removeAt(0)
            }
        } catch (_: Exception) {}
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreen(): String? {
        val latch  = CountDownLatch(1)
        var result: String? = null
        val executor = Executor { it.run() }

        takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            executor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val bitmap = Bitmap.wrapHardwareBuffer(
                            screenshot.hardwareBuffer,
                            screenshot.colorSpace
                        )?.copy(Bitmap.Config.ARGB_8888, false)
                        screenshot.hardwareBuffer.close()
                        if (bitmap != null) {
                            val out = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                            bitmap.recycle()
                            result = Base64.getEncoder().encodeToString(out.toByteArray())
                        }
                    } catch (_: Exception) {}
                    latch.countDown()
                }

                override fun onFailure(errorCode: Int) {
                    latch.countDown()
                }
            }
        )

        latch.await(10, TimeUnit.SECONDS)
        return result
    }
}
