```kotlin
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
 * Barcha runtime/special permissionlar avtomatik onCreate()da so'ralmaydi.
 * Foydalanuvchi "Barcha ruxsatlarni sozlash" tugmasini bosganda
 * permission wizard ketma-ket ishlaydi.
 */
class MainActivity : AppCompatActivity() {

    private val deviceId by lazy {
        getPreferences(0)
            .getString("deviceId", null)
            ?: UUID.randomUUID().toString().also {
                getPreferences(0)
                    .edit()
                    .putString("deviceId", it)
                    .apply()
            }
    }

    private lateinit var tvPairing: TextView
    private lateinit var tvStatus: TextView

    /**
     * Permission wizard bosqichi.
     *
     * 0 = Notification
     * 1 = Location
     * 2 = Microphone
     * 3 = Usage Access
     * 4 = Accessibility
     * 5 = Screen Capture
     * 6 = tugagan
     */
    private var permissionStep = -1

    // ---------------------------------------------------------
    // SCREEN CAPTURE
    // ---------------------------------------------------------

    private val screenLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val data = result.data

            if (
                result.resultCode == Activity.RESULT_OK &&
                data != null
            ) {

                startChildService(
                    Intent(
                        this,
                        ScreenCaptureService::class.java
                    )
                        .putExtra(
                            "resultCode",
                            result.resultCode
                        )
                        .putExtra(
                            "code",
                            data
                        )
                        .putExtra(
                            "deviceId",
                            deviceId
                        )
                        .putExtra(
                            "enableProjection",
                            true
                        )
                )

                setStatus(
                    "✅ Screen capture ruxsati berildi"
                )

            } else {

                setStatus(
                    "⚠️ Screen capture bekor qilindi"
                )
            }

            // Screen Capture oxirgi bosqich.
            if (permissionStep == 5) {
                permissionStep = 6
                continuePermissionSetup()
            }
        }

    // ---------------------------------------------------------
    // NOTIFICATION
    // ---------------------------------------------------------

    private val notificationLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                setStatus(
                    "✅ Bildirishnoma ruxsati berildi"
                )
            } else {
                setStatus(
                    "⚠️ Bildirishnoma ruxsati berilmadi"
                )
            }

            continuePermissionSetup()
        }

    // ---------------------------------------------------------
    // LOCATION
    // ---------------------------------------------------------

    private val locationLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->

            val fine =
                result[
                    Manifest.permission.ACCESS_FINE_LOCATION
                ] == true

            val coarse =
                result[
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ] == true

            if (fine || coarse) {
                setStatus(
                    "✅ Lokatsiya ruxsati berildi"
                )
            } else {
                setStatus(
                    "⚠️ Lokatsiya ruxsati berilmadi"
                )
            }

            continuePermissionSetup()
        }

    // ---------------------------------------------------------
    // MICROPHONE
    // ---------------------------------------------------------

    private val microphoneLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->

            if (granted) {
                setStatus(
                    "✅ Mikrofon ruxsati berildi"
                )
            } else {
                setStatus(
                    "⚠️ Mikrofon ruxsati berilmadi"
                )
            }

            continuePermissionSetup()
        }

    // ---------------------------------------------------------
    // ACTIVITY
    // ---------------------------------------------------------

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        createUi()

        /*
         * Bu yerda permission so'ralmaydi.
         *
         * Faqat mavjud child service ishga tushiriladi.
         */
        startChildService(
            Intent(
                this,
                ScreenCaptureService::class.java
            )
                .putExtra(
                    "deviceId",
                    deviceId
                )
        )

        registerDevice()
    }

    // ---------------------------------------------------------
    // UI
    // ---------------------------------------------------------

    private fun createUi() {

        val root =
            LinearLayout(this).apply {

                orientation =
                    LinearLayout.VERTICAL

                setPadding(
                    40,
                    60,
                    40,
                    40
                )
            }

        tvPairing =
            TextView(this).apply {

                textSize = 16f

                text =
                    "Ulanmoqda..."
            }

        tvStatus =
            TextView(this).apply {

                textSize = 13f

                setPadding(
                    0,
                    8,
                    0,
                    24
                )

                text =
                    "Ruxsatlarni sozlash uchun tugmani bosing."
            }

        // -----------------------------------------------------
        // ASOSIY PERMISSION TUGMASI
        // -----------------------------------------------------

        val allPermissionsButton =
            makeBtn(
                "🔐 Barcha ruxsatlarni sozlash"
            ) {
                startPermissionSetup()
            }

        // -----------------------------------------------------
        // AUDIO
        // -----------------------------------------------------

        val audioStartButton =
            makeBtn(
                "🎙 Audio yozishni yoqish"
            ) {
                startAudioRecording()
            }

        val audioStopButton =
            makeBtn(
                "⏹ Audio yozishni to‘xtatish"
            ) {
                stopAudioRecording()
            }

        // -----------------------------------------------------
        // YASHIRISH
        // -----------------------------------------------------
        //
        // Bu alohida qoladi.
        // Mavjud hiding funksiyang bo'lsa shu callback ichiga
        // o'sha funksiyani ulaysan.
        //

        val hideButton =
            makeBtn(
                "🙈 Yashirish"
            ) {
                setStatus(
                    "Ilovani yashirish funksiyasi alohida ishlaydi."
                )

                /*
                 * Mavjud hide logic shu yerda qoladi.
                 *
                 * Masalan:
                 * hideApp()
                 */
            }

        root.addView(tvPairing)
        root.addView(tvStatus)

        root.addView(
            allPermissionsButton
        )

        root.addView(
            audioStartButton
        )

        root.addView(
            audioStopButton
        )

        root.addView(
            hideButton
        )

        setContentView(root)
    }

    // ---------------------------------------------------------
    // DEVICE REGISTER
    // ---------------------------------------------------------

    private fun registerDevice() {

        tvPairing.text =
            "Device ID: $deviceId\nUlanmoqda..."

        thread {

            try {

                val api =
                    Api(
                        BuildConfig.API_URL,
                        BuildConfig.DEVICE_SECRET
                    )

                val code =
                    api.register(
                        deviceId,
                        "Child device"
                    )

                runOnUiThread {

                    tvPairing.text =
                        "✅ Ulandi!\nPairing kod: $code"
                }

            } catch (e: Exception) {

                runOnUiThread {

                    tvPairing.text =
                        "⚠️ Serverga ulanilmadi: ${
                            e.message ?: "network error"
                        }"
                }
            }
        }
    }

    // ---------------------------------------------------------
    // GENERIC SERVICE START
    // ---------------------------------------------------------

    private fun startChildService(
        intent: Intent
    ) {

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

        } catch (e: Exception) {

            setStatus(
                "❌ Xizmat ishga tushmadi: ${
                    e.message ?: "unknown error"
                }"
            )
        }
    }

    // ---------------------------------------------------------
    // BUTTON FACTORY
    // ---------------------------------------------------------

    private fun makeBtn(
        text: String,
        onClick: () -> Unit
    ): Button {

        return Button(this).apply {

            this.text = text

            setOnClickListener {
                onClick()
            }
        }
    }

    // ---------------------------------------------------------
    // STATUS
    // ---------------------------------------------------------

    private fun setStatus(
        message: String
    ) {

        if (::tvStatus.isInitialized) {
            tvStatus.text = message
        }
    }

    // =========================================================
    // PERMISSION WIZARD
    // =========================================================

    private fun startPermissionSetup() {

        permissionStep = 0

        setStatus(
            "🔐 Ruxsatlarni sozlash boshlandi..."
        )

        continuePermissionSetup()
    }

    private fun continuePermissionSetup() {

        when (permissionStep) {

            // =================================================
            // 0 — NOTIFICATION
            // =================================================

            0 -> {

                permissionStep = 1

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {

                    val granted =
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) ==
                            PackageManager.PERMISSION_GRANTED

                    if (!granted) {

                        setStatus(
                            "🔔 Bildirishnoma ruxsatini bering..."
                        )

                        notificationLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS
                        )

                        return
                    }
                }

                // Android 12 va pastida alohida
                // notification permission yo'q.
                continuePermissionSetup()
            }

            // =================================================
            // 1 — LOCATION
            // =================================================

            1 -> {

                permissionStep = 2

                val fine =
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) ==
                        PackageManager.PERMISSION_GRANTED

                val coarse =
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) ==
                        PackageManager.PERMISSION_GRANTED

                if (!fine && !coarse) {

                    setStatus(
                        "📍 Lokatsiya ruxsatini bering..."
                    )

                    locationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )

                    return
                }

                continuePermissionSetup()
            }

            // =================================================
            // 2 — MICROPHONE
            // =================================================

            2 -> {

                permissionStep = 3

                val granted =
                    ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.RECORD_AUDIO
                    ) ==
                        PackageManager.PERMISSION_GRANTED

                if (!granted) {

                    setStatus(
                        "🎙 Mikrofon ruxsatini bering..."
                    )

                    microphoneLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )

                    return
                }

                continuePermissionSetup()
            }

            // =================================================
            // 3 — USAGE ACCESS
            // =================================================

            3 -> {

                permissionStep = 4

                if (
                    !AppHelper(this)
                        .hasUsagePermission()
                ) {

                    setStatus(
                        "📊 Usage Access ochiladi. Family Guard'ni yoqing."
                    )

                    startActivity(
                        Intent(
                            Settings.ACTION_USAGE_ACCESS_SETTINGS
                        )
                    )

                    return
                }

                continuePermissionSetup()
            }

            // =================================================
            // 4 — ACCESSIBILITY
            // =================================================

            4 -> {

                permissionStep = 5

                if (
                    !FamilyGuardAccessibilityService
                        .isEnabled()
                ) {

                    setStatus(
                        "♿ Accessibility ochiladi. Family Guard'ni yoqing."
                    )

                    startActivity(
                        Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                    )

                    return
                }

                continuePermissionSetup()
            }

            // =================================================
            // 5 — SCREEN CAPTURE
            // =================================================

            5 -> {

                setStatus(
                    "📸 Screen capture ruxsatini bering..."
                )

                requestScreen()

                return
            }

            // =================================================
            // 6 — DONE
            // =================================================

            else -> {

                setStatus(
                    "✅ Barcha mavjud ruxsatlar sozlandi!"
                )

                getPreferences(0)
                    .edit()
                    .putBoolean(
                        "permission_setup_completed",
                        true
                    )
                    .apply()
            }
        }
    }

    // =========================================================
    // SCREEN CAPTURE REQUEST
    // =========================================================

    private fun requestScreen() {

        val manager =
            getSystemService(
                MEDIA_PROJECTION_SERVICE
            ) as MediaProjectionManager

        screenLauncher.launch(
            manager.createScreenCaptureIntent()
        )
    }

    // =========================================================
    // INDIVIDUAL NOTIFICATION REQUEST
    // =========================================================

    private fun requestNotification() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.TIRAMISU
        ) {

            setStatus(
                "✅ Bu Android versiyasida alohida notification ruxsati kerak emas."
            )

            return
        }

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) {

            setStatus(
                "✅ Bildirishnoma ruxsati bor."
            )

        } else {

            notificationLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
    }

    // =========================================================
    // INDIVIDUAL LOCATION REQUEST
    // =========================================================

    private fun requestLocation() {

        val fine =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        val coarse =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {

            setStatus(
                "✅ Lokatsiya ruxsati bor."
            )

            return
        }

        locationLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    // =========================================================
    // INDIVIDUAL USAGE ACCESS
    // =========================================================

    private fun requestUsageStats() {

        if (
            AppHelper(this)
                .hasUsagePermission()
        ) {

            setStatus(
                "✅ Ilovalar statistikasi ruxsati bor."
            )

        } else {

            setStatus(
                "⚠️ Usage Access ochiladi. Family Guard'ni yoqing."
            )

            startActivity(
                Intent(
                    Settings.ACTION_USAGE_ACCESS_SETTINGS
                )
            )
        }
    }

    // =========================================================
    // INDIVIDUAL ACCESSIBILITY
    // =========================================================

    private fun requestAccessibility() {

        if (
            FamilyGuardAccessibilityService
                .isEnabled()
        ) {

            setStatus(
                "✅ Accessibility yoqilgan."
            )

        } else {

            setStatus(
                "⚠️ Accessibility sozlamalarida Family Guard'ni yoqing."
            )

            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )
        }
    }

    // =========================================================
    // AUDIO RECORDING
    // =========================================================
    //
    // Recording faqat foydalanuvchi tugmani bosganda boshlanadi.
    // Mikrofon ishlayotganida Android foreground notification/
    // microphone indicator ko'rinadi.
    // =========================================================

    private fun startAudioRecording() {

        val granted =
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (!granted) {

            setStatus(
                "⚠️ Avval 'Barcha ruxsatlarni sozlash' orqali mikrofon ruxsatini bering."
            )

            return
        }

        val intent =
            Intent(
                this,
                AudioRecordingService::class.java
            ).apply {

                action =
                    AudioRecordingService.ACTION_START
            }

        try {

            ContextCompat.startForegroundService(
                this,
                intent
            )

            setStatus(
                "🔴 Audio yozish boshlandi. Android mikrofon indikatori ko‘rinadi."
            )

        } catch (e: Exception) {

            setStatus(
                "❌ Audio service ishga tushmadi: ${
                    e.message ?: "unknown error"
                }"
            )
        }
    }

    // =========================================================
    // AUDIO STOP
    // =========================================================

    private fun stopAudioRecording() {

        val intent =
            Intent(
                this,
                AudioRecordingService::class.java
            ).apply {

                action =
                    AudioRecordingService.ACTION_STOP
            }

        try {

            startService(intent)

            setStatus(
                "⏹ Audio yozish to‘xtatildi."
            )

        } catch (e: Exception) {

            setStatus(
                "❌ Audio service to‘xtatilmadi: ${
                    e.message ?: "unknown error"
                }"
            )
        }
    }

    // =========================================================
    // SETTINGS'DAN QAYTISH
    // =========================================================

    override fun onResume() {

        super.onResume()

        /*
         * Usage Access va Accessibility Android Settings orqali
         * beriladi. Foydalanuvchi qaytganda keyingi bosqichni
         * davom ettiramiz.
         */

        when (permissionStep) {

            4 -> {
                if (
                    FamilyGuardAccessibilityService
                        .isEnabled()
                ) {
                    continuePermissionSetup()
                }
            }

            3 -> {
                if (
                    AppHelper(this)
                        .hasUsagePermission()
                ) {
                    continuePermissionSetup()
                }
            }
        }
    }
}
```
