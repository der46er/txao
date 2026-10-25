# Txao Files

A glass-style file manager in the look of Fossify File Manager, on the TXAO wallpaper (or a background image you pick).

- **Files**: search pill with layout, sort and more buttons, storage chip + clickable breadcrumb, folder grid or list rows with item counts / size / date, `+` button (new folder, new file, paste), bottom bar with Files, Recents and Storage.
- **Layout per folder**: the grid/list button affects only the folder you are in and is remembered for it. Folders without a choice use a default: storage roots are a grid, every folder below is a list. "More > Use default layout here" forgets a folder's choice.
- **Selection**: long-press to select; share, copy, cut, rename, delete.
- **Recents** (newest files) and **Storage** (used/free per volume, including SD cards).
- **Background**: More > Choose background image (kept inside the app only).
- Needs the "All files access" permission, granted once in Android settings. No analytics, no ads, no accounts, no network permission.

Written from scratch (clean-room) for TXAO; Fossify File Manager was used only as a visual reference.

## Build

`gradle :app:assembleRelease` (needs the Android SDK path in `local.properties`). Signing is read from `signing/keystore.properties` if present.

## Tests

`gradle :app:testDebugUnitTest`
