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
        var imageReader: ImageReader? = null

        try {
            val mgr = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Kamera ID ni topamiz
            val id = mgr.cameraIdList.firstOrNull {
                mgr.getCameraCharacteristics(it)
                    .get(CameraCharacteristics.LENS_FACING) == facing
            } ?: run {
                latch.countDown()
                return null
            }

            val chars = mgr.getCameraCharacteristics(id)

            // Eng yaxshi o'lchamni aniqlaymiz (1080p dan katta bo'lmasin — tezroq)
            val map   = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(ImageFormat.JPEG)
                .filter { it.width <= 1920 && it.height <= 1080 }
                .sortedByDescending { it.width * it.height }
            val size  = sizes.firstOrNull() ?: android.util.Size(1280, 720)

            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 3)

            imageReader.setOnImageAvailableListener({ r ->
                val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
                try {
                    val buf   = img.planes[0].buffer
                    val bytes = ByteArray(buf.remaining()).also { b -> buf.get(b) }
                    if (result == null) {
                        result = Base64.getEncoder().encodeToString(bytes)
                        latch.countDown()
                    }
                } finally {
                    img.close()
                }
            }, handler)

            mgr.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(cam: CameraDevice) {
                    try {
                        val surface = imageReader!!.surface

                        // Preview request — kamera isishi uchun
                        val previewReq = cam.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW
                        ).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            set(CaptureRequest.CONTROL_AWB_MODE,
                                CaptureRequest.CONTROL_AWB_MODE_AUTO)
                        }.build()

                        // Capture request — asosiy rasm
                        val captureReq = cam.createCaptureRequest(
                            CameraDevice.TEMPLATE_STILL_CAPTURE
                        ).apply {
                            addTarget(surface)
                            set(CaptureRequest.CONTROL_MODE,
                                CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AWB_MODE,
                                CaptureRequest.CONTROL_AWB_MODE_AUTO)
                            set(CaptureRequest.JPEG_QUALITY, 85.toByte())
                            set(CaptureRequest.NOISE_REDUCTION_MODE,
                                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                            set(CaptureRequest.EDGE_MODE,
                                CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                            set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1)
                        }.build()

                        cam.createCaptureSession(
                            listOf(surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        // 2 soniya preview — AE/AF/AWB moslashsin
                                        session.setRepeatingRequest(previewReq, null, handler)

                                        handler.postDelayed({
                                            try {
                                                // AF trigger
                                                val afReq = cam.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_PREVIEW
                                                ).apply {
                                                    addTarget(surface)
                                                    set(CaptureRequest.CONTROL_MODE,
                                                        CaptureRequest.CONTROL_MODE_AUTO)
                                                    set(CaptureRequest.CONTROL_AF_MODE,
                                                        CaptureRequest.CONTROL_AF_MODE_AUTO)
                                                    set(CaptureRequest.CONTROL_AF_TRIGGER,
                                                        CaptureRequest.CONTROL_AF_TRIGGER_START)
                                                    set(CaptureRequest.CONTROL_AE_MODE,
                                                        CaptureRequest.CONTROL_AE_MODE_ON)
                                                    set(CaptureRequest.CONTROL_AWB_MODE,
                                                        CaptureRequest.CONTROL_AWB_MODE_AUTO)
                                                }.build()

                                                session.capture(afReq, null, handler)

                                                // AF tugashini kutamiz (800ms)
                                                handler.postDelayed({
                                                    try {
                                                        session.stopRepeating()

                                                        // Asosiy rasmni olamiz
                                                        session.capture(
                                                            captureReq,
                                                            object : CameraCaptureSession.CaptureCallback() {
                                                                override fun onCaptureCompleted(
                                                                    s: CameraCaptureSession,
                                                                    r: CaptureRequest,
                                                                    res: TotalCaptureResult
                                                                ) {
                                                                    // Hammani yopamiz
                                                                    handler.postDelayed({
                                                                        try { s.close() } catch (_: Exception) {}
                                                                        try { cam.close() } catch (_: Exception) {}
                                                                        try { imageReader?.close() } catch (_: Exception) {}
                                                                    }, 300)
                                                                }

                                                                override fun onCaptureFailed(
                                                                    s: CameraCaptureSession,
                                                                    r: CaptureRequest,
                                                                    f: CaptureFailure
                                                                ) {
                                                                    try { s.close() } catch (_: Exception) {}
                                                                    try { cam.close() } catch (_: Exception) {}
                                                                    try { imageReader?.close() } catch (_: Exception) {}
                                                                    latch.countDown()
                                                                }
                                                            },
                                                            handler
                                                        )
                                                    } catch (e: Exception) {
                                                        try { session.close() } catch (_: Exception) {}
                                                        try { cam.close() } catch (_: Exception) {}
                                                        try { imageReader?.close() } catch (_: Exception) {}
                                                        latch.countDown()
                                                    }
                                                }, 800)
                                            } catch (e: Exception) {
                                                try { session.close() } catch (_: Exception) {}
                                                try { cam.close() } catch (_: Exception) {}
                                                try { imageReader?.close() } catch (_: Exception) {}
                                                latch.countDown()
                                            }
                                        }, 2000)

                                    } catch (e: Exception) {
                                        try { session.close() } catch (_: Exception) {}
                                        try { cam.close() } catch (_: Exception) {}
                                        try { imageReader?.close() } catch (_: Exception) {}
                                        latch.countDown()
                                    }
                                }

                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    try { s.close() } catch (_: Exception) {}
                                    try { cam.close() } catch (_: Exception) {}
                                    try { imageReader?.close() } catch (_: Exception) {}
                                    latch.countDown()
                                }
                            },
                            handler
                        )
                    } catch (e: Exception) {
                        try { cam.close() } catch (_: Exception) {}
                        try { imageReader?.close() } catch (_: Exception) {}
                        latch.countDown()
                    }
                }

                override fun onDisconnected(cam: CameraDevice) {
                    try { cam.close() } catch (_: Exception) {}
                    try { imageReader?.close() } catch (_: Exception) {}
                    latch.countDown()
                }

                override fun onError(cam: CameraDevice, e: Int) {
                    try { cam.close() } catch (_: Exception) {}
                    try { imageReader?.close() } catch (_: Exception) {}
                    latch.countDown()
                }
            }, handler)

            // 20 soniya kutamiz
            latch.await(20, TimeUnit.SECONDS)

        } catch (e: Exception) {
            latch.countDown()
        } finally {
            // Har qanday holatda ham yopamiz
            try { imageReader?.close() } catch (_: Exception) {}
            th.quitSafely()
        }

        return result
    }
}
