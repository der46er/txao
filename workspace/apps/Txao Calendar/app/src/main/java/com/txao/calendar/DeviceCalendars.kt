package com.txao.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Bridge to the phone's calendar provider, so accounts synced by the system (Google, Exchange, DAVx5 ...) show up next to
 * the calendars kept inside this app. Everything is wrapped in runCatching: a revoked permission or a broken provider
 * just means "no device events".
 */
object DeviceCalendars {
    val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    fun granted(ctx: Context): Boolean = PERMISSIONS.all { ctx.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    fun calendars(ctx: Context): List<Cal> {
        if (!granted(ctx)) return emptyList()
        val out = ArrayList<Cal>()
        runCatching {
            val proj = arrayOf(
                CalendarContract.Calendars._ID, CalendarContract.Calendars.ACCOUNT_NAME, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.CALENDAR_COLOR, CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            )
            ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)?.use { c ->
                while (c.moveToNext()) {
                    out += Cal(
                        id = DEVICE_PREFIX + c.getLong(0), name = c.getString(2).orEmpty(), color = c.getInt(3),
                        account = c.getString(1).orEmpty(), writable = c.getInt(4) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                    )
                }
            }
        }
        return out
    }

    /** Shown occurrences from the given device calendars that overlap [from, to). */
    fun instances(ctx: Context, from: Long, to: Long, calendarIds: Set<String>, zone: ZoneId = ZoneId.systemDefault()): List<Occurrence> {
        if (calendarIds.isEmpty() || !granted(ctx)) return emptyList()
        val out = ArrayList<Occurrence>()
        runCatching {
            val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
            ContentUris.appendId(builder, from - 86_400_000L)
            ContentUris.appendId(builder, to + 86_400_000L)
            val proj = arrayOf(
                CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.CALENDAR_ID, CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.DESCRIPTION, CalendarContract.Instances.EVENT_COLOR,
                CalendarContract.Instances.RRULE, CalendarContract.Instances.DTSTART,
            )
            ctx.contentResolver.query(builder.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
                while (c.moveToNext()) {
                    val calId = DEVICE_PREFIX + c.getLong(1)
                    if (calId !in calendarIds) continue
                    val allDay = c.getInt(5) == 1
                    var start = c.getLong(3)
                    var end = c.getLong(4)
                    var series = c.getLong(10)
                    if (allDay) {
                        // All-day times come as UTC midnights; show them as local midnights.
                        val first = utcDate(start)
                        val last = if (end > start) utcDate(end - 1) else first
                        start = first.atStartOfDay(zone).toInstant().toEpochMilli()
                        end = last.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
                        series = utcDate(series).atStartOfDay(zone).toInstant().toEpochMilli()
                    }
                    if (end <= from || start >= to) continue
                    val color = if (c.isNull(8)) 0 else c.getInt(8)
                    val e = Event(
                        id = DEVICE_PREFIX + c.getLong(0), title = c.getString(2).orEmpty(), notes = c.getString(7).orEmpty(),
                        location = c.getString(6).orEmpty(), start = start, end = end, allDay = allDay, color = color,
                        calendarId = calId, rrule = c.getString(9).orEmpty(), seriesStart = series,
                    )
                    out += Occurrence(e, start, end)
                }
            }
        }
        return out
    }

    /** Reminder lead times (minutes) of a device event; unsupported methods are kept as plain alerts. */
    fun reminders(ctx: Context, eventId: String): List<Int> {
        if (!granted(ctx)) return emptyList()
        val out = ArrayList<Int>()
        runCatching {
            ctx.contentResolver.query(
                CalendarContract.Reminders.CONTENT_URI, arrayOf(CalendarContract.Reminders.MINUTES),
                CalendarContract.Reminders.EVENT_ID + "=?", arrayOf(rawId(eventId).toString()), null,
            )?.use { c -> while (c.moveToNext()) { val m = c.getInt(0); out += m } }
        }
        return out.distinct()
    }

