# Txao Music Player

Offline music player in the TXAO glass style: songs / albums / artists with cover art, background playback, queue, shuffle, repeat, sleep timer, and an Edit info screen that writes tags (MP3) and library info.

Features (1.1.0): TXAO glass UI (shared theme, orbs, motion), cover art everywhere, songs / albums / artists browsing, search, background playback with media notification and lock-screen controls, queue-aware shuffle and repeat (off / all / one), seek bar, sleep timer, and **Edit info** (the pen on an artist or album page edits that artist or album and all its songs; artists can also get a picture), hide songs and albums (restore in Settings), sort and drag-reorder albums, (title, artist, album, album artist, year, genre, track number, cover art; single song or a whole album/artist). MP3 files get ID3v2.4 tags written into the file (`Id3.kt`); other formats (WAV, FLAC, M4A, ...) keep the edit inside the app, applied on top of the library (`Overrides.kt`). Android asks once per edit to allow changing the files.

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
