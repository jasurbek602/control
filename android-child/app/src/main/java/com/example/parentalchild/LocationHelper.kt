package com.example.parentalchild

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
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

        val client = LocationServices.getFusedLocationProviderClient(ctx)
        val latch   = CountDownLatch(1)
        var result: Pair<Double, Double>? = null

        try {
            // 1) Avval so'nggi ma'lum joylashuvni tekshir
            client.lastLocation.addOnSuccessListener { loc ->
                if (loc != null && loc.accuracy <= 100f) {
                    val age = System.currentTimeMillis() - loc.time
                    if (age < 5 * 60 * 1000L) { // 5 daqiqadan yangi
                        result = Pair(loc.latitude, loc.longitude)
                        latch.countDown()
                    }
                }
                if (result == null) latch.countDown()
            }.addOnFailureListener {
                latch.countDown()
            }

            latch.await(5, TimeUnit.SECONDS)
        } catch (_: Exception) {}

        // So'nggi joylashuv yaxshi bo'lsa qaytaramiz
        if (result != null) return result

        // 2) Yangi joylashuv so'raymiz
        return requestFresh(client)
    }

    private fun requestFresh(client: FusedLocationProviderClient): Pair<Double, Double>? {
        val latch  = CountDownLatch(1)
        var result: Pair<Double, Double>? = null

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
            setMaxUpdates(1)
            setWaitForAccurateLocation(false)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(res: LocationResult) {
                val loc = res.lastLocation ?: return
                result = Pair(loc.latitude, loc.longitude)
                latch.countDown()
            }
        }

        try {
            client.requestLocationUpdates(
                request, callback, Looper.getMainLooper()
            )
            // 30 soniya kutamiz
            latch.await(30, TimeUnit.SECONDS)
        } catch (_: Exception) {
        } finally {
            try { client.removeLocationUpdates(callback) } catch (_: Exception) {}
        }

        return result
    }
}
