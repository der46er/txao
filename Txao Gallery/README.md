# Txao Gallery

A glass-style photo and video gallery. Albums, all-media timeline grouped by day, favourites, search,
hide albums, sort, pinch-to-zoom viewer, video playback, slideshow, share, set as wallpaper, file details (EXIF)
and system trash (Android 11+). No analytics, no ads, no accounts, no network permission.

Written from scratch (clean-room) for TXAO; only platform APIs and Compose are used.

## Build

Open the folder in Android Studio, or: `gradle :app:assembleRelease`
(needs the Android SDK path in `local.properties`). Signing is read from `signing/keystore.properties` if present.

## Tests

`gradle :app:testDebugUnitTest`

## Permissions

- `READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO` (Android 13+), `READ_EXTERNAL_STORAGE` (up to 12), to list your media.
- `WRITE_EXTERNAL_STORAGE` (Android 9 and older only), to delete files there.
- `ACCESS_MEDIA_LOCATION`, to show GPS data of a photo in its details.

## Open from other apps

Handles `VIEW` for images and videos, so tapping a photo in another app can open it here.
