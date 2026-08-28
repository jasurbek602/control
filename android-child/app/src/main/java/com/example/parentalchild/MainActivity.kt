package com.example.parentalchild

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.media.projection.MediaProjectionManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val deviceId by lazy {
        getPreferences(0).getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                getPreferences(0).edit().putString("deviceId", it).apply()
            }
    }
    private val api by lazy { Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET) }

    private lateinit var tvPairing: TextView
    private lateinit var tvStatus: TextView
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComp: android.content.ComponentName

    @Volatile private var running = true
    @Volatile private var screenGranted = false

    private val screenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            screenGranted = true
            startForegroundService(
                Intent(this, ScreenCaptureService::class.java)
                    .putExtra("resultCode", r.resultCode)
                    .putExtra("code", r.data)
                    .putExtra("deviceId", deviceId)
            )
            setStatus("✅ Screen capture ruxsati berildi")
        } else {
            setStatus("❌ Screen capture bekor qilindi")
        }
    }

    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Kamera ruxsati berildi" else "❌ Kamera rad etildi") }

    private val locLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val ok = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                 results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            setStatus(if (ok) "✅ Lokatsiya ruxsati berildi" else "❌ Lokatsiya rad etildi")
        }
    }

    private val bgLocLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Background lokatsiya berildi" else "⚠️ Faqat ilova ochiq paytda") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComp = android.content.ComponentName(this, DeviceAdminReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }
        tvPairing = TextView(this).apply { textSize = 16f; text = "Ulanmoqda..." }
        tvStatus  = TextView(this).apply { textSize = 13f; setPadding(0, 8, 0, 24) }

        root.addView(tvPairing)
        root.addView(tvStatus)
        root.addView(Button(this).apply {
            text = "📱 Screen capture ruxsati"
            setOnClickListener { requestScreen() }
        })
        root.addView(Button(this).apply {
            text = "📷 Kamera ruxsati"
            setOnClickListener { requestCamera() }
        })
        root.addView(Button(this).apply {
            text = "📍 Lokatsiya ruxsati"
            setOnClickListener { requestLocation() }
        })
        root.addView(Button(this).apply {
            text = if (dpm.isAdminActive(adminComp)) "✅ Device Admin yoqilgan" else "🔐 Device Admin yoqish"
            setOnClickListener { requestAdmin() }
        })
        setContentView(root)

        startHeartbeatLoop()
        startPollLoop()
    }

    private fun getBattery(): Int {
        val bm = getSystemService(BatteryManager::class.java)
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun startHeartbeatLoop() {
        thread(name = "heartbeat") {
            try {
                val code = api.register(deviceId, "Child device")
                runOnUiThread {
                    tvPairing.text = if (code.isNotEmpty())
                        "✅ Ulandi\nPairing code: $code"
                    else "✅ Ulandi"
                }
            } catch (e: Exception) {
                runOnUiThread { tvPairing.text = "⚠️ ${e.message}" }
            }
            while (running) {
                try {
                    api.heartbeat(deviceId, getBattery())
                    runOnUiThread { setStatus("🔋 ${getBattery()}% · Monitoring faol") }
                } catch (_: Exception) {}
                Thread.sleep(10_000)
            }
        }
    }

    private fun startPollLoop() {
        thread(name = "poll") {
            Thread.sleep(4_000)
            while (running) {
                try {
                    val req = api.pending(deviceId)
                    if (req != null) handleRequest(req)
                } catch (_: Exception) {}
                Thread.sleep(5_000)
            }
        }
    }

    private fun handleRequest(req: JSONObject) {
        val id   = req.getString("_id")
        val type = req.getString("type")
        runOnUiThread { setStatus("⏳ $type bajarilmoqda...") }
        thread {
            try {
                when (type) {
                    "SCREENSHOT", "SCREEN_SHARE" -> {
                        if (!screenGranted || ScreenCaptureService.instance == null) {
                            api.updateStatus(id, "FAILED")
                            runOnUiThread { setStatus("⚠️ Screen ruxsati yo'q — tugmani bosing") }
                            return@thread
                        }
                        val b64 = ScreenCaptureService.captureScreen()
                        if (b64 != null) {
                            val url = api.uploadImage(b64)
                            api.updateStatus(id, "DONE", url)
                            runOnUiThread { setStatus("✅ $type yuborildi") }
                        } else {
                            api.updateStatus(id, "FAILED")
                        }
                    }
                    "CAMERA_FRONT" -> shootCamera(id, CameraCharacteristics.LENS_FACING_FRONT, type)
                    "CAMERA_BACK"  -> shootCamera(id, CameraCharacteristics.LENS_FACING_BACK, type)
                    "LOCATION"     -> sendLocation(id)
                    else -> api.updateStatus(id, "FAILED")
                }
            } catch (e: Exception) {
                try { api.updateStatus(id, "FAILED") } catch (_: Exception) {}
                runOnUiThread { setStatus("❌ ${e.message}") }
            }
        }
    }

    private fun shootCamera(id: String, facing: Int, type: String) {
        if (!hasCam()) { api.updateStatus(id, "FAILED"); return }
        val b64 = CameraHelper(this).capturePhoto(facing)
        if (b64 != null) {
            val url = api.uploadImage(b64)
            api.updateStatus(id, "DONE", url)
            runOnUiThread { setStatus("✅ $type yuborildi") }
        } else {
            api.updateStatus(id, "FAILED")
        }
    }

    private fun sendLocation(id: String) {
        val loc = LocationHelper(this).getLastLocation()
        if (loc != null) {
            api.updateStatus(id, "DONE", "https://maps.google.com/?q=${loc.latitude},${loc.longitude}")
            runOnUiThread { setStatus("✅ Lokatsiya yuborildi") }
        } else {
            api.updateStatus(id, "FAILED")
        }
    }

    private fun setStatus(msg: String) { if (::tvStatus.isInitialized) tvStatus.text = msg }
    private fun hasCam() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun requestScreen() { screenLauncher.launch((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()) }
    private fun requestCamera() { if (hasCam()) setStatus("✅ Kamera ruxsati bor") else camLauncher.launch(Manifest.permission.CAMERA) }
    private fun requestLocation() {
        locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    private fun requestAdmin() {
        if (dpm.isAdminActive(adminComp)) { setStatus("✅ Admin yoqilgan"); return }
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Family Guard nazorat uchun.")
        })
    }
    override fun onDestroy() { running = false; super.onDestroy() }
}
