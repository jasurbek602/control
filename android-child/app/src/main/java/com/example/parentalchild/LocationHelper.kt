package com.example.parentalchild

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationHelper(private val ctx: Context) {

    fun getLocation(): Pair<Double, Double>? {
        val hasFine   = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // GPS yoqilgan bo'lsa — yangi GPS koordinata olishga harakat qil
        if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val gps = requestGps(lm)
            if (gps != null) return gps
        }

        // GPS ishlamasa — so'nggi ma'lum joylashuvni qaytaramiz
        return getBestLast(lm)
    }

    private fun requestGps(lm: LocationManager): Pair<Double, Double>? {
        val latch   = CountDownLatch(1)
        var result: Pair<Double, Double>? = null
        val th      = HandlerThread("loc-gps").also { it.start() }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                // Aniqlik 50 metrdan yaxshi bo'lsa qabul qilamiz
                if (loc.accuracy <= 50f && result == null) {
                    result = Pair(loc.latitude, loc.longitude)
                    latch.countDown()
                }
            }
            override fun onProviderDisabled(p: String) { latch.countDown() }
            override fun onProviderEnabled(p: String)  {}
            @Suppress("DEPRECATION")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }

        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 0L, 0f, listener, th.looper
            )
            // Network ham qo'shamiz (tezroq javob berishi uchun)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, th.looper
                    )
                } catch (_: Exception) {}
            }
            // 45 soniya GPS ni kutamiz
            latch.await(45, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            th.quitSafely()
        }
        return result
    }

    private fun getBestLast(lm: LocationManager): Pair<Double, Double>? {
        var best: Location? = null
        val maxAgeMs = 10 * 60 * 1000L // 10 daqiqadan eski bo'lsa rad etamiz

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            "fused"
        )
        for (p in providers) {
            try {
                if (!lm.isProviderEnabled(p)) continue
                val loc = lm.getLastKnownLocation(p) ?: continue
                val age = System.currentTimeMillis() - loc.time
                if (age > maxAgeMs) continue
                if (best == null || loc.accuracy < best.accuracy) best = loc
            } catch (_: Exception) {}
        }
        return best?.let { Pair(it.latitude, it.longitude) }
    }
}
