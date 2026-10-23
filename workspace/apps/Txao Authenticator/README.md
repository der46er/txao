# Txao Authenticator

A glass-style two-factor authenticator (TOTP, HOTP and Steam Guard codes), laid out in the spirit of Proton Authenticator:
a searchable list of big codes with countdown rings, tap to copy, easy add/import/export. No network access, no analytics,
no ads, no accounts. Codes are computed on the device from the setup keys you add.

- `app/` – the Android app (Kotlin, Jetpack Compose, Material 3)
- `dist/` – built APKs (`txao-authenticator-v<version>-release-signed.apk`)
- `signing/` – your release signing key. **Back it up; never share it.** (git-ignored)

## Features

- **Codes:** TOTP (RFC 6238), HOTP (RFC 4226), Steam Guard; SHA-1/256/512, 6–8 digits, custom period
- **List:** search by service or account, countdown ring (turns red in the last 5 s), upcoming code previewed in the last 10 s,
  tap to copy, long-press / menu to edit, reorder or remove
- **Add:** live camera QR scanner, QR from an image, setup key, pasted/tapped `otpauth://` link, text shared from another app
- **Import:** `otpauth://` link lists, Google Authenticator "Export accounts" links, Aegis plain-text JSON, Txao encrypted backups
- **Export:** password-encrypted backup file (PBKDF2-SHA256 600k iterations + AES-256-GCM) or plain `otpauth://` text (with a warning)
- **Privacy:** optional app lock using the phone's own PIN/pattern/fingerprint, hide-codes-until-tap, clipboard wiped after 30 s,
  copied codes flagged sensitive, screenshots and the recents preview blocked (`FLAG_SECURE`)

## How secrets are stored

All accounts live in one file, `accounts.bin`, encrypted with AES-256-GCM. The key is generated inside the Android Keystore
and cannot be exported. Backups are disabled (`allowBackup=false`, extraction rules exclude everything), so the vault does not
leave the phone. **A lost or reset phone loses the accounts: make an encrypted backup and keep each service's recovery codes.**
If the vault cannot be decrypted the app goes read-only rather than overwriting it.

## Permissions

`CAMERA`, requested only when the QR scanner opens (the app also works without it). No network permission.

## Build / test

Open the folder in Android Studio, or: `gradle :app:assembleRelease` (needs the Android SDK path in `local.properties`).
Tests: `gradle :app:testDebugUnitTest` – RFC 4226 / 6238 / 4648 vectors, Steam, `otpauth://` parsing, Google/Aegis import, backup crypto.

## Not done yet

- Syncing between devices (use the encrypted backup file for now). When the Txao suite sync server exists this is the natural place for it.
- Importing other apps' encrypted exports (Aegis encrypted, Proton, 2FAS), drag-and-drop reordering.

Written from scratch against RFC 4226, 6238 and 4648 and the public `otpauth://` and Google migration formats; no third-party app
code was used. Beyond the shared Txao stack the dependencies are `com.google.zxing:core` (Apache-2.0) for QR decoding and CameraX for the preview.
