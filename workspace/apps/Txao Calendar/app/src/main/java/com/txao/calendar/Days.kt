package com.txao.calendar

import java.time.LocalDate
import java.time.ZoneId

object Days {
    /** Occurrences grouped by each local day they touch, for days in [from, toExclusive). */
    fun index(events: List<Event>, from: LocalDate, toExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, List<Occurrence>> {
        val f = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val t = toExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val map = HashMap<LocalDate, MutableList<Occurrence>>()
        for (e in events) for (o in Recurrence.occurrences(e, f, t, zone)) {
            val first = java.time.Instant.ofEpochMilli(o.start).atZone(zone).toLocalDate()
            val last = if (o.end > o.start) java.time.Instant.ofEpochMilli(o.end - 1).atZone(zone).toLocalDate() else first
            var d = if (first.isBefore(from)) from else first
            while (!d.isAfter(last) && d.isBefore(toExclusive)) {
                map.getOrPut(d) { ArrayList() }.add(o)
                d = d.plusDays(1)
            }
        }
        map.values.forEach { l -> l.sortWith(compareByDescending<Occurrence> { it.event.allDay }.thenBy { it.start }) }
        return map
    }
}
