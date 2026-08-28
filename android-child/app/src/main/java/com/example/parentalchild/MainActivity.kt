package com.example.parentalchild

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.UUID

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

    // Screen capture ruxsati
    private val screenLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { r ->
        if (r.resultCode == Activity.RESULT_OK && r.data != null) {
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

    // Kamera ruxsati
    private val camLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok -> setStatus(if (ok) "✅ Kamera ruxsati berildi" else "❌ Kamera rad etildi") }

    // ✅ Lokatsiya ruxsati (aniq — GPS)
    private val locLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val fine   = results[Manifest.permission.ACCESS_FINE_LOCATION]   == true
        val coarse = results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fine || coarse) {
            // Android 10+ da background lokatsiya alohida so'raladi
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                setStatus("✅ Lokatsiya ruxsati berildi")
            }
        } else {
            setStatus("❌ Lokatsiya ruxsati rad etildi")
        }
    }

    // ✅ Background lokatsiya (Android 10+) — "Har doim ruxsat ber"
    private val bgLocLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { ok ->
        setStatus(if (ok) "✅ Lokatsiya (background) ruxsati berildi" else "⚠️ Lokatsiya faqat ilova ochiq paytda ishlaydi")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dpm = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComp = android.content.ComponentName(this, DeviceAdminReceiver::class.java)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 40)
        }

        tvPairing = TextView(this).apply {
            textSize = 16f
            text = "Device ID: $deviceId"
        }
        tvStatus = TextView(this).apply {
            textSize = 13f
            setPadding(0, 8, 0, 24)
            text = "Holat: kutilmoqda..."
        }

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
        // ✅ Yangi tugma
        root.addView(Button(this).apply {
            text = "📍 Lokatsiya ruxsati"
            setOnClickListener { requestLocation() }
        })
        root.addView(Button(this).apply {
            text = if (dpm.isAdminActive(adminComp)) "✅ Device Admin yoqilgan" else "🔐 Device Admin yoqish"
            setOnClickListener { requestAdmin() }
        })

        setContentView(root)

        // Ilova ochilganda Service avtomatik ishga tushadi
        startForegroundService(
            Intent(this, ScreenCaptureService::class.java)
                .putExtra("deviceId", deviceId)
        )
    }

    private fun setStatus(msg: String) {
        if (::tvStatus.isInitialized) tvStatus.text = msg
    }

    private fun requestScreen() {
        screenLauncher.launch(
            (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent()
        )
    }

    private fun requestCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
            setStatus("✅ Kamera ruxsati bor")
        else
            camLauncher.launch(Manifest.permission.CAMERA)
    }

    // ✅ Lokatsiya ruxsati so'rash
    private fun requestLocation() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            // Android 10+ — background ruxsatini tekshiramiz
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val bg = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (!bg) bgLocLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                else setStatus("✅ Lokatsiya ruxsati bor (background)")
            } else {
                setStatus("✅ Lokatsiya ruxsati bor")
            }
        } else {
            locLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun requestAdmin() {
        if (dpm.isAdminActive(adminComp)) {
            setStatus("✅ Admin allaqachon yoqilgan"); return
        }
        startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComp)
            putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Family Guard nazorat uchun.")
        })
    }

    override fun onDestroy() { super.onDestroy() }
}
