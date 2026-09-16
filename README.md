# WEBUNIME Mobile

App Android HP (Jetpack Compose) + ekonomi Fase 1.

## API katalog

https://webunime-catalog-api.vercel.app

## Fitur Fase 1

- Tab **Akun**: level, XP, kunci, gem, Premium
- **3 kunci awal**; 1 episode = 1 kunci (Premium bebas)
- **Rewarded AdMob** (test ID) → +1 kunci
- Tukar **10 gem → 1 kunci**
- XP per episode (+ level-up → gem)
- **Play Billing** product ID: `webunime_premium_1m/3m/6m/12m` (debug bisa simulate)
- Login Google + sync Firestore (setelah konfigurasi Firebase)

## Build

```powershell
cd "C:\Users\sjatm\OneDrive\Documents\Project\App WEBUNIME Mobile"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug
```

## Setup Firebase (wajib untuk login cloud)

1. Buat project di [Firebase Console](https://console.firebase.google.com/)
2. Add Android app `com.webunime.mobile`
3. Download **google-services.json** → ganti `app/google-services.json`
4. Aktifkan **Authentication → Google**
5. Salin **Web client ID** OAuth ke `BuildConfig.GOOGLE_WEB_CLIENT_ID` di `app/build.gradle.kts`
6. Buat Firestore, tempel rules dari `firestore.rules`
7. (Opsional) deploy `functions/` untuk validasi server

## Setup AdMob / Billing

- Ganti `ADMOB_REWARDED_UNIT_ID` + meta-data `APPLICATION_ID` di Manifest ke unit production
- Di Play Console buat subscription ID sesuai `BillingRepository.plans`
