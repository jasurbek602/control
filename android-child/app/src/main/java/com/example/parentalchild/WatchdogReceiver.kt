package com.example.parentalchild

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Projection service watchdog disabled: MediaProjection tokens cannot be
 * recreated safely after process death, so an automatic restart would
 * repeatedly crash on modern Android versions.
 */
class WatchdogReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Intentionally no-op. ScreenCaptureService is user-started after
        // an explicit MediaProjection permission grant.
    }
}
