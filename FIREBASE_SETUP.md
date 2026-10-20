# Firebase setup for the in-app admin panel

The Android APK includes its own Admin Panel (Menu → Admin Panel). Channel edits sync through the existing `channels` collection; match edits and manually added matches use `matchOverrides`. Firestore rules grant writes only to accounts whose Firebase Authentication UID has an `admins/{uid}` document.

## Firestore data contract

- `channels`: `name`, `category`, `format`, `quality`, `streamUrl`, `streams`, `isActive`, `sortOrder`, and optional `logoUrl`/timestamps. The app publicly reads only documents where `isActive == true`.
- `streams` is an array of named stream objects, each with `name`, `qualityLabel`, `streamUrl`, and `format`. Existing single-URL channel documents remain supported.
- `matchOverrides`: match fields, scores/status, Dhaka start time, and optional named stream sources. Match data is public app content.
- `admins/{uid}`: admin membership record, created by the Firebase project owner; the app cannot create or edit these records.

## One-time Firebase setup

1. In Firebase Authentication, enable the **Email/Password** provider and create the admin account.
2. Register the Android app in that Firebase project using the package name `com.example`, then download its `google-services.json`.
3. In Firestore, create a document at `admins/{ADMIN_AUTH_UID}`. Replace the placeholder with the admin account's Firebase Authentication UID; the document may contain `{ "role": "admin" }`.
4. Deploy the repository rules with `firebase deploy --only firestore:rules`. Do not make `channels` or `matchOverrides` writable by unauthenticated users.
5. For GitHub Actions builds, store the downloaded file as the repository secret `FIREBASE_GOOGLE_SERVICES_JSON`. Do not commit or paste the file contents into chat. The workflow can still build without it, but Firebase features will be unavailable in that APK.
6. If the project uses a named Firestore database, set the GitHub Actions repository variable `FIRESTORE_DATABASE_ID` to its database ID. Leave it unset for `(default)`.

For local builds, put `google-services.json` at `app/google-services.json`; it is ignored by Git. Set `FIRESTORE_DATABASE_ID` locally only for a named database.

## Admin sign-in and stream URLs

Open **Menu → Admin Panel** in the APK and sign in with the account from step 1. The panel supports channel categories, multiple named streams, visibility/order controls, link checks, match status/scores, and manual matches. Stream checks only confirm that the URL responds over HTTP(S); they do not guarantee the provider permits playback in every region.

Channel URLs are public app data because regular users need them to play streams. Do not store private CDN signing keys, passwords, or other secrets in URLs or Firestore documents.
