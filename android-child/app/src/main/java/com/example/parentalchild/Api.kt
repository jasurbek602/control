package com.example.parentalchild

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Base64
import java.util.concurrent.TimeUnit

class Api(private val baseUrl: String, private val secret: String) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun req(url: String, method: String = "GET", body: String? = null) =
        Request.Builder()
            .url(baseUrl + url)
            .header("x-device-secret", secret)
            .method(method, body?.toRequestBody("application/json".toMediaType())
                ?: if (method == "GET") null else "".toRequestBody())
            .build()

    fun register(deviceId: String, name: String): String {
        http.newCall(req("/api/device/register", "POST",
            JSONObject().put("deviceId", deviceId).put("name", name).toString()
        )).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) throw Exception("Server(${res.code}): $text")
            return JSONObject(text).optString("pairingCode", "")
        }
    }
    fun uploadCallRecordings(files: List<java.io.File>): List<String> {
    return files
        .take(5)
        .mapNotNull { file ->
            try {
                val mime = when {
                    file.name.endsWith(".m4a", true) -> "audio/mp4"
                    file.name.endsWith(".mp3", true) -> "audio/mpeg"
                    file.name.endsWith(".wav", true) -> "audio/wav"
                    else -> "audio/*"
                }

                val url = uploadFile(file, mime)

                if (url.isBlank()) null else url
            } catch (_: Exception) {
                null
            }
        }
}
    fun heartbeat(deviceId: String, battery: Int) {
        http.newCall(req("/api/device/heartbeat", "POST",
            JSONObject().put("deviceId", deviceId).put("battery", battery).toString()
        )).execute().close()
    }

    fun pending(deviceId: String): JSONObject? {
        http.newCall(req("/api/request/pending?deviceId=$deviceId")).execute().use { res ->
            val text = res.body?.string().orEmpty()
            if (!res.isSuccessful) return null
            val json = JSONObject(text)
            return if (json.isNull("request") || !json.has("request")) null
            else json.getJSONObject("request")
        }
    }

    fun updateStatus(id: String, status: String, resultUrl: String? = null) {
        http.newCall(req("/api/request/status", "POST",
            JSONObject().put("id", id).put("status", status)
                .put("resultUrl", resultUrl ?: JSONObject.NULL).toString()
        )).execute().close()
    }

    fun uploadImage(base64: String, mimeType: String = "image/jpeg"): String {
        http.newCall(req("/api/upload", "POST",
            JSONObject().put("data", base64).put("mimeType", mimeType).toString()
        )).execute().use { res ->
            return JSONObject(res.body?.string().orEmpty()).optString("url", "")
        }
    }

    fun uploadFile(file: java.io.File, mimeType: String): String {
        val base64 = Base64.getEncoder().encodeToString(file.readBytes())
        return uploadImage(base64, mimeType)
    }

    // JSON ma'lumotlarni (ilovalar ro'yxati, usage) yuborish uchun
    fun uploadJson(jsonStr: String): String {
        val base64 = Base64.getEncoder().encodeToString(jsonStr.toByteArray())
        return uploadImage(base64, "application/json")
    }
}
