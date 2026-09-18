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
 * Setup screen hardened for Samsung/modern Android.
 * Runtime permissions are never requested automatically from onCreate().
 */
class MainActivity : AppCompatActivity() {
    private val deviceId by lazy {
        getPreferences(0).getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                getPreferences(0).edit().putString("deviceId", it).apply()
            }
    }

    private lateinit var tvPairing: TextView
    private lateinit var tvStatus: TextView

    private val screenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
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
            setStatus("❌ Screen capture bekor qilindi")
        }
    }

    private val notifLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        setStatus(if (ok) "✅ Bildirishnoma ruxsati berildi" else "⚠️ Bildirishnoma rad etildi")
    }

    private val locationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val fine = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarse = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        setStatus(if (fine || coarse) "✅ Lokatsiya ruxsati berildi" else "❌ Lokatsiya rad etildi")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        tvPairing = TextView(this).apply {
            textSize = 16f
            text = "Ulanmoqda..."
        }
        tvStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 8, 0, 24)
        }

        root.addView(tvPairing)
        root.addView(tvStatus)
        root.addView(makeBtn("📱 Screen capture ruxsati") { requestScreen() })
        root.addView(makeBtn("📍 Lokatsiya ruxsati") { requestLocation() })
        root.addView(makeBtn("🔔 Bildirishnoma ruxsati") { requestNotification() })
        root.addView(makeBtn("📊 Ilovalar statistikasi ruxsati") { requestUsageStats() })
        root.addView(makeBtn("♿ Accessibility screenshot") { requestAccessibility() })
        setContentView(root)

        // Activity is visible here, so Android's background-FGS restriction is not hit.
        startChildService(
            Intent(this, ScreenCaptureService::class.java)
                .putExtra("deviceId", deviceId)
        )

        tvPairing.text = "Device ID: $deviceId\nUlanmoqda..."
        thread {
            try {
                val api = Api(BuildConfig.API_URL, BuildConfig.DEVICE_SECRET)
                val code = api.register(deviceId, "Child device")
                runOnUiThread { tvPairing.text = "✅ Ulandi!\nPairing kod: $code" }
            } catch (e: Exception) {
                runOnUiThread {
                    tvPairing.text = "⚠️ Serverga ulanilmadi: ${e.message ?: "network error"}"
                }
            }
        }
    }

    private fun startChildService(intent: Intent) {
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: Exception) {
            setStatus("❌ Xizmat ishga tushmadi: ${e.message ?: "unknown error"}")
        }
    }

    private fun makeBtn(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        setOnClickListener { onClick() }
    }

    private fun setStatus(message: String) {
        if (::tvStatus.isInitialized) tvStatus.text = message
    }

    private fun requestScreen() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        screenLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun requestNotification() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setStatus("✅ Bu Android versiyasida alohida ruxsat kerak emas")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED) {
            setStatus("✅ Bildirishnoma ruxsati bor")
        } else {
            // Only called from the notification button.
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestLocation() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            setStatus("✅ Lokatsiya ruxsati bor")
            return
        }

        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun requestUsageStats() {
        if (AppHelper(this).hasUsagePermission()) {
            setStatus("✅ Ilovalar statistikasi ruxsati bor")
        } else {
            setStatus("⚠️ Sozlamalar ochiladi — Family Guard uchun Usage access ni yoqing")
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
    }

    private fun requestAccessibility() {
        if (FamilyGuardAccessibilityService.isEnabled()) {
            setStatus("✅ Accessibility screenshot yoqilgan")
        } else {
            setStatus("⚠️ Sozlamalarda Family Guard — Screenshot xizmatini yoqing")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }
}
