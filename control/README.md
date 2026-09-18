# Family Guard

Monorepo:
- `web/` — ota-ona paneli + API
- `android-child/` — Kotlin Android child app

## Samsung / modern Android hardening

Child app Android 14+ foreground-service talablariga moslashtirilgan. Birinchi ishga tushishda runtime permission dialoglari avtomatik ochilmaydi: foydalanuvchi kerakli funksiyani tugma orqali yoqadi.

Panel faqat amalda qo'llab-quvvatlanadigan `SCREENSHOT`, `LOCATION`, `APP_LIST` va `APP_USAGE` amallarini yuboradi. Remote camera va Screen Share tugmalari olib tashlangan.

Screen capture Android MediaProjection orqali foydalanuvchi roziligi bilan ishlaydi. Accessibility screenshot ham Settings orqali foydalanuvchi tomonidan yoqilgandan keyin ishlaydi.

## Android build

`android-child` AGP 9.3.0 / Gradle 9.5.0 / compileSdk 36 / targetSdk 36 bilan sozlangan.

Google Play uchun 2026-yil 31-avgustdan yangi ilova va update'lar Android 16 (API 36) yoki undan yuqorini target qilishi kerak.
