# Samsung S22 / modern Android hardening summary

## Root causes addressed

1. The original app started `ScreenCaptureService` immediately with a foreground-service declaration containing `camera|location|mediaProjection`. On Android 14+, foreground-service types have type-specific prerequisites. Starting such a service before those permissions/consents can throw `SecurityException`.
2. The original `MainActivity` automatically requested `POST_NOTIFICATIONS` from `onCreate()`. This was removed. Notification permission is now requested only from the explicit button.
3. The original app attempted camera work from a long-running background service. Camera FGS creation is restricted while the app is backgrounded on Android 14+; the remote camera feature was removed.
4. The original app used boot receivers, watchdog alarms, exact alarms, battery-optimization bypass and Device Admin. Those were removed from the manifest and UI to reduce OEM/Android startup and permission failure points.
5. The original build used AGP 9.3.0 with Gradle 8.11.1. AGP 9.3 requires Gradle 9.5.0 or newer. The wrapper was updated to Gradle 9.5.
6. The parent web panel no longer exposes removed camera/screen-share actions.

## Remaining user-approved actions

- Screen capture: MediaProjection consent button or Accessibility screenshot.
- Location: runtime location permission button.
- Usage access: Settings button.
- Notifications: runtime permission button.
- App list and usage reporting.

## Important Android 15 behavior

`dataSync` foreground services have a six-hour total limit per 24-hour period on Android 15+ for apps targeting API 35+. The service implements `onTimeout()` and stops instead of crashing when that limit is reached. For production always-on command delivery, FCM/WorkManager should replace continuous five-second polling.
