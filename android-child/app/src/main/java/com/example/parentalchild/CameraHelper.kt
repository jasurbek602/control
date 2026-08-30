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
        val latch   = CountDownLatch(1)
        val th      = HandlerThread("cam").also { it.start() }
        val handler = Handler(th.looper)

        try {
            val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id  = mgr.cameraIdList.firstOrNull {
                mgr.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == facing
            } ?: return null

            val reader = ImageReader.newInstance(1280, 720, ImageFormat.JPEG, 2)

            reader.setOnImageAvailableListener({ r ->
                val img   = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                val buf   = img.planes[0].buffer
                val bytes = ByteArray(buf.remaining()).also { b -> buf.get(b) }
                img.close()
                if (result == null) {
                    result = Base64.getEncoder().encodeToString(bytes)
                    latch.countDown()
                }
            }, handler)

            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    // Preview request — kamera isishi uchun
                    val previewReq = cam.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                    }.build()

                    // Capture request — asosiy rasm
                    val captureReq = cam.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                        addTarget(reader.surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                        set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                    }.build()

                    cam.createCaptureSession(
                        listOf(reader.surface),
                        object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                try {
                                    // 1.5 soniya preview yuborib kamera isitamiz
                                    session.setRepeatingRequest(previewReq, null, handler)
                                    handler.postDelayed({
                                        try {
                                            session.stopRepeating()
                                            session.capture(captureReq,
                                                object : CameraCaptureSession.CaptureCallback() {
                                                    override fun onCaptureCompleted(
                                                        s: CameraCaptureSession,
                                                        r: CaptureRequest,
                                                        res: TotalCaptureResult
                                                    ) {
                                                        handler.postDelayed({ cam.close() }, 500)
                                                    }
                                                    override fun onCaptureFailed(
                                                        s: CameraCaptureSession,
                                                        r: CaptureRequest,
                                                        f: CaptureFailure
                                                    ) {
                                                        cam.close(); latch.countDown()
                                                    }
                                                }, handler)
                                        } catch (_: Exception) {
                                            cam.close(); latch.countDown()
                                        }
                                    }, 1500) // 1.5 soniya kutish
                                } catch (_: Exception) {
                                    cam.close(); latch.countDown()
                                }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) {
                                cam.close(); latch.countDown()
                            }
                        }, handler)
                }
                override fun onDisconnected(cam: CameraDevice) { cam.close(); latch.countDown() }
                override fun onError(cam: CameraDevice, e: Int) { cam.close(); latch.countDown() }
            }, handler)

            latch.await(20, TimeUnit.SECONDS)
        } catch (_: Exception) {
            latch.countDown()
        } finally {
            th.quitSafely()
        }
        return result
    }
}