    /** Creates [e] in its device calendar; returns the new event id (as "dev:<id>") or null. */
    fun insert(ctx: Context, e: Event, zone: ZoneId = ZoneId.systemDefault()): String? = runCatching {
        val values = values(e, null, zone)
        values.put(CalendarContract.Events.CALENDAR_ID, rawId(e.calendarId))
        val uri = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return@runCatching null
        val id = ContentUris.parseId(uri)
        writeReminders(ctx, id, e)
        DEVICE_PREFIX + id
    }.getOrNull()

    /** Saves changes to an existing device event. [original] is the occurrence as it was shown before editing. */
    fun update(ctx: Context, e: Event, original: Event, zone: ZoneId = ZoneId.systemDefault()): Boolean = runCatching {
        val id = rawId(e.id)
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        ctx.contentResolver.update(uri, values(e, original, zone), null, null)
        writeReminders(ctx, id, e)
        true
    }.getOrDefault(false)

    fun delete(ctx: Context, eventId: String): Boolean = runCatching {
        ctx.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, rawId(eventId)), null, null) > 0
    }.getOrDefault(false)

    fun rawId(id: String): Long = id.removePrefix(DEVICE_PREFIX).toLong()

    private fun values(e: Event, original: Event?, zone: ZoneId): ContentValues {
        val v = ContentValues()
        v.put(CalendarContract.Events.TITLE, e.title)
        v.put(CalendarContract.Events.DESCRIPTION, e.notes)
        v.put(CalendarContract.Events.EVENT_LOCATION, e.location)
        v.put(CalendarContract.Events.ALL_DAY, if (e.allDay) 1 else 0)
        v.put(CalendarContract.Events.EVENT_TIMEZONE, if (e.allDay) "UTC" else zone.id)
        if (e.color != 0) v.put(CalendarContract.Events.EVENT_COLOR, e.color) else v.putNull(CalendarContract.Events.EVENT_COLOR)

        val repeating = if (original != null) original.rrule.isNotEmpty() else e.repeat.freq != Freq.NONE
        if (original == null) {
            ICal.rrule(e.repeat, e.allDay, zone)?.let { v.put(CalendarContract.Events.RRULE, it) }
        }
        // The edited occurrence moves the whole series by the same amount.
        val start: Long
        val end: Long
        if (original != null && original.rrule.isNotEmpty() && original.seriesStart != 0L) {
            if (e.allDay) {
                val days = java.time.temporal.ChronoUnit.DAYS.between(
                    Instant.ofEpochMilli(original.start).atZone(zone).toLocalDate(), Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate(),
                )
                start = Instant.ofEpochMilli(original.seriesStart).atZone(zone).toLocalDate().plusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                end = start + (allDayDays(e, zone)) * 86_400_000L
            } else {
                start = original.seriesStart + (e.start - original.start)
                end = start + (e.end - e.start)
            }
        } else if (e.allDay) {
            start = Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            end = start + allDayDays(e, zone) * 86_400_000L
        } else {
            start = e.start
            end = e.end
        }
        v.put(CalendarContract.Events.DTSTART, start)
        if (repeating) {
            v.putNull(CalendarContract.Events.DTEND)
            v.put(CalendarContract.Events.DURATION, if (e.allDay) "P${allDayDays(e, zone)}D" else "P${((end - start) / 1000).coerceAtLeast(1)}S")
        } else {
            v.put(CalendarContract.Events.DTEND, end)
        }
        return v
    }

    private fun allDayDays(e: Event, zone: ZoneId): Long {
        val s: LocalDate = Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate()
        val t: LocalDate = Instant.ofEpochMilli(e.end).atZone(zone).toLocalDate()
        return java.time.temporal.ChronoUnit.DAYS.between(s, t).coerceAtLeast(1)
    }

    private fun writeReminders(ctx: Context, id: Long, e: Event) {
        val cr = ctx.contentResolver
        cr.delete(CalendarContract.Reminders.CONTENT_URI, CalendarContract.Reminders.EVENT_ID + "=?", arrayOf(id.toString()))
        for (m in e.reminders.distinct()) {
            val rv = ContentValues()
            rv.put(CalendarContract.Reminders.EVENT_ID, id)
            rv.put(CalendarContract.Reminders.MINUTES, m)
            rv.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            runCatching { cr.insert(CalendarContract.Reminders.CONTENT_URI, rv) }
        }
    }

    private fun utcDate(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
}
