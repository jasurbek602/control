package com.example.parentalchild

import android.content.Context
import android.net.Uri
import java.io.File

class CallRecordingManager(private val context: Context) {

    companion object {
        private const val DIR_NAME = "call_recordings"
        private const val MAX_RECORDINGS = 5
    }

    private val dir: File
        get() = File(context.filesDir, DIR_NAME).apply {
            mkdirs()
        }

    /**
     * Foydalanuvchi Samsung Phone'dan tanlagan/share qilgan
     * call recording faylini Family Guard lokal xotirasiga saqlaydi.
     *
     * Har doim faqat eng oxirgi 5 ta yozuv saqlanadi.
     */
    fun importRecording(uri: Uri): File? {
        return try {
            val originalName = getFileName(uri)
                ?: "call_${System.currentTimeMillis()}.m4a"

            val safeName = sanitizeFileName(originalName)
            val output = File(
                dir,
                "${System.currentTimeMillis()}_$safeName"
            )

            context.contentResolver.openInputStream(uri)?.use { input ->
                output.outputStream().use { out ->
                    input.copyTo(out)
                }
            } ?: return null

            trimToLastFive()

            output
        } catch (_: Exception) {
            null
        }
    }

    fun getLastFive(): List<File> {
        return dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?.take(MAX_RECORDINGS)
            ?: emptyList()
    }

    private fun trimToLastFive() {
        val files = dir.listFiles()
            ?.filter { it.isFile }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        files.drop(MAX_RECORDINGS).forEach {
            runCatching { it.delete() }
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            context.contentResolver
                .query(
                    uri,
                    arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(
                            android.provider.OpenableColumns.DISPLAY_NAME
                        )
                        if (index >= 0) cursor.getString(index) else null
                    } else {
                        null
                    }
                }
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeFileName(name: String): String {
        return name
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(120)
    }
}
