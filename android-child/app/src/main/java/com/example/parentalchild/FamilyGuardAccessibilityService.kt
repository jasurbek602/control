package com.example.parentalchild

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.Executors
import kotlin.coroutines.resume

class FamilyGuardAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FamilyGuardAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null

        /**
         * Ekranni rasmga olish.
         * MUHIM: Bu funksiya Coroutine (suspend) orqali fonda chaqirilishi shart!
         */
        suspend fun takeShot(): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val svc = instance ?: return null
            return svc.captureScreen()
        }
    }

    private val screenshotExecutor = Executors.newSingleThreadExecutor()

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Tizim UI ipini band qilmaslik uchun bo'sh qoldiriladi
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        screenshotExecutor.shutdown()
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private suspend fun captureScreen(): String? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            try {
                takeScreenshot(
                    android.view.Display.DEFAULT_DISPLAY,
                    screenshotExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            var base64Result: String? = null
                            val buffer = screenshot.hardwareBuffer
                            try {
                                val bitmap = Bitmap.wrapHardwareBuffer(
                                    buffer,
                                    screenshot.colorSpace
                                )?.copy(Bitmap.Config.ARGB_8888, false)

                                if (bitmap != null) {
                                    ByteArrayOutputStream().use { out ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
                                        bitmap.recycle()
                                        base64Result = Base64.getEncoder().encodeToString(out.toByteArray())
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                buffer.close() // HardwareBuffer har doim yopilishi shart
                            }

                            if (continuation.isActive) {
                                continuation.resume(base64Result)
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                e.printStackTrace()
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }
}
