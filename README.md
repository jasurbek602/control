# Parental Control — safe starter monorepo

Tarkib:
- `web/` — Next.js ota-ona paneli + Vercel API + MongoDB
- `android-child/` — Android bola ilovasi (Kotlin)
- `.github/workflows/` — Android APK build

## Muhim ishlash qoidasi
Screenshot va screen capture Android `MediaProjection` orqali foydalanuvchi roziligi bilan ishlaydi. Android 14+ har bir yangi capture session uchun qayta rozilik talab qiladi. Kamera esa `CAMERA` runtime permission bilan ishlaydi. Bu loyiha yashirin kamera/screen capture qilmaydi.

## 1. MongoDB
MongoDB Atlas'da database yarating va `MONGODB_URI` ni oling.

## 2. Web
```bash
cd web
npm install
cp .env.example .env.local
npm run dev
```

`.env.local`:
```env
MONGODB_URI=mongodb+srv://...
DEVICE_SHARED_SECRET=change-me
NEXT_PUBLIC_APP_NAME=Family Guard
```

## 3. Vercel
GitHub repository'ni Vercel'ga import qiling. `web` ni Root Directory qilib tanlang va env variables kiriting. Next.js Vercel'da first-class qo'llab-quvvatlanadi.

## 4. Android
Android Studio Quail 3 / AGP 9.3.x bilan oching.
`local.properties` ga SDK yo'lini qo'ying.

Debug build:
```bash
./gradlew assembleDebug
```

GitHub Actions `assembleDebug` ishlatadi va APK artifact sifatida chiqaradi.

## 5. Qurilma ulash
Bola ilovasida Device ID va pairing code yaratiladi. Web panelda shu code bilan qurilma ulanadi.

## API oqimi
1. Child app `/api/device/register` orqali device yaratadi.
2. Child app davriy `/api/device/heartbeat` qiladi.
3. Parent panel `/api/request` orqali `SCREENSHOT`, `CAMERA_FRONT`, `CAMERA_BACK`, `SCREEN_SHARE` request yaratadi.
4. Child app `/api/request/pending` ni poll qiladi.
5. Bola tasdiqlasa action bajariladi va `/api/request/status` orqali natija yuboriladi.
6. Binary fayllar uchun production'da Vercel Blob/S3 kabi object storage ishlatish tavsiya etiladi; hozirgi starter metadata + placeholder URL saqlaydi.

## Production checklist
- Parent auth (NextAuth/Clerk yoki o'zingizning session layer)
- Device auth uchun per-device rotating token
- MongoDB schema validation + indexes
- Object storage + signed URLs
- Rate limiting
- Audit logs
- WebRTC signaling + STUN/TURN
- TLS only
- Child consent UI va persistent foreground notification

## Device Administrator

Android child app includes a visible `Device Administratorni yoqish` action using Android's `DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN`. The child must approve the system dialog; the app cannot silently grant itself administrator privileges.
