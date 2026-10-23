package com.txao.calendar

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Minimal iCalendar (.ics) reader and writer: events, simple repeat rules, exceptions and reminders. */
object ICal {
    private val DATE = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    fun write(events: List<Event>, zone: ZoneId = ZoneId.systemDefault()): String {
        val sb = StringBuilder()
        sb.line("BEGIN:VCALENDAR").line("VERSION:2.0").line("PRODID:-//TXAO//Txao Calendar//EN")
        for (e in events) {
            sb.line("BEGIN:VEVENT").line("UID:${e.id}@txao")
            sb.line("DTSTAMP:" + utc(System.currentTimeMillis()))
            if (e.allDay) {
                sb.line("DTSTART;VALUE=DATE:" + date(e.start, zone)).line("DTEND;VALUE=DATE:" + date(e.end, zone))
            } else {
                sb.line("DTSTART:" + utc(e.start)).line("DTEND:" + utc(e.end))
            }
            sb.line("SUMMARY:" + escape(e.title))
            if (e.notes.isNotEmpty()) sb.line("DESCRIPTION:" + escape(e.notes))
            if (e.location.isNotEmpty()) sb.line("LOCATION:" + escape(e.location))
            rrule(e.repeat, e.allDay, zone)?.let { sb.line("RRULE:$it") }
            for (x in e.exdates) sb.line(if (e.allDay) "EXDATE;VALUE=DATE:" + date(x, zone) else "EXDATE:" + utc(x))
            for (m in e.reminders) {
                sb.line("BEGIN:VALARM").line("ACTION:DISPLAY").line("DESCRIPTION:" + escape(e.title.ifEmpty { "Event" }))
                sb.line("TRIGGER:" + if (m >= 0) "-PT${m}M" else "PT${-m}M").line("END:VALARM")
            }
            sb.line("END:VEVENT")
        }
        sb.line("END:VCALENDAR")
        return sb.toString()
    }

    fun parse(text: String, zone: ZoneId = ZoneId.systemDefault()): List<Event> {
        val out = ArrayList<Event>()
        var cur: MutableList<Prop>? = null
        var alarm: MutableList<Prop>? = null
        val alarms = ArrayList<List<Prop>>()
        for (raw in unfold(text)) {
            val p = Prop.of(raw) ?: continue
            when {
                p.name == "BEGIN" && p.value == "VEVENT" -> { cur = ArrayList(); alarms.clear() }
                p.name == "BEGIN" && p.value == "VALARM" && cur != null -> alarm = ArrayList()
                p.name == "END" && p.value == "VALARM" -> { alarm?.let { alarms += it }; alarm = null }
                p.name == "END" && p.value == "VEVENT" -> {
                    cur?.let { c -> toEvent(c, alarms.toList(), zone)?.let { out += it } }
                    cur = null
                }
                alarm != null -> alarm.add(p)
                cur != null -> cur.add(p)
            }
        }
        return out
    }

    private fun toEvent(props: List<Prop>, alarms: List<List<Prop>>, zone: ZoneId): Event? {
        val ds = props.firstOrNull { it.name == "DTSTART" } ?: return null
        val allDay = ds.params["VALUE"] == "DATE" || ds.value.length == 8
        val start = parseTime(ds, zone) ?: return null
        var end = props.firstOrNull { it.name == "DTEND" }?.let { parseTime(it, zone) }
        if (end == null) {
            val dur = props.firstOrNull { it.name == "DURATION" }?.value?.let { runCatching { Duration.parse(it.removePrefix("+")) }.getOrNull() }
            end = when {
                dur != null -> start + dur.toMillis()
                allDay -> Instant.ofEpochMilli(start).atZone(zone).plusDays(1).toInstant().toEpochMilli()
                else -> start
            }
        }
        if (end < start) end = start
        val reminders = alarms.mapNotNull { a -> a.firstOrNull { it.name == "TRIGGER" }?.let { triggerMinutes(it.value) } }.distinct()
        val ex = props.filter { it.name == "EXDATE" }.flatMap { p -> p.value.split(',').mapNotNull { v -> parseTime(Prop(p.name, p.params, v.trim()), zone) } }
        val uid = props.firstOrNull { it.name == "UID" }?.value
        return Event(
            id = (uid ?: UUID.randomUUID().toString()).removeSuffix("@txao"),
            title = unescape(props.firstOrNull { it.name == "SUMMARY" }?.value.orEmpty()),
            notes = unescape(props.firstOrNull { it.name == "DESCRIPTION" }?.value.orEmpty()),
            location = unescape(props.firstOrNull { it.name == "LOCATION" }?.value.orEmpty()),
            start = start, end = end, allDay = allDay,
            repeat = props.firstOrNull { it.name == "RRULE" }?.let { parseRule(it.value, zone) } ?: Repeat(),
            reminders = reminders, exdates = ex,
        )
    }

