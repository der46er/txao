# Txao Music Player

Offline music player for the audio stored on your device. No accounts, no ads, no analytics, no network access.

Features: songs / albums / artists browsing, search, background playback with media notification and lock-screen controls, queue-aware shuffle and repeat (off / all / one), seek bar, sleep timer.

All code is original and written from scratch (see `../../CONVENTIONS.md`, clean-room rule).

## Build
```
gradle :app:assembleRelease
```
Tests:
```
gradle :app:testDebugUnitTest
```
Signing is read from `signing/keystore.properties` (git-ignored) when present. Built APKs go to `dist/txao-music-v<version>-release-signed.apk`.
