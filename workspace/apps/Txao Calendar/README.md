# Txao Calendar

A glass-style calendar (month and upcoming views, repeating events, reminders, colours, `.ics` import/export).
Everything is stored on the phone. No analytics, no ads, no accounts, and the app has no INTERNET permission.

- Month grid with swipe between months, day list below, "Upcoming" list for the next 120 days
- Events: all-day or timed, multi-day, location, notes, colour
- Repeats: daily / weekly (pick weekdays) / monthly / yearly, every N, forever / N times / until a date; delete just one occurrence
- Reminders as exact notifications (several per event), restored after reboot
- Import `.ics` (open a file or Menu → Import) and export everything (Menu → Export)
- Share text from any app (e.g. Txao Notes or Calculator) to start a new event; long-press the app icon for "New event"

## Build

Open the folder in Android Studio, or: `gradle :app:assembleRelease`
(needs the Android SDK path in `local.properties`).

## Tests

`gradle :app:testDebugUnitTest` (repeat expansion, reminder planning, iCalendar, storage format).

## Notes

Written from scratch for TXAO; the open-source Fossify Calendar was used only to decide which features to offer.
