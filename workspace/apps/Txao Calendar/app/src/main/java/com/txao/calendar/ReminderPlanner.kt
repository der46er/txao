package com.txao.calendar

/** Pure helper so the "which reminder fires next" logic can be unit tested without Android. */
object ReminderPlanner {
    data class Trigger(val at: Long, val occurrenceStart: Long, val eventId: String)

    /** Earliest reminder time strictly after [now] for [event], or null. */
    fun next(event: Event, now: Long): Trigger? {
        if (event.reminders.isEmpty()) return null
        var best: Trigger? = null
        // An occurrence that started before `now` may still have a reminder ahead only if reminders are negative,
        // so look back a day to cover "9:00 on the day" style reminders on all-day events.
        var from = now - 24L * 3600 * 1000
        repeat(8) {
            val occ = Recurrence.nextStart(event, from) ?: return best
            for (m in event.reminders) {
                val t = occ.start - m * 60_000L
                if (t > now && (best == null || t < best!!.at)) best = Trigger(t, occ.start, event.id)
            }
            if (best != null) return best
            from = occ.start + 1
        }
        return best
    }
}
