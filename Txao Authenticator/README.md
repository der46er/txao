# Txao Authenticator

A glass-style two-factor authenticator (TOTP and HOTP codes). **No permissions, no network, no analytics, no ads, no accounts.**
Codes are computed on the device from the setup keys you add.

- `app/` – the Android app (Kotlin, Jetpack Compose, Material 3)
- `dist/` – built APKs (`txao-authenticator-v<version>-release-signed.apk`)
- `signing/` – your release signing key. **Back it up; never share it.** (git-ignored)

## Features

- TOTP (RFC 6238) and HOTP (RFC 4226), SHA-1 / SHA-256 / SHA-512, 6–8 digits, custom period
- Add by setup key, pasted `otpauth://` link, a tapped `otpauth://` link, text shared from another app (e.g. Txao Notes), or a screenshot of a QR code
- Tap a code to copy it (flagged sensitive so Android hides it from the clipboard preview); long-press or the bin icon to remove
- Screenshots and the recents thumbnail are blocked (`FLAG_SECURE`)

## How secrets are stored

All accounts live in one file, `accounts.bin`, encrypted with AES-256-GCM. The key is generated inside the
Android Keystore and cannot be exported. Backups are disabled (`allowBackup=false`, extraction rules exclude everything),
so the vault does not leave the phone. **That also means a lost or reset phone loses the accounts:
keep each service's recovery codes.** If the vault cannot be decrypted the app goes read-only rather than overwriting it.

## Build / test

Open the folder in Android Studio, or: `gradle :app:assembleRelease` (needs the Android SDK path in `local.properties`).
Tests: `gradle :app:testDebugUnitTest` – checks the RFC 4226 / 6238 / 4648 test vectors and `otpauth://` parsing.

## Not yet done

- Live camera QR scanning (needs a camera permission and CameraX). QR *images* are supported via the "Link → Read QR from image" option.
- Import/export of other authenticators' backup formats, app lock with biometrics, reordering and search.

Written from scratch against RFC 4226, 6238 and 4648 and the public `otpauth://` URI format; no third-party app code was used.
The only dependency beyond the shared Txao stack is `com.google.zxing:core` (Apache-2.0) for decoding QR images.
