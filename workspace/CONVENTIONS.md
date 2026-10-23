# TXAO app conventions

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
Icon: put a square SVG at `workspace/icons/<id>.svg` and set `"icon": "icons/<id>.svg"` in your entry (apps without one get a generated placeholder). The Calculator/Notes/Files icons were recreated from their launcher icons; the suite design thread will replace them with the new icon family, so keep the same path and file name.
**Never edit `workspace/apps.json`**: it is generated from `apps.d/` by `workspace/build-apps.py`
(a GitHub Action regenerates it on main; you can also run `python3 workspace/build-apps.py` locally).
Pressing Download on the page downloads the APK directly. Planned apps show "Coming soon".

## Branding
Dark `#0B0E14` background (matches the apps' `txao_bg`), translucent glass cards with HyperOS-style large corners (24-32px), Material 3 tonal buttons (pill, light-blue primary), soft colour glows. The download page reuses the apps' wallpaper (`#0B0E14` + glows `#8FA3D6`, `#B79CFF`, `#D7A6C6`), glass surfaces (white 30% to 8% gradient), the lavender `#F1E8FF` to `#CDB8FF` accent with `#2E1B5C` text, and shows a Proton-style row of app icons with names underneath.
