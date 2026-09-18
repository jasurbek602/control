# Android setup — Samsung/Android 14/15/16

1. Android Studio'da `android-child` papkasini oching.
2. Gradle sync qiling. Loyiha AGP 9.3.0 va Gradle 9.5.0 bilan moslangan.
3. `API_URL` va `DEVICE_SECRET` ni `app/build.gradle.kts` ichida sozlang.
4. `Build > Make Project` orqali tekshiring.
5. `Build > Build APK(s)` orqali debug APK yarating.

## Birinchi ishga tushish

Ilova ochilganda hech qanday runtime permission avtomatik so'ralmaydi. Bildirishnoma, lokatsiya, Usage access, Accessibility va Screen capture alohida tugmalar orqali yoqiladi.

## Samsung S22 uchun o'zgarishlar

- Android 14+ FGS talablari sabab service avval `dataSync` sifatida ishga tushadi. Screen capture faqat foydalanuvchi MediaProjection roziligidan keyin yoqiladi.
- Remote kamera funksiyasi olib tashlandi: Android 14+ background camera foreground-service cheklovlari sabab bu yo'l ishonchsiz.
- Exact alarm, battery-optimization bypass, Device Admin va boot-time auto-start olib tashlandi; ular o'rnatish va ishga tushish jarayonini keraksiz murakkablashtirgan.
- Android 15 `dataSync` foreground service uchun 24 soat ichida 6 soatlik limit qo'yadi. Limitga yetganda service crash qilmasdan to'xtaydi. Uzoq muddatli production polling uchun FCM/WorkManager arxitekturasi kerak bo'ladi.

## APK o'rnatilmasa

- Eski imzolangan versiya bilan yangi APK signature'lari farq qilsa, avval eski ilovani uninstall qiling yoki bir xil signing key ishlating.
- `minSdk=26`, `targetSdk=36`, `compileSdk=36`.
