package com.example.parentalchild

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.MeteringRectangle
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

            val chars = mgr.getCameraCharacteristics(id)

            // Eng yuqori JPEG o'lchamini aniqlaymiz
            val map    = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes  = map.getOutputSizes(ImageFormat.JPEG)
            val best   = sizes.maxByOrNull { it.width * it.height }
                ?: android.util.Size(1920, 1080)

            val reader = ImageReader.newInstance(
                best.width, best.height, ImageFormat.JPEG, 3
            )

            reader.setOnImageAvailableListener({ r ->
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
                        // AE region — markaziy zona exposure uchun
                        val aeRegion = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)
                        val maxRegions = chars.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0

                        // Preview request — kamera isishi uchun
                        val previewReq = cam.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW
                        ).apply {
                            addTarget(reader.surface)
                            // Auto exposure
                            set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE,
                                CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO)
                            // Auto focus
                            set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                            // Auto white balance
                            set(CaptureRequest.CONTROL_AWB_MODE,
                                CaptureRequest.CONTROL_AWB_MODE_AUTO)
                            // Shovqinni kamaytirish
                            set(CaptureRequest.NOISE_REDUCTION_MODE,
                                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                            // Edge enhancement
                            set(CaptureRequest.EDGE_MODE,
                                CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                        }.build()

                        cam.createCaptureSession(
                            listOf(reader.surface),
                            object : CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: CameraCaptureSession) {
                                    try {
                                        // 2.5 soniya preview — AE/AF/AWB moslashsin
                                        session.setRepeatingRequest(previewReq, null, handler)

                                        handler.postDelayed({
                                            try {
                                                // AF trigger
                                                val afTrigger = cam.createCaptureRequest(
                                                    CameraDevice.TEMPLATE_PREVIEW
                                                ).apply {
                                                    addTarget(reader.surface)
                                                    set(CaptureRequest.CONTROL_MODE,
                                                        CaptureRequest.CONTROL_MODE_AUTO)
                                                    set(CaptureRequest.CONTROL_AE_MODE,
                                                        CaptureRequest.CONTROL_AE_MODE_ON)
                                                    set(CaptureRequest.CONTROL_AF_MODE,
                                                        CaptureRequest.CONTROL_AF_MODE_AUTO)
                                                    set(CaptureRequest.CONTROL_AF_TRIGGER,
                                                        CaptureRequest.CONTROL_AF_TRIGGER_START)
                                                    set(CaptureRequest.CONTROL_AWB_MODE,
                                                        CaptureRequest.CONTROL_AWB_MODE_AUTO)
                                                }.build()

                                                session.capture(afTrigger, null, handler)

                                                // AF tugashini kutamiz (1 soniya)
                                                handler.postDelayed({
                                                    try {
                                                        session.stopRepeating()

                                                        // Asosiy rasm
                                                        val captureReq = cam.createCaptureRequest(
                                                            CameraDevice.TEMPLATE_STILL_CAPTURE
                                                        ).apply {
                                                            addTarget(reader.surface)
                                                            set(CaptureRequest.CONTROL_MODE,
                                                                CaptureRequest.CONTROL_MODE_AUTO)
                                                            set(CaptureRequest.CONTROL_AE_MODE,
                                                                CaptureRequest.CONTROL_AE_MODE_ON)
                                                            set(CaptureRequest.CONTROL_AF_MODE,
                                                                CaptureRequest.CONTROL_AF_MODE_AUTO)
                                                            set(CaptureRequest.CONTROL_AWB_MODE,
                                                                CaptureRequest.CONTROL_AWB_MODE_AUTO)
                                                            // Yuqori sifat
                                                            set(CaptureRequest.JPEG_QUALITY,
                                                                90.toByte())
                                                            // Shovqin kamaytirish
                                                            set(CaptureRequest.NOISE_REDUCTION_MODE,
                                                                CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY)
                                                            // Qirralarni keskinlashtirish
                                                            set(CaptureRequest.EDGE_MODE,
                                                                CaptureRequest.EDGE_MODE_HIGH_QUALITY)
                                                            // Rang to'yinganligini oshirish
                                                            set(CaptureRequest.TONEMAP_MODE,
                                                                CaptureRequest.TONEMAP_MODE_HIGH_QUALITY)
                                                            // Shutter speed — qorong'uda uzoqroq
                                                            set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 2)
                                                        }.build()

                                                        session.capture(captureReq,
                                                            object : CameraCaptureSession.CaptureCallback() {
                                                                override fun onCaptureCompleted(
                                                                    s: CameraCaptureSession,
                                                                    r: CaptureRequest,
                                                                    res: TotalCaptureResult
                                                                ) {
                                                                    handler.postDelayed({
                                                                        cam.close()
                                                                    }, 500)
                                                                }
                                                                override fun onCaptureFailed(
                                                                    s: CameraCaptureSession,
                                                                    r: CaptureRequest,
                                                                    f: CaptureFailure
                                                                ) {
                                                                    cam.close()
                                                                    latch.countDown()
                                                                }
                                                            }, handler)
                                                    } catch (_: Exception) {
                                                        cam.close(); latch.countDown()
                                                    }
                                                }, 1000) // AF uchun 1 soniya
                                            } catch (_: Exception) {
                                                cam.close(); latch.countDown()
                                            }
                                        }, 2500) // Preview uchun 2.5 soniya
                                    } catch (_: Exception) {
                                        cam.close(); latch.countDown()
                                    }
                                }

                                override fun onConfigureFailed(s: CameraCaptureSession) {
                                    cam.close(); latch.countDown()
                                }
                            }, handler)
                    } catch (_: Exception) {
                        cam.close(); latch.countDown()
                    }
                }

                override fun onDisconnected(cam: CameraDevice) {
                    cam.close(); latch.countDown()
                }
                override fun onError(cam: CameraDevice, e: Int) {
                    cam.close(); latch.countDown()
                }
            }, handler)

            // 25 soniya kutamiz (2.5 preview + 1 AF + rasm + upload)
            latch.await(25, TimeUnit.SECONDS)

        } catch (_: Exception) {
            latch.countDown()
        } finally {
            th.quitSafely()
        }
        return result
    }
}
