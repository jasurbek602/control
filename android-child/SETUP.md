# Android setup

1. Android Studio'da `android-child` papkasini oching.
2. Gradle sync qiling.
3. `API_URL` va `DEVICE_SECRET` ni `app/build.gradle.kts` da o'zgartiring yoki keyinchalik CI secrets orqali buildConfig sifatida inject qiling.
4. Local debug uchun `Build > Make Project`.

Eslatma: repo minimal skeleton sifatida wrapper'siz berilgan; Android Studio Project Upgrade/Gradle task wrapperni generatsiya qilishi mumkin. GitHub Actions'da wrapper mavjud bo'lgach `./gradlew assembleDebug` ishlaydi.
