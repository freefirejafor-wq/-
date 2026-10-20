# Firebase channel control (Android)

The Android app reads channels from the same Firestore database used by the AI Studio admin website. The web admin remains the only writer.

## Data contract

- Collection: `channels`
- Android query: `isActive == true`
- Fields: `name` (string), `category` (string), `format` (`HLS`, `DASH`, or `PROGRESSIVE`), `isActive` (boolean), `logoUrl` (string), `quality` (string), `sortOrder` (integer), `streamUrl` (string), plus optional timestamps.
- `category` maps to the Android channel group. `sortOrder` controls display order. Inactive documents are not returned to the APK.
- Android subscribes with a Firestore snapshot listener, so admin changes appear without publishing a new APK.

## Firebase config without committing keys

The repository is public. Never commit `google-services.json` or a service-account key.

1. In the same Firebase project as the admin website, register an Android app using the package name from `app/build.gradle.kts` (`com.example`).
2. Download that Android app's `google-services.json`.
3. In GitHub, open **Settings → Secrets and variables → Actions → New repository secret**. Name it `FIREBASE_GOOGLE_SERVICES_JSON` and use the complete contents of `google-services.json` as the secret value. Do not paste the file contents into chat or commit them to the repo.
4. If Firestore uses the default database, leave `FIRESTORE_DATABASE_ID` unset. For a named database, add a repository variable named `FIRESTORE_DATABASE_ID` with the exact database ID shown in the Firestore selector.
5. Re-run **Build & Release Debug APK**. The workflow materializes the config only inside the runner, then builds the APK.

For a local build, place the downloaded file at `app/google-services.json`; `.gitignore` keeps it out of commits. Set `FIRESTORE_DATABASE_ID` in the local environment only when using a named database.

## Firestore security

Keep client writes denied. The Android app only reads active channel records; the admin website should retain authenticated admin checks for create, update, and delete. If the Android listener reports `PERMISSION_DENIED`, update rules to allow reads of active channels only—do not make the whole collection publicly writable.
