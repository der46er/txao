package com.txao.calendar

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import java.util.UUID

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = EventRepository(app)
    val settings = CalendarSettings(app)
    val events = mutableStateListOf<Event>().also { it.addAll(repo.load()) }

    init { Reminders.ensureChannel(app); Reminders.rescheduleAll(app, events) }

    fun newId(): String = UUID.randomUUID().toString()

    fun upsert(e: Event) {
        val i = events.indexOfFirst { it.id == e.id }
        if (i >= 0) events[i] = e else events += e
        persist(e)
    }

    fun delete(id: String) {
        events.removeAll { it.id == id }
        Reminders.cancel(getApplication(), id)
        repo.save(events.toList())
    }

    /** Remove one showing of a repeating event; the series itself stays. */
    fun deleteOccurrence(o: Occurrence) {
        val e = o.event
        if (e.repeat.freq == Freq.NONE) delete(e.id) else upsert(e.copy(exdates = e.exdates + o.start))
    }

    fun importIcs(text: String): Int {
        val parsed = ICal.parse(text)
        // Same UID replaces the existing event, so importing our own export again doesn't duplicate.
        parsed.forEach { upsert(it) }
        return parsed.size
    }

    fun exportIcs(): String = ICal.write(events.toList())

    private fun persist(changed: Event) {
        repo.save(events.toList())
        Reminders.schedule(getApplication(), changed)
    }
}
