# Txao SMS

A glass-style SMS app: conversations, pinned chats, search, inline notification replies, send-later,
blocked numbers, delivery reports. **No analytics, no ads, no accounts, no network.** Messages live in
the phone's normal SMS database, so nothing is locked into this app.

- `app/` – the Android app (Kotlin, Jetpack Compose, Material 3)
- `dist/` – built APKs (`-release-signed` to install)
- `signing/` – your release signing key (git-ignored, never commit it)

Written from scratch for Txao; the open-source Fossify Messages app was only used to decide which features
an SMS app needs.

## Features

- Becomes your **default SMS app** (required by Android to send/receive); asks once on first start
- Conversation list with unread counts, contact names, pin, delete, block
- Chat view with day separators, segment counter (GSM-7 / Unicode), copy, delete, retry failed
- Notifications with **Reply** and **Mark as read**
- **Send later**: exact-alarm scheduled messages that survive reboot
- Message search, blocked numbers (blocked senders are dropped silently)
- "Text this number" links (`sms:` / `smsto:`) and the Share menu (e.g. from Txao Notes or Calculator)

Not yet: MMS (picture/group messages), dual-SIM selection, backup/export.

## Build

Open in Android Studio, or `gradle :app:assembleRelease` (needs the Android SDK path in `local.properties`).
Release signing is read from `signing/keystore.properties` if present.

## Tests

`gradle :app:testDebugUnitTest` (number matching, segment counting, scheduled-message storage).

## Permissions

| Permission | Why |
| --- | --- |
| READ/WRITE/SEND/RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH | the job of the default SMS app |
| READ_CONTACTS | show names and pick recipients (optional) |
| POST_NOTIFICATIONS | notify about new messages |
| USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED | send-later at the exact minute, re-armed after reboot |
