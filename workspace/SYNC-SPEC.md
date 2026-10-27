# TXAO sync, server and cross-app compatibility

## Design in one paragraph

Txao Notes already ships an optional, end-to-end encrypted sync to a **user-run server** (`Txao Notes/server`, Python, no
dependencies, SQLite). I read "add a server" as exactly that: **an optional, user-added sync server**, off by default, the same
server for every TXAO app. Nothing new has to be built server-side: the existing server stores opaque encrypted items and blobs
per account, so each app just gets its own account on it. *(Assumption, flag to Nick: if "add a server" meant something else,
e.g. a hosted TXAO service, only the default URL changes; the protocol stays.)*

## Accounts and keys

* The user has **one sync key** (`TXAO-...`) and **one server address (+ access code)**.
* Each app derives its own token and cipher key from that key (`txao-<app>/auth|enc/v1`, see CONVENTIONS §8), so each app is a
  separate account on the same server. Quotas, wipes and compromises are therefore per app, and a bug in one app cannot
  corrupt another's data. The server cannot tell the apps apart or link them.
* `TXAO_MAX_ACCOUNTS` on the server must be ≥ number of apps × devices' keys (default 20 is fine for one person; raise for more).

## Wire protocol (unchanged from Notes 1.0)

```
GET  /v1/health
GET  /v1/changes?since=<seq>        -> {"seq", "items":[{"id","seq","updatedAt","ciphertext"}], "more"}
PUT  /v1/items/<id>  {"updatedAt","ciphertext"}   -> 200, or 409 if the server copy is newer
GET|HEAD|PUT /v1/blobs/<id>        raw ciphertext
Headers: Authorization: Bearer <authToken>, X-Access-Code: <server access code>
```

Conflict rule: **last writer wins per item** (newest `updatedAt`), deletes are tombstone items
`{"t":"del","id":"<itemId>","updatedAt":ms}`. Clients pull first, then push dirty items, then fetch missing blobs. Ids match
`[A-Za-z0-9_\-:.]{1,80}`. Client code: `txao-core/Transport.kt` (`Transport`, `HttpTransport`); each app writes its own
`SyncEngine` like Notes' `Sync.kt` against its own repository.

Ciphertext plaintext = UTF-8 JSON `{"t": <type>, ...}`. Item id prefix says the type; AAD is the full item id.

## Item types per app

| App | Item ids / `t` | Blobs | Notes |
|---|---|---|---|
| Notes | `n_<id>` note, `l_<id>` label | pictures | as shipped |
| Gallery | `m_<id>` media metadata (name, date, album, favourite, tags), `a_<id>` album | encrypted originals/thumbnails, **opt-in** per album (default: metadata only) | server limit `TXAO_MAX_BLOB_MB` (raise to ~200 for video) |
| Calendar | `c_<id>` calendar, `e_<id>` event (recurrence rule as RFC 5545 string, reminders) | none | device-calendar import stays local |
| Authenticator | `k_<id>` account (issuer, name, algorithm, digits, period, **secret**) | none | highest sensitivity: sync is a separate switch, off until enabled; secrets are never shown in logs or notifications |
| SMS | `s_<id>` per-conversation settings (pin, mute, archive, labels), `b_<id>` blocked number, `q_<id>` quick replies | none | message bodies are **not** synced (the carrier/phone number is the source of truth); an encrypted archive export is separate and manual |
| Phone | `c_<id>` contact (name, phones, emails, address, company, birthday, notes, IM usernames, starred), `e_<phoneKey>` emergency contact (name, number); planned: `f_<id>` speed dial, `b_<id>` blocked number, `r_<id>` call-note | `cp_<id>_<hash>` contact photo, `cb_<id>_<hash>` contact background image | only contacts stored on the phone itself (not Google/Exchange accounts, those already sync through their own account); call log is not synced; the system Emergency information cannot be written by apps |
| Music | `p_<id>` playlist (track references by title/artist/duration hash), `h_<id>` favourites, listening stats | none | audio files are never uploaded |

Every item has `updatedAt` (ms) and `t`; unknown `t` values are ignored (forward compatible); an item that fails to decrypt is
skipped and counted, never fatal.

## Setting up a second app (no retyping)

1. The first app with sync configured exposes its setup through a `ContentProvider` (`com.txao.<app>.syncconfig`) guarded by
   the signature permission `com.txao.permission.SYNC_CONFIG`.
2. Any other TXAO app shows **"Use sync setup from Txao Notes"** in Settings → Sync when it finds a provider; on tap the user
   confirms and the server address, access code and key are copied into the new app's own Keystore-wrapped storage.
3. Fallback (and for apps signed with a different key): copy/paste the key and address as in Notes today.

The permission is signature-level, so it only works when all TXAO apps are signed with **the same release key** (each app
currently has its own `signing/`). *Assumption to confirm:* reuse one shared key for all apps (backed up securely).

## Cross-app links (no network, no dependency)

* Scheme `txao://<app>/<kind>/<id>`; each app registers an `ACTION_VIEW` filter for its own host.
  Examples: `txao://notes/note/<id>`, `txao://calendar/event/<id>`, `txao://gallery/media/<id>`, `txao://music/playlist/<id>`.
* Text to Notes: `ACTION_SEND text/plain` to `com.txao.notes` (already how Calculator's long-press on `=` works).
  Available to every app through `TxaoLinks.sendToNotes`.
* Add to Calendar: `com.txao.action.ADD_EVENT` with extras `title`, `start`, `notes` (Calendar opens a prefilled sheet).
  Used by Notes reminders, SMS ("remind me") and Phone (call-back reminder).
* Call / message: Phone and SMS keep the standard `ACTION_DIAL` / `ACTION_SENDTO sms:` intents so TXAO apps work with each
  other and with any other app, and own the `txao://phone/call/<number>` / `txao://sms/thread/<id>` links for deep linking.
* Authenticator codes can be copied/auto-filled into other TXAO apps only through the user's clipboard (auto-cleared after 30 s).
* Every caller uses `TxaoLinks.launch` and hides the feature when the target app is not installed.

## Server hardening notes (for the README of each app)

HTTPS via the bundled Caddy compose file; plain `http://` only on a trusted LAN (explicit switch in the app); set
`TXAO_ACCESS_CODE`; the server stores only random ids, timestamps and ciphertext, so a stolen disk reveals no content.
