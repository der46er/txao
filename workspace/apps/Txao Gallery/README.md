# Txao Gallery

A glass-style photo and video gallery in the style of Google Photos, fully on device:

- **Photos**: timeline grouped by day, draggable date scrubber, pinch to change the grid size, "On this day" memories.
- **Search**: by file name, folder, month/year, or keywords such as videos, screenshots, favorites; category and date chips.
- **Library**: favorites, videos, screenshots, trash (Android 11+), and your device folders (long-press to hide).
- **Viewer**: swipe between items, pinch/double-tap zoom, video, slideshow, share, set as wallpaper, EXIF details.
- **Editor**: rotate, flip, crop ratios, brightness / contrast / saturation, filters; saves a copy to `Pictures/Txao`. No analytics, no ads, no accounts, no network permission.

Written from scratch (clean-room) for TXAO; only platform APIs and Compose are used.

## Build

Open the folder in Android Studio, or: `gradle :app:assembleRelease`
(needs the Android SDK path in `local.properties`). Signing is read from `signing/keystore.properties` if present.

## Tests

`gradle :app:testDebugUnitTest`

## Permissions

- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (Android 13+), `READ_EXTERNAL_STORAGE` (up to 12), to list your media.
- `WRITE_EXTERNAL_STORAGE` (Android 9 and older only), to delete files and save edited copies there.
- `ACCESS_MEDIA_LOCATION`, to show GPS data of a photo in its details.

## Open from other apps

Handles `VIEW` for images and videos, so tapping a photo in another app can open it here.
