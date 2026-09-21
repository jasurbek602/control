package com.example.parentalchild

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Do not auto-start ScreenCaptureService on boot: a MediaProjection token is
 * user-granted and is not available after reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        // Intentionally no-op.
    }
}
