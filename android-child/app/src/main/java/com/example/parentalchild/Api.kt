package com.example.parentalchild

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

class Api(
    private val baseUrl: String,
    private val secret: String
) {

    private val http =
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                60,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                120,
                TimeUnit.SECONDS
            )
            .build()

    private fun req(
        url: String,
        method: String = "GET",
        body: String? = null
    ): Request {

        val requestBody =
            if (method == "GET") {
                null
            } else {
                (body ?: "")
                    .toRequestBody(
                        "application/json".toMediaType()
                    )
            }

        return Request.Builder()
            .url(baseUrl + url)
            .header(
                "x-device-secret",
                secret
            )
            .method(
                method,
                requestBody
            )
            .build()
    }

    fun register(
        deviceId: String,
        name: String
    ): String {

        http.newCall(
            req(
                "/api/device/register",
                "POST",
                JSONObject()
                    .put(
                        "deviceId",
                        deviceId
                    )
                    .put(
                        "name",
                        name
                    )
                    .toString()
            )
        ).execute().use { res ->

            val text =
                res.body
                    ?.string()
                    .orEmpty()

            if (!res.isSuccessful) {
                throw Exception(
                    "Server(${res.code}): $text"
                )
            }

            return JSONObject(text)
                .optString(
                    "pairingCode",
                    ""
                )
        }
    }

    fun heartbeat(
        deviceId: String,
        battery: Int
    ) {

        http.newCall(
            req(
                "/api/device/heartbeat",
                "POST",
                JSONObject()
                    .put(
                        "deviceId",
                        deviceId
                    )
                    .put(
                        "battery",
                        battery
                    )
                    .toString()
            )
        ).execute().close()
    }

    fun pending(
        deviceId: String
    ): JSONObject? {

        http.newCall(
            req(
                "/api/request/pending?deviceId=$deviceId"
            )
        ).execute().use { res ->

            val text =
                res.body
                    ?.string()
                    .orEmpty()

            if (!res.isSuccessful) {
                return null
            }

            val json =
                JSONObject(text)

            return if (
                json.isNull("request") ||
                !json.has("request")
            ) {
                null
            } else {
                json.getJSONObject("request")
            }
        }
    }

    fun updateStatus(
        id: String,
        status: String,
        resultUrl: String? = null
    ) {

        http.newCall(
            req(
                "/api/request/status",
                "POST",
                JSONObject()
                    .put(
                        "id",
                        id
                    )
                    .put(
                        "status",
                        status
                    )
                    .put(
                        "resultUrl",
                        resultUrl ?: JSONObject.NULL
                    )
                    .toString()
            )
        ).execute().close()
    }

    fun uploadImage(
        base64: String,
        mimeType: String = "image/jpeg"
    ): String {

        http.newCall(
            req(
                "/api/upload",
                "POST",
                JSONObject()
                    .put(
                        "data",
                        base64
                    )
                    .put(
                        "mimeType",
                        mimeType
                    )
                    .toString()
            )
        ).execute().use { res ->

            val text =
                res.body
                    ?.string()
                    .orEmpty()

            if (!res.isSuccessful) {
                throw Exception(
                    "Upload failed: ${res.code} $text"
                )
            }

            return JSONObject(text)
                .optString(
                    "url",
                    ""
                )
        }
    }

    fun uploadFile(
        file: File,
        mimeType: String
    ): String {

        val base64 =
            Base64
                .getEncoder()
                .encodeToString(
                    file.readBytes()
                )

        return uploadImage(
            base64,
            mimeType
        )
    }

    fun uploadJson(
        jsonStr: String
    ): String {

        val base64 =
            Base64
                .getEncoder()
                .encodeToString(
                    jsonStr.toByteArray()
                )

        return uploadImage(
            base64,
            "application/json"
        )
    }

    private fun audioMimeType(
        file: File
    ): String {

        return when {

            file.name.endsWith(
                ".m4a",
                ignoreCase = true
            ) -> "audio/mp4"

            file.name.endsWith(
                ".mp3",
                ignoreCase = true
            ) -> "audio/mpeg"

            file.name.endsWith(
                ".wav",
                ignoreCase = true
            ) -> "audio/wav"

            file.name.endsWith(
                ".aac",
                ignoreCase = true
            ) -> "audio/aac"

            file.name.endsWith(
                ".ogg",
                ignoreCase = true
            ) -> "audio/ogg"

            else -> "audio/*"
        }
    }

    fun uploadCallRecordings(
        files: List<File>
    ): List<Pair<File, String>> {

        val result =
            mutableListOf<Pair<File, String>>()

        files
            .take(5)
            .forEach { file ->

                try {

                    val url =
                        uploadFile(
                            file,
                            audioMimeType(file)
                        )

                    if (url.isNotBlank()) {

                        result.add(
                            file to url
                        )
                    }

                } catch (_: Exception) {
                    // Bitta recording xato bo'lsa,
                    // qolganlari davom etadi.
                }
            }

        return result
    }
}
