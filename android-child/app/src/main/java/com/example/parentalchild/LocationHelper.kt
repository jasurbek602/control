package com.example.parentalchild

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.HandlerThread
import android.os.Handler
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LocationHelper(private val ctx: Context) {

    fun getLocation(): Pair<Double, Double>? {
        // Ruxsat tekshirish
        val hasFine   = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)   == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        // 1) Avval oxirgi ma'lum lokatsiyani sinab ko'ramiz (tez, batery tejaydi)
        val last = bestLastKnown(lm)
        if (last != null) return last

        // 2) Yangi lokatsiya so'raymiz (15 soniya timeout)
        return requestFresh(lm)
    }

    private fun bestLastKnown(lm: LocationManager): Pair<Double, Double>? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        var best: Location? = null
        for (p in providers) {
            try {
                if (!lm.isProviderEnabled(p)) continue
                val loc = lm.getLastKnownLocation(p) ?: continue
                if (best == null || loc.accuracy < best.accuracy) best = loc
            } catch (_: Exception) {}
        }
        return best?.let { Pair(it.latitude, it.longitude) }
    }

    private fun requestFresh(lm: LocationManager): Pair<Double, Double>? {
        val latch = CountDownLatch(1)
        var result: Pair<Double, Double>? = null

        val th = HandlerThread("loc").also { it.start() }
        val handler = Handler(th.looper)

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                result = Pair(loc.latitude, loc.longitude)
                latch.countDown()
            }
            @Deprecated("Deprecated in API 29")
            override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
            override fun onProviderEnabled(p: String)  {}
            override fun onProviderDisabled(p: String) { latch.countDown() }
        }

        try {
            val provider = when {
                lm.isProviderEnabled(LocationManager.GPS_PROVIDER)     -> LocationManager.GPS_PROVIDER
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> return null
            }
            lm.requestLocationUpdates(provider, 0L, 0f, listener, th.looper)
            latch.await(15, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            th.quitSafely()
        }
        return result
    }
}
