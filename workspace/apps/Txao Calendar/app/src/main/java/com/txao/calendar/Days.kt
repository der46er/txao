package com.txao.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object Days {
    /** Occurrences grouped by each local day they touch, for days in [from, toExclusive). */
    fun index(events: List<Event>, from: LocalDate, toExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Occurrence>> {
        val f = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val t = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        return spread(events.flatMap { Recurrence.occurrences(it, f, t, zone) }, from, toExclusive, zone)
    }

    /** Puts every occurrence under each local day it touches (inside the range) and sorts each day: all-day first, then by start. */
    fun spread(occurrences: Iterable<Occurrence>, from: LocalDate, toExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Occurrence>> {
        val map = HashMap<LocalDate, MutableList<Occurrence>>()
        for (o in occurrences) {
            val first = Instant.ofEpochMilli(o.start).atZone(zone).toLocalDate()
            val last = if (o.end > o.start) Instant.ofEpochMilli(o.end - 1).atZone(zone).toLocalDate() else first
            var d = if (first.isBefore(from)) from else first
            while (!d.isAfter(last) && d.isBefore(toExclusive)) {
                map.getOrPut(d) { ArrayList() }.add(o)
                d = d.plusDays(1)
            }
        }
        map.values.forEach { l -> l.sortWith(compareByDescending<Occurrence> { it.banner }.thenBy { it.start }) }
        return map
    }
}
