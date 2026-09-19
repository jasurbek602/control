package com.example.parentalchild

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Explicit, user-started audio recording.
 *
 * The recording is saved locally on the child device.
 * Android shows a foreground-service notification while recording.
 */
class AudioRecordingService : Service() {

    companion object {
        const val ACTION_START =
            "com.example.parentalchild.audio.START"

        const val ACTION_STOP =
            "com.example.parentalchild.audio.STOP"

        private const val CHANNEL_ID =
            "family_guard_audio"

        private const val NOTIFICATION_ID =
            2001

        private const val MAX_RECORDINGS =
            5
    }

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null

    override fun onCreate() {
        super.onCreate()

        val manager = getSystemService(NotificationManager::class.java)

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Family Guard audio recording",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> startRecording()
            ACTION_STOP -> stopRecording()
        }

        return START_NOT_STICKY
    }

    private fun startRecording() {
        if (recorder != null) return

        try {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("🔴 Audio yozilmoqda"),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                }
            )

            val dir = File(filesDir, "audio_recordings")
            if (!dir.exists()) {
                dir.mkdirs()
            }

            trimOldRecordings(dir)

            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.US
            ).format(Date())

            val file = File(
                dir,
                "audio_$timestamp.m4a"
            )

            val mediaRecorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            mediaRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            recorder = mediaRecorder
            currentFile = file

            updateNotification("🔴 Audio yozilmoqda: ${file.name}")
        } catch (_: Exception) {
            releaseRecorder()

            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }

            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
            try {
                currentFile?.delete()
            } catch (_: Exception) {
            }
        } finally {
            releaseRecorder()

            try {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } catch (_: Exception) {
            }

            stopSelf()
        }
    }

    private fun releaseRecorder() {
        try {
            recorder?.reset()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null
        currentFile = null
    }

    private fun buildNotification(text: String): Notification {
        return NotificationCompat.Builder(
            this,
            CHANNEL_ID
        )
            .setContentTitle("Family Guard")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                buildNotification(text)
            )
    }

    private fun trimOldRecordings(dir: File) {
        val files = dir.listFiles()
            ?.filter {
                it.isFile &&
                    it.name.startsWith("audio_") &&
                    it.extension == "m4a"
            }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        files
            .drop(MAX_RECORDINGS - 1)
            .forEach {
                runCatching { it.delete() }
            }
    }

    override fun onDestroy() {
        releaseRecorder()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
