# TXAO app conventions

Two halves: project/process rules (top) and the **Design system** (bottom, extracted from Txao Calculator and Txao Notes, the reference apps). Shared code: [`txao-core/`](txao-core). Server, sync and cross-app rules: [`SYNC-SPEC.md`](SYNC-SPEC.md). Icons: [`icons/`](icons).

Derived from the existing apps (Calculator, Notes, Files). New apps (Gallery, Calendar, Authenticator, SMS) follow these.

## Clean-room rule
Open-source reference apps (e.g. the Fossify Gallery/Calendar/Messages zips) are for understanding **features only**.
All TXAO code is written from scratch. Do not copy, translate or paraphrase source files, resources, strings or icons.

## Project layout (one Gradle project per app, own folder/repo dir)
```
Txao <Name>/
  app/src/main/{AndroidManifest.xml,java/com/txao/<name>,res}
  app/src/test/java/com/txao/<name>
  build.gradle.kts  settings.gradle.kts  gradle.properties  .gitignore  README.md
  dist/      built APKs: txao-<name>-v<version>-release-signed.apk
  signing/   keystore + keystore.properties (git-ignored, never committed)
```
`rootProject.name = "Txao <Name>"`, `include(":app")`.

## Build setup (copy from Txao Notes)
- Kotlin + Jetpack Compose + Material 3 (Expressive), glass-style UI, dark-friendly.
- AGP `com.android.application` 9.1.0, `org.jetbrains.kotlin.plugin.compose` 2.2.20.
- `namespace` = `applicationId` = `com.txao.<name>`; `compileSdk`/`targetSdk` 37; `minSdk` 26 (Files uses 30 only if required).
- Java/Kotlin JVM target 17; `buildFeatures { compose = true }`.
- Release: `isMinifyEnabled` + `isShrinkResources`, signing read from `signing/keystore.properties` if present.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }`.
- `gradle.properties`: `org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8`, `android.useAndroidX=true`, `android.nonTransitiveRClass=true`, `kotlin.code.style=official`.
- Compose BOM 2025.10.00, material3 1.5.0-beta02, material-icons-extended, activity-compose, lifecycle-viewmodel-compose; tests: junit + org.json.
- `.gitignore`: `signing/ build/ .gradle/ local.properties .kotlin/ .idea/`.

## Product principles
No analytics, no ads, no accounts. Network only for optional, off-by-default features to the user's own server. `allowBackup=false`. Request only permissions the app truly needs and explain them in the manifest.

## Cross-app integration
Apps talk via Android intents, no network (e.g. Calculator long-press `=` sends `ACTION_SEND` text/plain to `com.txao.notes`). Prefer share intents for new links between apps.

## Versioning and release
Bump `versionCode`/`versionName` each release; APK name `txao-<name>-v<version>-release-signed.apk`.
Each app has a README (what it is, build command `gradle :app:assembleRelease`, tests `gradle :app:testDebugUnitTest`).

## Adding an app to the download page
Each app owns ONE small file, `workspace/apps.d/<id>.json`, so parallel PRs never touch the same file:
```json
{ "id": "sms", "name": "Txao SMS", "package": "com.txao.sms", "status": "planned",
  "description": "SMS messaging.", "accent": "#a78bfa" }
