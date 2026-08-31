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
        val hasFine = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return null

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // Yangi koordinata ol (GPS + Network)
        val fresh = requestLocation(lm, hasFine)
        if (fresh != null) return fresh

        // Yangi olinmasa — so'nggi ma'lumotni qaytaramiz
        return getBestLast(lm)
    }

    private fun requestLocation(lm: LocationManager, hasFine: Boolean): Pair<Double, Double>? {
        val latch  = CountDownLatch(1)
        var best: Location? = null
        val th     = HandlerThread("loc").also { it.start() }

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                synchronized(this) {
                    // Eng aniq koordinatani saqlaymiz
                    if (best == null || loc.accuracy < best!!.accuracy) {
                        best = loc
                        // GPS dan 30m aniqlikdagi signal kelsa — darhol qabul qilamiz
                        if (loc.provider == LocationManager.GPS_PROVIDER && loc.accuracy <= 30f) {
                            latch.countDown()
                        }
                        // Network dan 100m aniqlik — GPS yo'q bo'lsa qabul qilamiz
                        if (loc.provider == LocationManager.NETWORK_PROVIDER && loc.accuracy <= 100f) {
                            if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || !hasFine) {
                                latch.countDown()
                            }
                        }
                    }
                }
            }
            override fun onProviderDisabled(p: String) {}
            override fun onProviderEnabled(p: String)  {}
            @Suppress("DEPRECATION")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
        }

        try {
            // GPS (aniq, tashqarida ishlaydi)
            if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 0L, 0f, listener, th.looper
                )
            }
            // Network (tez, ichkarida ham ishlaydi)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, th.looper
                )
            }
            // 30 soniya kutamiz
            latch.await(30, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            th.quitSafely()
        }

        return best?.let { Pair(it.latitude, it.longitude) }
    }

    private fun getBestLast(lm: LocationManager): Pair<Double, Double>? {
        var best: Location? = null
        val maxAgeMs = 15 * 60 * 1000L // 15 daqiqa

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
                if (best == null || loc.accuracy < best!!.accuracy) best = loc
            } catch (_: Exception) {}
        }
        return best?.let { Pair(it.latitude, it.longitude) }
    }
}
