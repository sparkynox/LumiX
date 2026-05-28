# Lumi X ★ 

> YouTube background play + no-ads client using yt-dlp + ExoPlayer

Built by [SparkyNox](https://github.com/SparkyNox) • Yori Ecosystem

---

## Features
- Paste or share any YouTube URL to play instantly
- Background play — screen off, music keeps going
- Audio-only mode (saves data)
- Watch history saved to your account (Firebase)
- Google Sign-In
- ARMv7 (32-bit) + ARM64 (64-bit) builds

## Setup

### 1. Firebase
1. Go to [Firebase Console](https://console.firebase.google.com)
2. Create a project → Add Android app → package: `com.sparkynox.lumix`
3. Enable **Authentication → Google Sign-In**
4. Enable **Firestore Database**
5. Download `google-services.json` → put in `app/` folder

### 2. GitHub Secret
Add `GOOGLE_SERVICES_JSON` to your repo secrets (Settings → Secrets):
- Value = entire contents of `google-services.json`

### 3. yt-dlp binaries
CI downloads them automatically. For local builds:
```bash
mkdir -p app/src/main/assets
curl -L "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_aarch64" -o app/src/main/assets/yt-dlp_arm64
curl -L "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux_armv7l" -o app/src/main/assets/yt-dlp_armv7
```

### 4. Build
```bash
./gradlew assembleDebug
```
APKs will be in `app/build/outputs/apk/debug/`

## CI/CD
GitHub Actions builds APKs on every push. On release, APKs are attached automatically.

---

**Credits:** SparkyNox • [GitHub](https://github.com/SparkyNox) • [Modrinth](https://modrinth.com/user/sparkynox)
