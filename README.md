# Football Live TV â½ðº

A native Android sports live-streaming application built with **Kotlin**, **Jetpack Compose**, **Material 3**, and **AndroidX Media3 ExoPlayer**.

---

## â¡ Direct APK Download via GitHub Actions & Releases

This repository includes a pre-configured **GitHub Actions CI/CD pipeline** located at [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) that automatically builds the debug APK and makes it available for immediate direct download.

### Option 1: Download from GitHub Releases (Recommended)
1. Go to your repository on GitHub.
2. Click on the **Releases** tab on the right side of the repository page (or navigate to `https://github.com/<username>/<repo>/releases`).
3. Click on the latest release (e.g. `Football Live TV v7.3`).
4. Under **Assets**, click **`Football-Live-TV-v7.3-debug.apk`** to download it directly to your phone or computer.

### Option 2: Download from GitHub Actions Artifacts
1. Go to the **Actions** tab in your GitHub repository.
2. Click on the latest run of **Build & Release Debug APK**.
3. Scroll down to the **Artifacts** section at the bottom of the summary page.
4. Click on **`Football-Live-TV-Debug-APK`** to download the built APK.

### Option 3: Trigger a Manual Build Anytime (One-Click)
1. Navigate to the **Actions** tab in your GitHub repository.
2. In the left sidebar, click **Build & Release Debug APK**.
3. Click the **Run workflow** button on the right.
4. Ensure *Publish a GitHub Release* is checked, choose your branch (e.g. `main`), and click **Run workflow**.
5. Once the job completes (usually ~2â3 minutes), the new APK will be uploaded to Releases and Artifacts.

---

## ð  Local Build Instructions

If you wish to build the debug APK locally on your machine:

### Prerequisites:
- JDK 17 or JDK 21 (Temurin / OpenJDK)
- Android SDK installed with API 36 / 34 build-tools

### Build Command:
```bash
# Make gradlew executable (macOS / Linux)
chmod +x gradlew

# Build the debug APK
./gradlew :app:assembleDebug
```

The compiled APK will be located at:
```
app/build/outputs/apk/debug/app-debug.apk
```

---

## ð± Features

- **Cricket match updates**: International Test, ODI, and T20I fixtures limited to yesterday, today, and tomorrow in Asia/Dhaka.
- **Football match updates**: Club and international scores from ESPN's global soccer scoreboard, using the same three-day Dhaka calendar window.
- **Built-in Media3 ExoPlayer**: Native player supporting HLS (`.m3u8`), MPEG-DASH (`.mpd`), and progressive streams.
- **Multiple-Stream Picker**: Instant source switching between FHD, HD, and adaptive servers with quality badges.
- **Asia/Dhaka Fixture Schedules**: Match timings and countdown timers displayed in `Asia/Dhaka` (GMT+6) timezone.
- **M3U Playlist Importer**: Import and stream IPTV channels via URL or raw text with channel grouping and search.
- **Custom Player Controls**: Aspect ratio cycling (Fit / Zoom / Fill), Picture-in-Picture (floating player), seek Â±10s, mute, full screen landscape support.
- **Bottom Navigation**: Favourites, Sports, Live Events, Categories, and Menu.

## In-app Admin Panel

The Admin Panel is part of the Android app (Menu → Admin Panel). It supports:

- Email/password sign-in for admins only.
- Adding, editing, hiding, deleting, and ordering channels and categories.
- Multiple named stream URLs per channel or match, including HLS `.m3u8` and DASH `.mpd`.
- Checking whether a stream URL responds over HTTP(S).
- Editing feed matches and adding manual matches, including score, status, Dhaka start time, and stream choices.

### One-time Firebase setup

The admin screen and public sync use Firebase Authentication and Cloud Firestore. The APK does not contain an admin password or a client-side admin allowlist; Firestore rules enforce admin-only writes.

1. In Firebase Authentication, enable **Email/Password** and create the admin account.
2. Create a Cloud Firestore database.
3. In Firestore, create a document at `admins/{ADMIN_AUTH_UID}`. Use the admin's Firebase Authentication UID as the document ID; the document body can contain `{ "role": "admin" }`.
4. Deploy the repository's rules with `firebase deploy --only firestore:rules`.
5. Ensure the existing GitHub Actions secret `FIREBASE_GOOGLE_SERVICES_JSON` is configured for the Android app's Firebase project before building the APK.

The app uses the `channels` and `matchOverrides` collections. Channel documents with `isActive: true` are visible to regular users; all writes require an `admins/{uid}` record. Match override documents are public app data and are writable only by admins. Do not put private CDN signing keys or other secrets in public stream URLs or Firestore documents.
