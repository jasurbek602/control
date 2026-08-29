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

        // 1) Oxirgi ma'lum joylashuv (yosh bo'lmasa ham ishlatamiz)
        val last = getBestLast(lm)
        if (last != null) return last

        // 2) Yangi joylashuv so'rash (30 soniya)
        return requestFresh(lm)
    }

    private fun getBestLast(lm: LocationManager): Pair<Double, Double>? {
        var best: Location? = null
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER, // tezroq
            LocationManager.GPS_PROVIDER,
            "fused"                           // Android 12+ uchun
        )
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
        val latch  = CountDownLatch(1)
        var result: Pair<Double, Double>? = null

        val th      = HandlerThread("loc").also { it.start() }
        val handler = Handler(th.looper)

        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                // Birinchi kelgan joylashuvni qabul qilamiz
                if (result == null) {
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
            var registered = false
            // Avval Network (tezroq, ichkaridayam ishlaydi)
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, th.looper)
                    registered = true
                } catch (_: Exception) {}
            }
            // GPS ham qo'shamiz (aniqroq)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0L, 0f, listener, th.looper)
                    registered = true
                } catch (_: Exception) {}
            }

            if (!registered) return null

            // 30 soniya kutamiz
            latch.await(30, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try { lm.removeUpdates(listener) } catch (_: Exception) {}
            th.quitSafely()
        }

        return result
    }
}
