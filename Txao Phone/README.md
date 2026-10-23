# Txao Phone

Dialer for Android in the TXAO glass look, with the layout and features people know from the stock Google dialer. No analytics, no ads, no accounts, no network access.

- Favorites (starred contacts) and Frequent (most called)
- Recents grouped by caller, with an All / Missed filter, day headings and a call-details page (history, call, message, block, add contact, delete history)
- Contacts in alphabetical sections, create new contact
- Search across contacts and recent calls, or call a typed number
- Keypad with contact suggestions by number or name (T9)
- Voicemail tab that dials your carrier's voicemail
- Blocked numbers: callers on the block list are rejected before they ring (needs Txao Phone as the default phone app)
- In-call screen: answer/decline, mute, speaker, hold, keypad tones, call timer

Written from scratch for TXAO; see `workspace/CONVENTIONS.md`.

## Build and test
```
gradle :app:assembleRelease
gradle :app:testDebugUnitTest
```
Signed APKs go in `dist/` as `txao-phone-v<version>-release-signed.apk` (keystore in `signing/`, git-ignored).
