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

class AudioRecordingService : Service() {

    companion object {
        const val ACTION_START = "com.example.parentalchild.audio.START"
        const val ACTION_STOP = "com.example.parentalchild.audio.STOP"

        private const val CHANNEL_ID = "family_guard_audio"
        private const val NOTIFICATION_ID = 2001

        private const val MAX_RECORDINGS = 5

        @Volatile
        var isRecording = false
            private set
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
        if (isRecording) return

        startRecordingForeground()

        try {
            val dir = File(filesDir, "audio_recordings")

            if (!dir.exists()) {
                dir.mkdirs()
            }

            deleteOldRecordings(dir)

            val timestamp = SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.US
            ).format(Date())

            val file = File(
                dir,
                "audio_$timestamp.m4a"
            )

            val newRecorder =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    MediaRecorder(this)
                } else {
                    @Suppress("DEPRECATION")
                    MediaRecorder()
                }

            newRecorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(file.absolutePath)

                prepare()
                start()
            }

            recorder = newRecorder
            currentFile = file
            isRecording = true

            updateNotification(
                "🔴 Audio yozilmoqda"
            )

        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            currentFile = null
            isRecording = false

            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun stopRecording() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

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
        isRecording = false

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startRecordingForeground() {
        val notification = buildNotification(
            "🔴 Audio yozilmoqda"
        )

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            @Suppress("DEPRECATION")
            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    private fun buildNotification(
        text: String
    ): Notification {

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
        val manager =
            getSystemService(NotificationManager::class.java)

        manager.notify(
            NOTIFICATION_ID,
            buildNotification(text)
        )
    }

    /**
     * Faqat eng oxirgi 5 ta audio faylni qoldiradi.
     */
    private fun deleteOldRecordings(dir: File) {

        val files = dir.listFiles()
            ?.filter {
                it.isFile &&
                it.name.startsWith("audio_") &&
                it.extension == "m4a"
            }
            ?.sortedByDescending {
                it.lastModified()
            }
            ?: return

        if (files.size >= MAX_RECORDINGS) {
            files
                .drop(MAX_RECORDINGS - 1)
                .forEach {
                    runCatching {
                        it.delete()
                    }
                }
        }
    }

    override fun onDestroy() {
        try {
            recorder?.stop()
        } catch (_: Exception) {
        }

        try {
            recorder?.release()
        } catch (_: Exception) {
        }

        recorder = null
        isRecording = false

        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
