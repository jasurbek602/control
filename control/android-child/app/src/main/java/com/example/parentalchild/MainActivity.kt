package com.example.parentalchild

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
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

/**
 * Family Guard child setup screen.
 *
 * Design goals:
 * - Already granted permissions are skipped.
 * - Optional features failing do not crash the whole activity.
 * - Special permissions (Usage Access / Accessibility) are opened in Settings.
 * - Screen capture always requires the Android system consent dialog.
 * - Audio recording is explicit and uses a visible foreground-service notification.
 */
class MainActivity : AppCompatActivity() {

    private enum class SettingsStep { NONE, USAGE, ACCESSIBILITY }

    private var setupRunning = false
    private var awaitingSettings = SettingsStep.NONE

    private val deviceId: String by lazy {
        getPreferences(0).getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                getPreferences(0).edit().putString("deviceId", it).apply()
            }
    }

    private lateinit var tvPairing: TextView
    private lateinit var tvStatus: TextView
    private lateinit var allPermissionsButton: Button

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        setStatus(if (granted) "✅ Bildirishnoma ruxsati berildi" else "⚠️ Bildirishnoma ruxsati berilmadi")
        continuePermissionSetup()
    }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        setStatus(if (fine || coarse) "✅ Lokatsiya ruxsati berildi" else "⚠️ Lokatsiya ruxsati berilmadi")
        continuePermissionSetup()
    }

    private val microphoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        setStatus(if (granted) "✅ Mikrofon ruxsati berildi" else "⚠️ Mikrofon ruxsati berilmadi")
        continuePermissionSetup()
    }

    private val screenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                startChildService(
                    Intent(this, ScreenCaptureService::class.java)
                        .putExtra("resultCode", result.resultCode)
                        .putExtra("code", data)
                        .putExtra("deviceId", deviceId)
                        .putExtra("enableProjection", true)
                )
                setStatus("✅ Screen capture ruxsati berildi")
            } else {
                setStatus("⚠️ Screen capture bekor qilindi — qolgan funksiyalar ishlaydi")
            }
        } catch (e: Exception) {
            setStatus("⚠️ Screen capture ishga tushmadi — qolgan funksiyalar ishlaydi")
        } finally {
            setupRunning = false
            allPermissionsButton.isEnabled = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        registerDevice()

        // Start the monitoring service only while the Activity is visible.
        // This avoids trying to launch a foreground service from a background state.
        startChildService(
            Intent(this, ScreenCaptureService::class.java)
                .putExtra("deviceId", deviceId)
        )
    }

    override fun onResume() {
        super.onResume()
        when (awaitingSettings) {
            SettingsStep.USAGE -> {
                if (AppHelper(this).hasUsagePermission()) {
                    awaitingSettings = SettingsStep.NONE
                    setStatus("✅ Usage Access yoqildi")
                    continuePermissionSetup()
                }
            }
            SettingsStep.ACCESSIBILITY -> {
                if (FamilyGuardAccessibilityService.isEnabled()) {
                    awaitingSettings = SettingsStep.NONE
                    setStatus("✅ Accessibility yoqildi")
                    continuePermissionSetup()
                }
            }
            SettingsStep.NONE -> Unit
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        tvPairing = TextView(this).apply {
            textSize = 16f
            text = "Device ID: $deviceId\nUlanmoqda..."
        }

        tvStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 8, 0, 24)
        }

        allPermissionsButton = makeBtn("🔐 Barcha ruxsatlarni sozlash") {
            startPermissionSetup()
        }

        root.addView(tvPairing)
        root.addView(tvStatus)
        root.addView(allPermissionsButton)
        root.addView(makeBtn("🎙️ Audio yozishni boshlash") { startAudioRecording() })
        root.addView(makeBtn("⏹️ Audio yozishni to‘xtatish") { stopAudioRecording() })
        root.addView(makeBtn("📱 Screen capture") { requestScreenOnly() })
        root.addView(makeBtn("🙈 Oynani yopish") { finishAndRemoveTask() })

        setContentView(root)
    }

    private fun startPermissionSetup() {
        if (setupRunning) return
        setupRunning = true
        allPermissionsButton.isEnabled = false
        awaitingSettings = SettingsStep.NONE
        setStatus("🔐 Ruxsatlar tekshirilmoqda...")
        continuePermissionSetup()
    }

    /** Runs one step at a time. Granted permissions are skipped automatically. */
    private fun continuePermissionSetup() {
        if (!setupRunning) return

        try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasPermission(Manifest.permission.POST_NOTIFICATIONS) -> {
                    setStatus("🔔 Bildirishnoma ruxsati so‘ralmoqda...")
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }

                !hasLocationPermission() -> {
                    setStatus("📍 Lokatsiya ruxsati so‘ralmoqda...")
                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }

                !hasPermission(Manifest.permission.RECORD_AUDIO) -> {
                    setStatus("🎙️ Mikrofon ruxsati so‘ralmoqda...")
                    microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }

                !AppHelper(this).hasUsagePermission() -> {
                    awaitingSettings = SettingsStep.USAGE
                    setStatus("📊 Sozlamalarda Family Guard uchun Usage Access ni yoqing")
                    safeStartActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }

                !FamilyGuardAccessibilityService.isEnabled() -> {
                    awaitingSettings = SettingsStep.ACCESSIBILITY
                    setStatus("♿ Sozlamalarda Family Guard Accessibility xizmatini yoqing")
                    safeStartActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }

                else -> {
                    setupRunning = false
                    allPermissionsButton.isEnabled = true
                    setStatus("✅ Ruxsatlar sozlandi. Berilganlari avtomatik o‘tkazib yuborildi.")
                }
            }
        } catch (e: Exception) {
            // A single optional step must not terminate the Activity.
            setStatus("⚠️ Bu bosqich ishlamadi, keyinroq qayta urinib ko‘rish mumkin")
            setupRunning = false
            awaitingSettings = SettingsStep.NONE
            allPermissionsButton.isEnabled = true
        }
    }

    private fun requestScreenOnly() {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenLauncher.launch(manager.createScreenCaptureIntent())
        } catch (_: Exception) {
            setStatus("⚠️ Screen capture hozir mavjud emas")
        }
    }

    private fun startAudioRecording() {
        try {
            if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                setStatus("⚠️ Avval mikrofon ruxsatini bering")
                return
            }
            val intent = Intent(this, AudioRecordingService::class.java)
                .setAction(AudioRecordingService.ACTION_START)
            ContextCompat.startForegroundService(this, intent)
            setStatus("🔴 Audio yozish boshlandi — Android ko‘rinadigan indikatorini saqlaydi")
        } catch (_: Exception) {
            setStatus("⚠️ Audio yozish ishga tushmadi")
        }
    }

    private fun stopAudioRecording() {
        try {
            val intent = Intent(this, AudioRecordingService::class.java)
                .setAction(AudioRecordingService.ACTION_STOP)
            startService(intent)
            setStatus("⏹️ Audio yozish to‘xtatildi")
        } catch (_: Exception) {
            setStatus("⚠️ Audio yozishni to‘xtatish muvaffaqiyatsiz")
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun safeStartActivity(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: Exception) {
            awaitingSettings = SettingsStep.NONE
            setupRunning = false
            allPermissionsButton.isEnabled = true
            setStatus("⚠️ Android sozlamalarini ochib bo‘lmadi")
        }
    }

    private fun startChildService(intent: Intent) {
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (_: SecurityException) {
            setStatus("⚠️ Monitoring xizmati uchun Android ruxsati yetarli emas")
        } catch (_: Exception) {
            setStatus("⚠️ Monitoring xizmati ishga tushmadi")
        }
    }

    private fun registerDevice() {
        thread(name = "family-guard-register") {
            try {
                val api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
                val code = api.register(deviceId, "Child device")
                runOnUiThread {
                    tvPairing.text = "✅ Ulandi!\nDevice ID: $deviceId\nPairing kod: $code"
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvPairing.text = "⚠️ Serverga ulanilmadi\nDevice ID: $deviceId"
                }
            }
        }
    }

    private fun makeBtn(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            setOnClickListener {
                try {
                    onClick()
                } catch (_: Exception) {
                    setStatus("⚠️ Amal bajarilmadi, ilova ishlashda davom etadi")
                }
            }
        }

    private fun setStatus(message: String) {
        if (::tvStatus.isInitialized) tvStatus.text = message
    }
}
