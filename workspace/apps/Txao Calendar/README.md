# Txao Calendar

A Google Calendar–style calendar with the TXAO glass look: schedule, day, 3-day, week and month views, repeating events,
reminders, colours, several calendars and optional access to the calendars synced on the phone.
Everything is stored on the phone. No analytics, no ads, no accounts, and the app has no INTERNET permission.

- **Views** (hamburger menu): Schedule, Day, 3 days, Week, Month. Swipe sideways for the previous / next day, 3 days or week, swipe up/down between months.
  Tap the title to drop down a mini month, the calendar-icon button jumps to today, search looks through titles, places and notes.
- **Week / day grid**: events sit side by side when they overlap, a red line marks "now", all-day events sit in a banner row, tap an empty slot to get a "New event" block there.
- **Month**: event chips in each day (as many as fit), tap a date for that day's list.
- **Events**: all-day or timed, multi-day, location, description, notifications (several per event), Google's 11 event colours or the calendar's colour.
- **Repeats**: every day / week on a weekday / month / year, every weekday, or custom (every N, weekdays, ends never / after N times / on a date); delete just one occurrence.
- **Calendars**: create your own calendars with a colour, tick them on and off in the menu. With the optional device-calendar switch (Settings, or "Show device calendars" in the menu) the app also reads, creates, edits and deletes events in
  the calendars the system syncs (Google, Exchange, DAVx5 …). That is the only reason the app asks for the calendar permission.
- Reminders as exact notifications for the app's own calendars, restored after reboot (device calendars use the system's reminders)
- Import `.ics` (open a file or Settings → Import) and export your calendar (Settings → Export)
- Share text from any app (e.g. Txao Notes or Calculator) to start a new event; long-press the app icon for "New event"

## Build

Open the folder in Android Studio, or: `gradle :app:assembleRelease`
(needs the Android SDK path in `local.properties`).

## Tests

`gradle :app:testDebugUnitTest` (repeat expansion, reminder planning, iCalendar, storage format, overlapping-event layout).

## Notes

Written from scratch for TXAO; the open-source Fossify Calendar was used only to decide which features to offer.
