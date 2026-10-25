# Txao SMS

A glass-style SMS and MMS app that works hand in hand with Txao Phone: conversations, pinned chats, search,
inline notification replies, send-later, blocked numbers, delivery reports, picture messages. **No analytics, no ads, no accounts, no network.** Messages live in
the phone's normal SMS database, so nothing is locked into this app.

- `app/` – the Android app (Kotlin, Jetpack Compose, Material 3)
- `dist/` – built APKs (`-release-signed` to install)
- `signing/` – your release signing key (git-ignored, never commit it)

Written from scratch for Txao; the open-source Fossify Messages app was only used to decide which features
an SMS app needs.

## Features (modelled on Google Messages, in the Txao glass look)

- Becomes your **default SMS app** (required by Android to send/receive); asks once on first start
- Inbox with a search pill, side drawer (Inbox, Archived, Starred, Spam & blocked, Settings), colored contact avatars,
  bold unread chats, pinned and muted markers, and a "Start chat" button
- **Swipe** a chat to archive it; **long-press** to select several (pin, archive, delete, block, mute)
- Chat: grouped bubbles with the contact picture on the last one, "Today • 14:05" dividers, Sent / Delivered /
  "Not sent · Tap to retry" status, in-chat search, call, mute, archive, block, delete
- Select messages to star, copy, forward, delete or see details (size, encoding, SMS parts)
- Start-chat screen with a contact list (A-Z headers), typed numbers, and forwarding
- Notifications with **Reply** and **Mark as read**; archived chats come back on a new message
- **Send later** via exact alarms (survives reboot), blocked numbers, delivery reports
- **Picture messages (MMS)**: send photos and videos (pictures are scaled to fit the carrier limit), receive and view
  them in the chat, retry failed ones. The system sends/downloads over mobile data; Txao SMS stores them in the normal MMS database
- **Works with Txao Phone**: contact photos and names everywhere, tap the name/picture to open the contact page in Txao Phone,
  call button, the contact's **background image** behind the chat, and their saved **usernames** (WhatsApp, Telegram, ...) as chips you can tap
- Search finds conversations (by name or number) and messages, including the text of picture messages
- "Text this number" links (`sms:` / `smsto:`) and the Share menu (e.g. from Txao Notes or Calculator)

Not yet: group MMS (a message to several people is sent to each one separately), sharing pictures *into* Txao SMS from other apps, RCS, dual-SIM selection, backup/export.

## Build

Open in Android Studio, or `gradle :app:assembleRelease` (needs the Android SDK path in `local.properties`).
Release signing is read from `signing/keystore.properties` if present.

## Tests

`gradle :app:testDebugUnitTest` (number matching, segment counting, scheduled-message storage, MMS encoding/decoding, username links).

## Permissions

| Permission | Why |
| --- | --- |
| READ/WRITE/SEND/RECEIVE_SMS, RECEIVE_MMS, RECEIVE_WAP_PUSH | the job of the default SMS app |
| READ_CONTACTS | show names, photos and usernames, and pick recipients (optional) |
| ACCESS_NETWORK_STATE | picture messages go over mobile data, handled by the system |
| POST_NOTIFICATIONS | notify about new messages |
| USE_EXACT_ALARM, RECEIVE_BOOT_COMPLETED | send-later at the exact minute, re-armed after reboot |
