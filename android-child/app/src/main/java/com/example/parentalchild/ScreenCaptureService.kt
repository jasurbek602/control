package com.example.parentalchild

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.ByteArrayOutputStream
import java.util.Base64

class ScreenCaptureService : Service() {

    companion object {
        var instance: ScreenCaptureService? = null
        fun captureScreen(): String? = instance?.capture()
    }

    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var w = 1080; private var h = 1920; private var dpi = 320

    override fun onCreate() {
        super.onCreate()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(NotificationChannel("cap", "Screen Capture", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, NotificationCompat.Builder(this, "cap")
            .setContentTitle("Family Guard").setContentText("Monitoring faol")
            .setSmallIcon(android.R.drawable.ic_menu_view).build())

        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: return START_NOT_STICKY
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra("code", Intent::class.java)
                            else @Suppress("DEPRECATION") intent.getParcelableExtra("code")
        if (data == null) return START_NOT_STICKY

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val b = getSystemService(WindowManager::class.java).currentWindowMetrics.bounds
            w = b.width(); h = b.height()
        } else {
            val dm = resources.displayMetrics; w = dm.widthPixels; h = dm.heightPixels
        }
        dpi = resources.displayMetrics.densityDpi

        projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        display = projection?.createVirtualDisplay("FG", w, h, dpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, null)

        return START_STICKY
    }

    fun capture(): String? = try {
        Thread.sleep(300)
        val img = reader?.acquireLatestImage() ?: return null
        val pl = img.planes[0]
        val pad = pl.rowStride - pl.pixelStride * w
        val bmp = Bitmap.createBitmap(w + pad / pl.pixelStride, h, Bitmap.Config.ARGB_8888)
        bmp.copyPixelsFromBuffer(pl.buffer)
        img.close()
        val crop = Bitmap.createBitmap(bmp, 0, 0, w, h); bmp.recycle()
        val out = ByteArrayOutputStream()
        crop.compress(Bitmap.CompressFormat.JPEG, 70, out); crop.recycle()
        Base64.getEncoder().encodeToString(out.toByteArray())
    } catch (_: Exception) { null }

    override fun onDestroy() { instance = null; display?.release(); reader?.close(); projection?.stop(); super.onDestroy() }
    override fun onBind(i: Intent?): IBinder? = null
}
