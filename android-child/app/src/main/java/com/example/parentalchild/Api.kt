package com.example.parentalchild
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class Api(private val baseUrl:String, private val secret:String){
 private val http=OkHttpClient()
 private fun req(url:String, method:String="GET", body:String?=null)=Request.Builder().url(baseUrl+url).header("x-device-secret",secret).method(method, body?.toRequestBody("application/json".toMediaType())).build()
 fun register(deviceId:String, name:String):String{
  http.newCall(req("/api/device/register","POST",JSONObject(mapOf("deviceId" to deviceId,"name" to name)).toString())).execute().use{
   val text=it.body?.string().orEmpty()
   return JSONObject(text).optString("pairingCode","")
  }
 }
 fun heartbeat(deviceId:String){ http.newCall(req("/api/device/heartbeat","POST",JSONObject(mapOf("deviceId" to deviceId)).toString())).execute().close() }
 fun pending(deviceId:String):String{ http.newCall(req("/api/request/pending?deviceId=$deviceId")).execute().use{return it.body?.string().orEmpty()} }
 fun status(id:String,status:String,resultUrl:String?=null){ http.newCall(req("/api/request/status","POST",JSONObject(mapOf("id" to id,"status" to status,"resultUrl" to resultUrl)).toString())).execute().close() }
}
