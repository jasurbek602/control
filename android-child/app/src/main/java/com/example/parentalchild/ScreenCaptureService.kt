package com.example.parentalchild

import android.app.*
import android.content.*
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ScreenCaptureService: Service(){
 override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
  val mgr=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
  startForeground(100, NotificationCompat.Builder(this,"capture").setContentTitle("Family Guard").setContentText("Screen capture faol").setSmallIcon(android.R.drawable.ic_menu_view).build())
  // Production: create VirtualDisplay from MediaProjection and feed frames to WebRTC/object storage.
  // Intentional: the OS consent intent is passed from MainActivity; no hidden capture is attempted.
  return START_NOT_STICKY
 }
 override fun onBind(intent:Intent?):IBinder?=null
}
