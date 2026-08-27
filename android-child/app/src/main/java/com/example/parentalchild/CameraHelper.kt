package com.example.parentalchild

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CameraHelper(private val ctx: Context) {

    fun capturePhoto(facing: Int): String? {
        var result: String? = null
        val latch = CountDownLatch(1)
        val th = HandlerThread("cam").also { it.start() }
        val handler = Handler(th.looper)

        try {
            val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = mgr.cameraIdList.firstOrNull {
                mgr.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == facing
            } ?: return null

            val reader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 1)
            reader.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buf = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { b -> buf.get(b) }
                img.close()
                result = Base64.getEncoder().encodeToString(bytes)
                latch.countDown()
            }, handler)

            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    val req = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    }.build()
                    cam.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                        override fun onConfigured(s: CameraCaptureSession) {
                            s.capture(req, object : CameraCaptureSession.CaptureCallback() {
                                override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, res: TotalCaptureResult) { cam.close() }
                            }, handler)
                        }
                        override fun onConfigureFailed(s: CameraCaptureSession) { cam.close(); latch.countDown() }
                    }, handler)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); latch.countDown() }
                override fun onError(cam: CameraDevice, e: Int) { cam.close(); latch.countDown() }
            }, handler)

            latch.await(15, TimeUnit.SECONDS)
        } catch (_: Exception) {
            latch.countDown()
        } finally {
            th.quitSafely()
        }
        return result
    }
}
