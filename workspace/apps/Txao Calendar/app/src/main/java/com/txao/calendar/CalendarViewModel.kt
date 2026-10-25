package com.txao.calendar

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = EventRepository(app)
    private val calRepo = CalendarRepository(app)
    val settings = CalendarSettings(app)
    val events = mutableStateListOf<Event>().also { it.addAll(repo.load()) }
    val localCalendars = mutableStateListOf<Cal>().also { it.addAll(calRepo.load()) }

    /** Calendars from the phone's calendar provider (empty until permission is granted and the setting is on). */
    var deviceCalendars by mutableStateOf<List<Cal>>(emptyList())
        private set

    /** Bumped whenever device events may have changed, so views re-query. */
    var deviceTick by mutableIntStateOf(0)
        private set

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) { refreshDevice() }
    }
    private var observing = false

    init {
        Reminders.ensureChannel(app)
        Reminders.rescheduleAll(app, events)
        refreshDevice()
    }

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    fun newId(): String = UUID.randomUUID().toString()

    val calendars: List<Cal> get() = localCalendars + deviceCalendars
    fun calendar(id: String): Cal? = calendars.firstOrNull { it.id == id }
    fun isVisible(id: String): Boolean = id !in settings.hiddenCalendars

    fun setVisible(id: String, visible: Boolean) {
        settings.hiddenCalendars = if (visible) settings.hiddenCalendars - id else settings.hiddenCalendars + id
    }

    /** ARGB colour an event is drawn in: its own colour, else its calendar's. */
    fun colorOf(e: Event): Int = if (e.color != 0) e.color else calendar(e.calendarId)?.color ?: DefaultCalendarColor

    // ---- device calendars -------------------------------------------------------------------------------------

    fun refreshDevice() {
        val app = getApplication<Application>()
        val on = settings.deviceCalendars && DeviceCalendars.granted(app)
        deviceCalendars = if (on) DeviceCalendars.calendars(app) else emptyList()
        if (on && !observing) {
            runCatching { app.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer); observing = true }
        } else if (!on && observing) {
            app.contentResolver.unregisterContentObserver(observer); observing = false
        }
        deviceTick++
    }

    fun setDeviceCalendars(on: Boolean) {
        settings.deviceCalendars = on
        refreshDevice()
    }

    // ---- reading ----------------------------------------------------------------------------------------------

    /** Visible occurrences grouped by day for [from, toExclusive), from local and device calendars. */
    fun index(from: LocalDate, toExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Occurrence>> {
        val hidden = settings.hiddenCalendars
        val f = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val t = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val all = ArrayList<Occurrence>()
        for (e in events) if (e.calendarId !in hidden) all += Recurrence.occurrences(e, f, t, zone)
        val deviceIds = deviceCalendars.map { it.id }.filter { it !in hidden }.toSet()
        if (deviceIds.isNotEmpty()) all += DeviceCalendars.instances(getApplication(), f, t, deviceIds, zone)
        return Days.spread(all, from, toExclusive, zone)
    }

    /** Occurrences whose title, place or notes contain [query], within a year either side of today. */
    fun search(query: String): List<Occurrence> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val today = LocalDate.now()
        val seen = HashSet<Pair<String, Long>>()
        return index(today.minusYears(1), today.plusYears(1)).values.flatten()
            .filter { o -> seen.add(o.event.id to o.start) }
            .filter { o -> o.event.title.contains(q, true) || o.event.location.contains(q, true) || o.event.notes.contains(q, true) }
            .sortedBy { it.start }.take(200)
    }

    /** Device events don't carry their reminders in the occurrence query; fetch them when an event is opened. */
    fun withReminders(e: Event): Event = if (e.isDevice) e.copy(reminders = DeviceCalendars.reminders(getApplication(), e.id)) else e

    // ---- writing ----------------------------------------------------------------------------------------------

    /** Adds or updates an event. [original] is the event before editing (null for a new one), used to move it between calendars. */
    fun save(e: Event, original: Event?) {
        val app = getApplication<Application>()
        val movedOrNew = original == null || original.calendarId != e.calendarId
        when {
            e.isDevice && !movedOrNew && original != null -> {
                DeviceCalendars.update(app, e, original)
                deviceTick++
            }
            e.isDevice -> {
                if (original != null) removeEvent(original)
                val created = DeviceCalendars.insert(app, e)
                if (created == null) upsert(e.copy(calendarId = LOCAL_CALENDAR))
                deviceTick++
            }
            else -> {
                if (original != null && original.isDevice) { DeviceCalendars.delete(app, original.id); deviceTick++ }
                upsert(e)
            }
        }
    }

    private fun upsert(e: Event) {
        val i = events.indexOfFirst { it.id == e.id }
        if (i >= 0) events[i] = e else events += e
        persist(e)
    }

    private fun removeEvent(e: Event) {
        if (e.isDevice) DeviceCalendars.delete(getApplication(), e.id) else delete(e.id)
    }

    fun delete(id: String) {
        if (id.startsWith(DEVICE_PREFIX)) {
            DeviceCalendars.delete(getApplication(), id)
            deviceTick++
            return
        }
        events.removeAll { it.id == id }
        Reminders.cancel(getApplication(), id)
        repo.save(events.toList())
    }

    /** Remove one showing of a repeating event; the series itself stays. */
    fun deleteOccurrence(o: Occurrence) {
        val e = o.event
        if (e.isDevice || e.repeat.freq == Freq.NONE) delete(e.id) else upsert(e.copy(exdates = e.exdates + o.start))
    }

    fun importIcs(text: String): Int {
        val parsed = ICal.parse(text)
        // Same UID replaces the existing event, so importing our own export again doesn't duplicate.
        parsed.forEach { upsert(it) }
        return parsed.size
    }

    fun exportIcs(): String = ICal.write(events.toList())

    // ---- calendars --------------------------------------------------------------------------------------------

    fun addCalendar(name: String, color: Int) {
        localCalendars += Cal(newId(), name.trim().ifEmpty { "Calendar" }, color)
        calRepo.save(localCalendars.toList())
    }

    fun updateCalendar(c: Cal) {
        val i = localCalendars.indexOfFirst { it.id == c.id }
        if (i >= 0) { localCalendars[i] = c; calRepo.save(localCalendars.toList()) }
    }

    /** Deletes a local calendar and its events. The default calendar can't be removed. */
    fun deleteCalendar(id: String) {
        if (id == LOCAL_CALENDAR) return
        events.filter { it.calendarId == id }.forEach { Reminders.cancel(getApplication(), it.id) }
        events.removeAll { it.calendarId == id }
        repo.save(events.toList())
        localCalendars.removeAll { it.id == id }
        calRepo.save(localCalendars.toList())
        setVisible(id, true)
    }

    private fun persist(changed: Event) {
        repo.save(events.toList())
        Reminders.schedule(getApplication(), changed)
    }
}
