# Setup Firebase (myproject-fbbb9) — Spark

Project: https://console.firebase.google.com/project/myproject-fbbb9/overview

Package Android: `com.webunime.mobile`

## Spark cukup untuk apa?

| Layanan | Spark | Dipakai app |
|---------|-------|-------------|
| Authentication (Google) | Ya | Login |
| Cloud Firestore | Ya (kuota gratis) | Sync kunci/gem/level |
| Cloud Functions | Tidak (butuh Blaze) | Opsional nanti |
| AdMob / Play Billing | Di luar Firebase | Sudah di app (test ID) |

## Checklist

1. [ ] Add Android app `com.webunime.mobile`
2. [ ] Download `google-services.json` → `app/google-services.json`
3. [ ] Authentication → Google → Enable
4. [ ] Salin Web client ID → `GOOGLE_WEB_CLIENT_ID` di `app/build.gradle.kts`
5. [ ] Firestore create + rules dari `firestore.rules`
6. [ ] Tambah SHA-1 debug keystore, download ulang `google-services.json`
7. [ ] Rebuild `assembleDebug` / Run di Android Studio

## SHA-1 debug

```powershell
keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```

## Setelah login berhasil

Dokumen Firestore: `users/{uid}` berisi `keys`, `gems`, `xp`, `level`, `isPremium`, `premiumUntilMs`.
