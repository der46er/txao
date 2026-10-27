# Txao Phone

Dialer for Android in the TXAO glass look, with the layout and features people know from the stock Google dialer. No analytics, no ads, no accounts. The only network use is the optional sync with your own Txao server, off until you set it up in Settings.

- Home like Google Phone: search bar with menu and voice search, All / Missed / Contacts / Non-spam chips, collapsible Favorites row, calls grouped by day in rounded cards with direction arrows and the SIM name on dual-SIM phones; second tab is the Keypad
- The latest tap always wins: choosing a tab or opening anything clears the search so stale results never cover the screen
- Contact settings list on the contact page: ringtone, call reminder, share vCard, home-screen shortcut, send to voicemail (also honoured by call screening), emergency contact, block, delete
- Xiaomi Hyper Island: the call notification carries the HyperOS focus-notification extras (best effort, other phones get the normal call notification)
- Emergency contacts in Settings (list synced with your server; the system Emergency information has no public write API, so a button opens it)
- Optional sync of phone-only contacts, photos, backgrounds and emergency contacts with your own Txao server, the same server address and sync key as Notes and the other apps
- Contact photos everywhere (lists, favorites, call details, in-call and incoming screens), with a tinted initial as fallback
- Contacts in alphabetical sections; create, edit and delete contacts inside the app (name, several phones and emails, address, company, birthday, notes, photo)
- Custom background image per contact (editor), used behind their page and the call screen with a readability scrim
- Usernames on WhatsApp, Telegram, Signal, Messenger, Instagram, Discord, Snapchat, X and Skype, tap to open the chat
- Dial pad codes: `*#06#`, USSD (`*123#`, `#31#`, …) with the network reply shown in a dialog, hidden `*#*#…#*#*` codes; long-press backspace clears the number
- Shares contact backgrounds read-only with Txao SMS (`content://com.txao.phone.share/background`) and opens a contact page when Txao SMS asks (`com.txao.phone.action.OPEN_CONTACT`)
- Settings: optionally save new contacts to a Google account so they sync (off by default), editable decline quick replies
- Contact page with photo header, star, edit, call / message / video / email / share / block, labelled numbers and emails, and the full call history
- Search across contacts and recent calls, or call a typed number
- Keypad with contact suggestions by number or name (T9)
- Voicemail in the menu dials your carrier's voicemail
- Blocked numbers: callers on the block list are rejected before they ring (needs Txao Phone as the default phone app)
- Real dialer: asks to become the default phone app on first launch, places calls through Telecom and shows its own call screen for outgoing and incoming calls (never the system dialer)
- In-call screen: answer/decline, decline with a quick-reply message, mute, speaker, hold, add call, switch / merge calls, keypad tones, call timer, country of international numbers

Written from scratch for TXAO; see `workspace/CONVENTIONS.md` (this app lives in `workspace/apps/`).

## Build and test
```
gradle :app:assembleRelease
gradle :app:testDebugUnitTest
```
Signed APKs go in `dist/` as `txao-phone-v<version>-release-signed.apk` (keystore in `signing/`, git-ignored).

## Not yet done
Set calling SIM per contact, speed dial, in-call video UI, call recording, syncing Google-account contacts (they sync through Google), online spam lookup (the suite stays offline).

## Notes
- Contact background images from Google Phone cannot be imported: Google keeps them private to its own apps and does not expose them through contacts sync or any Android API. Pick the image again from the contact page ("Background") instead.
- Platform logos come from Simple Icons (CC0), see `ICON-LICENSES.md`.
