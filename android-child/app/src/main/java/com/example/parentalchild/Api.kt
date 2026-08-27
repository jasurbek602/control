package com.example.parentalchild
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
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

    fun heartbeat(deviceId: String) {
        http.newCall(req("/api/device/heartbeat", "POST",
            JSONObject().put("deviceId", deviceId).toString()
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
}