```
To release: copy the signed APK to `workspace/downloads/txao-<name>-<version>.apk`, then in your entry set
`"status": "available"`, `"version"` and `"file": "downloads/txao-<name>-<version>.apk"`.
Icon: put a square SVG at `workspace/icons/<id>.svg` and set `"icon": "icons/<id>.svg"` in your entry (apps without one get a generated placeholder, so always set it). `icons/*.svg` are rendered from each app's real launcher icon by `python3 workspace/icons/export_svg.py`; re-run it after changing an icon.
**Never edit `workspace/apps.json`**: it is generated from `apps.d/` by `workspace/build-apps.py`
(a GitHub Action regenerates it on main; you can also run `python3 workspace/build-apps.py` locally).
Pressing Download on the page downloads the APK directly. Planned apps show "Coming soon".

## Branding
The in-app look is defined in **Design system** below (lavender accent, glass, orbs, Material 3 Expressive). `accent` in an app's `apps.d` entry only colours its card on the download page. Download page layout follows a Proton-style product grid.

## Design system (all TXAO apps look and behave like Calculator and Notes)

Every app uses `TxaoTheme`, the glass components and the motion specs from `txao-core` (copy `workspace/txao-core/src/main/kotlin/com/txao/core`
into the app, or add it as a module). This is clean-room: the reference apps are our own code, and no third-party UI code is copied.

### D1. Principles

1. **Private by default.** No analytics, ads, accounts or trackers. `allowBackup=false`, no crash reporters. `INTERNET` is
   declared only by apps that can sync, and nothing is sent until the user turns sync on and enters their own server.
2. **Dark and calm.** Night canvas, soft coloured light behind frosted glass, lavender as the single accent.
3. **Alive but fast.** Spring physics everywhere, short fades for screen changes, haptics on every tap. Nothing blocks input.
4. **One look, one key.** Same theme, components, settings layout and sync key across all apps.

### D2. Stack (identical in all apps)

Kotlin, Jetpack Compose, **Material 3 Expressive** (`androidx.compose.material3:material3:1.5.0-beta02`, Compose BOM
`2025.10.00`), `activity-compose 1.11.0`, `lifecycle-viewmodel-compose 2.9.4`. `minSdk 26`, `targetSdk/compileSdk 37`, Java 17.
Release: `isMinifyEnabled` + `isShrinkResources`, `dependenciesInfo` off, signing from `signing/keystore.properties` (git-ignored).
Single activity, Compose-only UI. Icons are drawn with `Canvas` paths (or `material-icons-core`) so the APK stays small.
Package ids: `com.txao.<app>` (`notes`, `calculator`, `gallery`, `calendar`, `authenticator`, `sms`, `phone`, `music`).
App names: "Txao Notes", "Txao Gallery", ... (always "Txao" + one word).

### D3. Colour

`TxaoTheme(look)` in `txao-core/Theme.kt`. Dynamic colour (Android 12+) is **on by default**; otherwise the fallback palette:

| Role | Dark (default) | Light |
|---|---|---|
| background / surface | `#0B0E14` | `#F6F4FB` |
| onSurface | `#E6E8F2` | M3 default |
| surfaceContainer | `#161A22` | `#ECE9F4` |
| surfaceContainerHigh | `#20252E` | `#E6E3EF` |
| primary (lavender) | `#E9DDFF` (on `#2E1B5C`) | `#5B4B9A` (on white) |
| primaryContainer | `#435275` | `#E6DEFF` |
| secondary (blue-grey) | `#8FA3D6` | `#5E6A8C` |
| secondaryContainer | `#333B50` | `#DCE2F9` |
| tertiary (rose) | `#D7A6C6` | `#8A5A7A` |

Theme setting: `0` dark (default) · `1` light · `2` follow system. The window background and status/nav bars are
transparent over `#0B0E14` (`res/values/themes.xml`, `Theme.Material.NoActionBar`) so the first frame is already dark; no
splash artwork (`ic_splash_empty`).

### D4. Glass surfaces (`txao-core/Glass.kt`)

* `GlassBackground`: `background` colour + three radial "orbs" (`primary` 26 %, `tertiary` 18 %, `secondary` 18 % alpha) at
  (0.10, 0.62, r 0.9), (0.95, 0.78, r 0.8), (0.75, 0.08, r 0.7) of the screen. Calculator uses 30/22/20 %.
* `Modifier.glassPanel(shape, tint, glass, solid, strength)`: clip, vertical gradient of `tint` (alpha ×1.4 → ×0.85) and a
  1 dp edge `linearGradient(white 32 % → 4 % → 14 %)`. When the user switches **Glass effect** off, a flat `solid`
  `surfaceContainer*` colour is used instead. Every panel must honour this switch.
* `GlassCard(radius = 28.dp)`: tint `onSurface @ 7 %`. Dialogs/snackbars use radius 22 and tint `onSurface @ 16 %`.
* Tints by role: neutral `onSurface 8.5 %`, operator `secondary 24 %`, accent `primary 36 %`, primary action `primary 92 %`
  (content `onPrimary`), quiet `onSurface 5.5 %`.
* Text: `onSurface` for content, `onSurfaceVariant` for secondary, `primary` for links and quiet actions, `error` for failures.
* Corners: cards 28, sheets/dialogs 22–28, text fields 16, controls fully round (100 dp).

### D5. Motion (`TxaoMotion`)

| Interaction | Spec |
|---|---|
| Press (keys, icon buttons, chips) | corners spring 100 dp → 14–16 dp, `spring(0.62, MediumLow)`; scale 0.92 (icon) / 0.95 (key), `spring(0.55, Medium)`; glass `strength` 1.7× while pressed |
| Screen change (`AnimatedContent`) | `fadeIn(240ms) + scaleIn(0.95, spring(0.8, MediumLow))` togetherWith `fadeOut(120ms)` |
| App start | content `alpha` 0 → 1 over 300 ms over the already-dark window |
| Snackbar | glass pill, 3 s (6 s when it has *Undo*), bottom, 96 dp above the nav bar |
| Everything else | `MotionScheme.expressive()` defaults (set on `MaterialExpressiveTheme`) |

Haptics: `VirtualKey` on tap, `LongPress` on long-press, both gated by the **Haptics** setting. Respect system animation scale.

### D6. Components

`GlassCard`, `GlassIconButton` (round, tinted variant), key-style buttons, `GlassBackground`, `Fill`, the settings `Section`
(glass card with rows) and `SwitchRow(title, subtitle, checked)`. Large primary actions are full-width round glass buttons in
the `primary 92 %` tint. Lists are glass cards (radius 28) on the orbs, 12 dp apart, 16 dp screen padding. Top bars are
transparent with `GlassIconButton`s; no elevation shadows. Use `systemBarsPadding()`, edge-to-edge.

### D7. Settings screen (same order everywhere)

1. **Appearance**: Theme (dark / light / system), Dynamic colour, Glass effect, Haptics.
2. App-specific sections.
3. **Sync across devices** (optional; see SYNC-SPEC.md): server address, access code, "Allow http://", sync key
   (*Create a new key* / *I already have a key* / *Show key* / *Remove key*), *Connect and sync*, *Sync now*, *Turn off sync*.
4. **Privacy** blurb: "No analytics, no ads, no accounts, no trackers. ...".
5. **About**: version, "Made by Txao".

Preferences live in `SharedPreferences("settings")` via the delegate classes in `NotesSettings.kt` (`BoolPref`, `IntPref`,
`StringPref`); secrets (the sync key) are wrapped with an **Android Keystore AES-256-GCM** key (`SecretStore`), never stored plain.
Settings classes implement `TxaoLook` so they can be passed to `TxaoTheme`.

### D8. Encryption

* **Sync key**: 256 random bits, shown `TXAO-XXXX-XXXX-...` (RFC-4648 base32, 52 chars). One key for all apps.
* **Per-app derivation**: `authToken = hex(HMAC-SHA256(key, "txao-<app>/auth/v1"))`, `encKey = HMAC-SHA256(key, "txao-<app>/enc/v1")`.
  The server keeps only `SHA-256(authToken)`. Labels for `notes` are unchanged, so existing Notes accounts keep working.
* **Items and blobs**: AES-256-GCM, output = 12-byte random nonce ‖ ciphertext+tag, AAD = the item id (`blob:<id>` for blobs), so a
  server cannot swap items.
* **At rest on the device**: private app storage; sensitive stores (Authenticator seeds, SMS archive) are additionally encrypted
  with a Keystore key and may require device credentials / biometrics.
* Never log keys, tokens or plaintext. A generic `User-Agent` (`Txao`) is sent.

Reference code: `txao-core/Crypto.kt` (`TxaoCrypto`), tested in `TxaoCryptoTest` (pins the Notes vector).

### D9. Icon family

Generated by [`icons/generate_icons.py`](icons/generate_icons.py) (`python generate_icons.py`, output in `icons/out/<app>/res`,
SVG masters in `icons/out/svg`, contact sheet `icons/out/preview.html`). Grammar, shared with Calculator and Notes:

* 108×108 adaptive icon, `background` + `foreground` + `monochrome` vectors (+ `mipmap-anydpi-v26/ic_launcher.xml`).
* Background: `#0B0E14` + three radial lights (lavender `#B79CFF` 55 %, rose `#D7A6C6` 40 %, blue-grey `#8FA3D6` 40 %); the light
  layout alternates between two arrangements so neighbouring icons differ slightly.
* Foreground (group scale 0.74, glass shape inside the 66 dp safe zone): one frosted-glass shape (`#55FFFFFF → #14FFFFFF`,
  1 u edge `#80FFFFFF`), white glyph strokes `#F2FFFFFF`, round caps, plus **one lavender accent disc** (r = 13,
  `#F1E8FF → #CDB8FF`) at the lower right with a dark `#2E1B5C` glyph.
* Monochrome: outline of the same shapes, 3 u strokes, for themed icons.

| App | Glass shape | Glyph | Accent disc |
|---|---|---|---|
| Notes (existing) | note card | three lines | tick |
| Calculator (existing) | four keys | + − × = | (= key is the accent) |
| Gallery | photo card | mountains + sun | sparkle |
| Calendar | calendar card with rings | date dots | "today" dot |
| Authenticator | shield | keyhole | clock hands |
| SMS | speech bubble | three dots | lock |
| Phone | rounded square | handset | outgoing arrow |
| Music | rounded square | note | play |

Status-bar/notification icons: single-colour 24 dp vectors in the same stroke style (see Notes `ic_stat_note.xml`).
