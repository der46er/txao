package com.txao.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

object Format {
    private val time get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    private val date get() = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault())

    fun time(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = time.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(ms: Long, zone: ZoneId = ZoneId.systemDefault()): String = date.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(d: LocalDate): String = date.format(d)

    fun whenText(e: Event, zone: ZoneId = ZoneId.systemDefault()): String {
        val s = Instant.ofEpochMilli(e.start).atZone(zone)
        if (e.allDay) {
            val lastDay = Instant.ofEpochMilli(e.end).atZone(zone).toLocalDate().minusDays(1)
            return if (lastDay.isAfter(s.toLocalDate())) "${date(s.toLocalDate())} – ${date(lastDay)}" else date(s.toLocalDate()) + " · All day"
        }
        val en = Instant.ofEpochMilli(e.end).atZone(zone)
        return if (en.toLocalDate() == s.toLocalDate()) "${date(s.toLocalDate())} · ${time(e.start, zone)} – ${time(e.end, zone)}"
        else "${date(s.toLocalDate())} ${time(e.start, zone)} – ${date(en.toLocalDate())} ${time(e.end, zone)}"
    }

    fun reminderLabel(minutes: Int, allDay: Boolean): String = when {
        minutes < 0 -> "On the day at %d:00".format(-minutes / 60)
        minutes == 0 -> if (allDay) "At midnight" else "At start"
        minutes < 60 -> "$minutes min before"
        minutes < 1440 -> "${minutes / 60} h before"
        else -> "${minutes / 1440} d before"
    }
}