    private fun rrule(r: Repeat, allDay: Boolean, zone: ZoneId): String? {
        if (r.freq == Freq.NONE) return null
        val parts = arrayListOf("FREQ=${r.freq.name}")
        if (r.interval > 1) parts += "INTERVAL=${r.interval}"
        if (r.freq == Freq.WEEKLY && r.weekdays != 0) {
            parts += "BYDAY=" + DAY_CODES.filterIndexed { i, _ -> r.weekdays and (1 shl i) != 0 }.joinToString(",")
        }
        r.count?.let { parts += "COUNT=$it" }
        r.until?.let { parts += "UNTIL=" + if (allDay) date(it, zone) else utc(it) }
        return parts.joinToString(";")
    }

    private fun parseRule(v: String, zone: ZoneId): Repeat? {
        val m = v.split(';').mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0].uppercase() to p[1] } }.toMap()
        val freq = when (m["FREQ"]) {
            "DAILY" -> Freq.DAILY; "WEEKLY" -> Freq.WEEKLY; "MONTHLY" -> Freq.MONTHLY; "YEARLY" -> Freq.YEARLY
            else -> return null
        }
        var mask = 0
        m["BYDAY"]?.split(',')?.forEach { d -> val i = DAY_CODES.indexOf(d.takeLast(2)); if (i >= 0) mask = mask or (1 shl i) }
        val until = m["UNTIL"]?.let { parseTime(Prop("UNTIL", emptyMap(), it), zone) }
        return Repeat(freq, m["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1, if (freq == Freq.WEEKLY) mask else 0, until, m["COUNT"]?.toIntOrNull())
    }

    private fun triggerMinutes(v: String): Int? {
        val neg = v.startsWith("-")
        val d = runCatching { Duration.parse(v.removePrefix("-").removePrefix("+")) }.getOrNull() ?: return null
        val min = d.toMinutes().toInt()
        return if (neg) min else -min
    }

    private fun parseTime(p: Prop, zone: ZoneId): Long? = runCatching {
        val v = p.value
        when {
            v.length == 8 -> LocalDate.parse(v, DATE).atStartOfDay(zone).toInstant().toEpochMilli()
            v.endsWith("Z") -> LocalDateTime.parse(v.dropLast(1), DATE_TIME).toInstant(ZoneOffset.UTC).toEpochMilli()
            else -> {
                val z = p.params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: zone
                LocalDateTime.parse(v, DATE_TIME).atZone(z).toInstant().toEpochMilli()
            }
        }
    }.getOrNull()

    private fun utc(ms: Long) = DATE_TIME.format(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC)) + "Z"
    private fun date(ms: Long, zone: ZoneId) = DATE.format(Instant.ofEpochMilli(ms).atZone(zone))

    private fun escape(s: String) = s.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,").replace("\r\n", "\\n").replace("\n", "\\n")

    private fun unescape(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                val n = s[i + 1]
                sb.append(if (n == 'n' || n == 'N') '\n' else n)
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }

    private fun unfold(text: String): List<String> {
        val lines = ArrayList<String>()
        for (l in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((l.startsWith(" ") || l.startsWith("\t")) && lines.isNotEmpty()) lines[lines.lastIndex] = lines.last() + l.substring(1)
            else if (l.isNotEmpty()) lines += l
        }
        return lines
    }

    private fun StringBuilder.line(s: String): StringBuilder {
        // fold at 75 characters as the spec asks
        var rest = s
        var first = true
        while (rest.length > (if (first) 75 else 74)) {
            val n = if (first) 75 else 74
            append(if (first) "" else " ").append(rest, 0, n).append("\r\n")
            rest = rest.substring(n); first = false
        }
        append(if (first) "" else " ").append(rest).append("\r\n")
        return this
    }

    private data class Prop(val name: String, val params: Map<String, String>, val value: String) {
        companion object {
            fun of(line: String): Prop? {
                val colon = line.indexOf(':')
                if (colon <= 0) return null
                val head = line.substring(0, colon).split(';')
                val params = head.drop(1).mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { p -> p[0].uppercase() to p[1].trim('"') } }.toMap()
                return Prop(head[0].uppercase(), params, line.substring(colon + 1))
            }
        }
    }
}
