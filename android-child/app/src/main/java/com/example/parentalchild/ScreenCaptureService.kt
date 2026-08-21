package com.example.parentalchild

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlin.concurrent.thread

class ScreenCaptureService: Service(){
 companion object {
  @Volatile private var active = false
  fun hasActiveProjection(): Boolean = active
 }
 override fun onCreate(){
  super.onCreate()
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
   val channel = NotificationChannel(
    "capture",
    "Screen Capture",
    NotificationManager.IMPORTANCE_LOW
   )
   val manager = getSystemService(NotificationManager::class.java)
   manager.createNotificationChannel(channel)
  }
 }
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startForeground(100, NotificationCompat.Builder(this,"capture").setContentTitle("Family Guard").setContentText("Screen capture faol").setSmallIcon(android.R.drawable.ic_menu_view).build())
  // MainActivity dan kelgan holatda (foydalanuvchi tizim dialogini tasdiqlagach) active=true bo'ladi.
  val requestId = intent?.getStringExtra("requestId")
  if (intent?.hasExtra("code") == true) {
   active = true
  }
  // Production: create VirtualDisplay from MediaProjection and feed frames to WebRTC/object storage.
  // Intentional: the OS consent intent is passed from MainActivity; no hidden capture is attempted.
  if (requestId != null) {
   thread {
    try {
     Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET).status(requestId, "DONE", null)
    } catch (_: Exception) {}
   }
  }
  return START_NOT_STICKY
 }
 override fun onDestroy(){
  active = false
  super.onDestroy()
 }
 override fun onBind(intent:Intent?):IBinder?=null
}
