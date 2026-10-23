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
1. Copy the signed APK to `workspace/downloads/txao-<name>-<version>.apk`.
2. In `workspace/apps.json`, set that app's `status` to `"available"`, and add `version` and `file`.
The page renders from `apps.json`; no HTML edits needed. Planned apps are already listed as "Coming soon".

## Branding
Dark `#0D0D0D` background, translucent "glass" cards (`rgba(18,18,18,.88)`, 1px `rgba(255,255,255,.12)` border, rounded), Inter font, white primary buttons, uppercase black-weight headings.
