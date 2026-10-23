package com.txao.calendar

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Expands repeating events into the concrete occurrences that fall in a time range. */
object Recurrence {
    private const val MAX_STEPS = 200_000

    /** Occurrences of [event] that overlap [from, to) (epoch ms), in start order. */
    fun occurrences(event: Event, from: Long, to: Long, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> {
        val out = ArrayList<Occurrence>()
        val first = Instant.ofEpochMilli(event.start).atZone(zone)
        val durationMs = (event.end - event.start).coerceAtLeast(0)
        val days = if (event.allDay) ChronoUnit.DAYS.between(first.toLocalDate(), Instant.ofEpochMilli(event.end).atZone(zone).toLocalDate()) else 0
        var emitted = 0
        for (cand in candidates(event.start, event.repeat, zone)) {
            val s = cand.toInstant().toEpochMilli()
            if (event.repeat.until != null && s > event.repeat.until) break
            if (s >= to) break
            emitted++
            if (event.repeat.count != null && emitted > event.repeat.count) break
            val e = if (event.allDay) cand.plusDays(days.coerceAtLeast(1)).toInstant().toEpochMilli() else s + durationMs
            val overlaps = if (s == e) s in from until to else e > from && s < to
            if (overlaps && s !in event.exdates) out += Occurrence(event, s, e)
        }
        return out
    }

    /** The first occurrence that starts at or after [after], or null when the series is over. */
    fun nextStart(event: Event, after: Long, zone: ZoneId = ZoneId.systemDefault()): Occurrence? {
        if (event.repeat.freq == Freq.NONE) return if (event.start >= after && event.start !in event.exdates) Occurrence(event, event.start, event.end) else null
        // Search in growing windows so far-off yearly events are found without expanding everything.
        var span = 400L * 24 * 3600 * 1000
        repeat(6) {
            val hit = occurrences(event, after, after + span, zone).firstOrNull { it.start >= after }
            if (hit != null) return hit
            span *= 4
        }
        return null
    }

    private fun candidates(start: Long, r: Repeat, zone: ZoneId): Sequence<ZonedDateTime> {
        val first = Instant.ofEpochMilli(start).atZone(zone)
        val n = r.interval.coerceAtLeast(1).toLong()
        return when (r.freq) {
            Freq.NONE -> sequenceOf(first)
            Freq.DAILY -> generateSequence(0L) { if (it < MAX_STEPS) it + 1 else null }.map { first.plusDays(it * n) }
            Freq.MONTHLY -> generateSequence(0L) { if (it < MAX_STEPS) it + 1 else null }.map { first.plusMonths(it * n) }
            Freq.YEARLY -> generateSequence(0L) { if (it < MAX_STEPS) it + 1 else null }.map { first.plusYears(it * n) }
            Freq.WEEKLY -> weekly(first, r, n)
        }
    }

    private fun weekly(first: ZonedDateTime, r: Repeat, n: Long): Sequence<ZonedDateTime> {
        val mask = if (r.weekdays == 0) 1 shl (first.dayOfWeek.value - 1) else r.weekdays
        val weekStart = first.minusDays((first.dayOfWeek.value - 1).toLong())
        return generateSequence(0L) { if (it < MAX_STEPS) it + 1 else null }.flatMap { w ->
            val base = weekStart.plusWeeks(w * n)
            DayOfWeek.values().asSequence()
                .filter { mask and (1 shl (it.value - 1)) != 0 }
                .map { base.plusDays((it.value - 1).toLong()) }
                .filter { !it.isBefore(first) }
        }
    }
}
