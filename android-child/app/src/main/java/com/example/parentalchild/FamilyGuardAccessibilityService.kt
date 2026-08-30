package com.example.parentalchild

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

class FamilyGuardAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FamilyGuardAccessibilityService? = null

        fun isEnabled(): Boolean = instance != null

        fun takeShot(): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
            val svc = instance ?: return null
            return svc.captureScreen()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun captureScreen(): String? {
        val latch = CountDownLatch(1)
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
