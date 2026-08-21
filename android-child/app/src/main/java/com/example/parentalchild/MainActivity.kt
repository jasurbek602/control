package com.example.parentalchild

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity: AppCompatActivity(){
 private val prefs by lazy { getSharedPreferences("default", 0) }
 private val deviceId by lazy { prefs.getString("deviceId",null) ?: UUID.randomUUID().toString().also{prefs.edit().putString("deviceId",it).apply()} }
 private val api by lazy { Api(BuildConfig.API_URL,BuildConfig.DEVICE_SECRET) }
 private lateinit var status:TextView
 private lateinit var dpm: DevicePolicyManager
 private lateinit var adminComponent: android.content.ComponentName
 private val screenLauncher=registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ r-> if(r.resultCode==Activity.RESULT_OK && r.data!=null){ startService(Intent(this,ScreenCaptureService::class.java).putExtra("code",r.data).putExtra("resultCode",r.resultCode)); status.text="Screen sharing/capture ruxsat berildi" } else status.text="Screen capture bekor qilindi" }
 private val camPermission=registerForActivityResult(ActivityResultContracts.RequestPermission()){ granted-> status.text=if(granted) "Kamera ruxsati berildi" else "Kamera ruxsati rad etildi" }
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
  adminComponent = android.content.ComponentName(this, DeviceAdminReceiver::class.java)
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(32,48,32,32)}
  status=TextView(this).apply{text="Device ID: $deviceId\nMonitoring ochiq rejimda ishlaydi"}
  val screen=Button(this).apply{text="Screen Share / Screenshot ruxsati";setOnClickListener{requestScreen()}}
  val camera=Button(this).apply{text="Kamera ruxsati";setOnClickListener{askCamera()}}
  val admin=Button(this).apply{
   text=if(dpm.isAdminActive(adminComponent)) "Device Administrator yoqilgan" else "Device Administratorni yoqish"
   setOnClickListener{requestDeviceAdmin()}
  }
  root.addView(status);root.addView(screen);root.addView(camera);root.addView(admin);setContentView(root)
 thread {
  try {
    val code = api.register(deviceId, "Child device")
    runOnUiThread { status.text = "Device ID: $deviceId\nPairing code: $code\nMonitoring ochiq rejimda ishlaydi" }
    val svcIntent = Intent(this, MonitorService::class.java).putExtra("deviceId", deviceId)
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
      startForegroundService(svcIntent)
    } else {
      startService(svcIntent)
    }
  } catch (e: Exception) {
    runOnUiThread { status.text = "XATO: ${e.message}" }
  }
}
 }
 override fun onResume(){
  super.onResume()
  if (::status.isInitialized && ::dpm.isInitialized && ::adminComponent.isInitialized) {
   status.text = "Device ID: $deviceId\nMonitoring ochiq rejimda ishlaydi\nDevice Administrator: ${if (dpm.isAdminActive(adminComponent)) "yoqilgan" else "o‘chirilgan"}"
  }
 }

 private fun requestScreen(){ val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; screenLauncher.launch(m.createScreenCaptureIntent()) }
 private fun requestDeviceAdmin(){
  if (dpm.isAdminActive(adminComponent)) {
   status.text = "Device Administrator allaqachon yoqilgan"
   return
  }
  val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
   putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
   putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Family Guard ota-ona nazorati funksiyalarini boshqarish uchun qurilma administratorini yoqishni so‘raydi. Yoqish ixtiyoriy va bu oynani bola o‘zi tasdiqlaydi.")
  }
  startActivity(intent)
 }
 private fun askCamera(){ if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) status.text="Kamera ruxsati berilgan" else camPermission.launch(Manifest.permission.CAMERA) }
}
