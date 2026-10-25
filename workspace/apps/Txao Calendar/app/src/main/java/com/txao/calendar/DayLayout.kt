package com.txao.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A timed occurrence positioned inside one day column: minutes from midnight plus its slot among overlapping events. */
data class Placed(val occ: Occurrence, val column: Int, val columns: Int, val topMin: Int, val bottomMin: Int)

/** Side-by-side layout for overlapping events in a day/week column, like Google Calendar. */
object DayLayout {
    const val MIN_MINUTES = 24

    private class Tmp(val occ: Occurrence, val top: Int, val bottom: Int) { var col = 0 }

    fun place(day: LocalDate, items: List<Occurrence>, zone: ZoneId = ZoneId.systemDefault()): List<Placed> {
        val dayStart = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val raw = items.filter { !it.banner }.map { o ->
            val top = (if (o.start <= dayStart) 0 else minutesOfDay(o.start, zone)).coerceAtMost(1440 - MIN_MINUTES)
            var bottom = if (o.end >= dayEnd) 1440 else minutesOfDay(o.end, zone)
            if (bottom < top + MIN_MINUTES) bottom = top + MIN_MINUTES
            Tmp(o, top, bottom)
        }.sortedWith(compareBy<Tmp> { it.top }.thenByDescending { it.bottom })

        val out = ArrayList<Placed>()
        var cluster = ArrayList<Tmp>()
        var clusterEnd = -1
        val colEnds = ArrayList<Int>()
        fun flush() {
            val n = colEnds.size
            cluster.forEach { out += Placed(it.occ, it.col, n, it.top, it.bottom) }
            cluster = ArrayList()
            colEnds.clear()
            clusterEnd = -1
        }
        for (t in raw) {
            if (cluster.isNotEmpty() && t.top >= clusterEnd) flush()
            var c = colEnds.indexOfFirst { it <= t.top }
            if (c < 0) { c = colEnds.size; colEnds += t.bottom } else colEnds[c] = t.bottom
            t.col = c
            cluster += t
            clusterEnd = maxOf(clusterEnd, t.bottom)
        }
        if (cluster.isNotEmpty()) flush()
        return out
    }

    private fun minutesOfDay(ms: Long, zone: ZoneId): Int {
        val t = Instant.ofEpochMilli(ms).atZone(zone)
        return t.hour * 60 + t.minute
    }
}
