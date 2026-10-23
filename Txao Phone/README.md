# Txao Phone

Dialer, recent calls and contacts for Android, with its own in-call screen. No analytics, no ads, no accounts, no network access.

- Keypad with contact search (by number or by typing the name on the keypad)
- Recents from the system call log, tap to call back, clear all
- Contacts list with search
- In-call screen: answer/decline, mute, speaker, hold, keypad tones, call duration
- Can be set as the default phone app

Written from scratch for TXAO; see `workspace/CONVENTIONS.md`.

## Build and test
```
gradle :app:assembleRelease
gradle :app:testDebugUnitTest
```
Signed APKs go in `dist/` as `txao-phone-v<version>-release-signed.apk` (keystore in `signing/`, git-ignored).
