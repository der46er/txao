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
        minutes == 0 -> if (allDay) "At midnight" else "At time of event"
        minutes < 60 -> plural(minutes, "minute") + " before"
        minutes < 1440 && minutes % 60 == 0 -> plural(minutes / 60, "hour") + " before"
        minutes < 1440 -> plural(minutes, "minute") + " before"
        minutes % 10080 == 0 -> plural(minutes / 10080, "week") + " before"
        minutes % 1440 == 0 -> plural(minutes / 1440, "day") + " before"
        else -> plural(minutes, "minute") + " before"
    }

    private fun plural(n: Int, unit: String) = if (n == 1) "1 $unit" else "$n ${unit}s"

    fun timeRange(o: Occurrence, zone: ZoneId = ZoneId.systemDefault()): String {
        if (o.event.allDay) return "All day"
        val s = Instant.ofEpochMilli(o.start).atZone(zone).toLocalDate()
        val e = Instant.ofEpochMilli(o.end).atZone(zone).toLocalDate()
        return if (s == e) time(o.start, zone) + " – " + time(o.end, zone) else time(o.start, zone) + " – " + shortDate(e) + ", " + time(o.end, zone)
    }

    fun shortDate(d: LocalDate): String = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()).format(d)
    fun dayName(d: LocalDate): String = d.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    fun monthName(d: java.time.YearMonth): String = d.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())

    /** App-bar title: the month, with the year when it isn't this year. */
    fun monthTitle(d: LocalDate): String {
        val m = d.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
        return if (d.year == LocalDate.now().year) m else "$m ${d.year}"
    }

    fun repeatText(r: Repeat, start: LocalDate, zone: ZoneId = ZoneId.systemDefault()): String {
        val every = r.interval.coerceAtLeast(1)
        val base = when (r.freq) {
            Freq.NONE -> return "Does not repeat"
            Freq.DAILY -> if (every == 1) "Every day" else "Every $every days"
            Freq.WEEKLY -> {
                val mask = if (r.weekdays == 0) 1 shl (start.dayOfWeek.value - 1) else r.weekdays
                val days = (0 until 7).filter { mask and (1 shl it) != 0 }
                val names = days.joinToString(", ") { java.time.DayOfWeek.of(it + 1).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }
                when {
                    every == 1 && mask == 31 -> "Every weekday"
                    every == 1 && days.size == 1 -> "Weekly on " + java.time.DayOfWeek.of(days[0] + 1).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
                    every == 1 -> "Weekly on $names"
                    else -> "Every $every weeks on $names"
                }
            }
            Freq.MONTHLY -> (if (every == 1) "Monthly" else "Every $every months") + " on day ${start.dayOfMonth}"
            Freq.YEARLY -> (if (every == 1) "Annually" else "Every $every years") + " on " + DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()).format(start)
        }
        val end = when {
            r.until != null -> ", until " + date(r.until, zone)
            r.count != null -> ", ${r.count} times"
            else -> ""
        }
        return base + end
    }
}
