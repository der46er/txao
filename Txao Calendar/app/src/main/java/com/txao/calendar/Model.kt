package com.txao.calendar

import org.json.JSONArray
import org.json.JSONObject

enum class Freq { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * How an event repeats. [weekdays] is a bit mask (Monday = bit 0 .. Sunday = bit 6) used by weekly rules;
 * 0 means "same weekday as the start". [until] (inclusive, epoch ms) and [count] both end the series.
 */
data class Repeat(
    val freq: Freq = Freq.NONE,
    val interval: Int = 1,
    val weekdays: Int = 0,
    val until: Long? = null,
    val count: Int? = null,
)

/**
 * A calendar event. Timed events store epoch-millisecond [start]/[end]. All-day events store local midnight of the
 * first day as [start] and local midnight of the day after the last day as [end] (end is exclusive).
 * [reminders] are minutes before [start]; a negative value fires after midnight on the day (all-day events).
 * [exdates] are start times of occurrences that were deleted from a repeating series.
 */
data class Event(
    val id: String,
    val title: String = "",
    val notes: String = "",
    val location: String = "",
    val start: Long,
    val end: Long,
    val allDay: Boolean = false,
    val color: Int = 0,
    val repeat: Repeat = Repeat(),
    val reminders: List<Int> = emptyList(),
    val exdates: List<Long> = emptyList(),
)

/** One concrete showing of an [Event] (a repeating event has many). */
data class Occurrence(val event: Event, val start: Long, val end: Long)

object EventJson {
    fun toJson(e: Event): JSONObject = JSONObject().apply {
        put("id", e.id); put("title", e.title); put("notes", e.notes); put("location", e.location)
        put("start", e.start); put("end", e.end); put("allDay", e.allDay); put("color", e.color)
        put("freq", e.repeat.freq.name); put("interval", e.repeat.interval); put("weekdays", e.repeat.weekdays)
        e.repeat.until?.let { put("until", it) }
        e.repeat.count?.let { put("count", it) }
        put("reminders", JSONArray(e.reminders))
        put("exdates", JSONArray(e.exdates))
    }

    fun fromJson(o: JSONObject): Event = Event(
        id = o.getString("id"),
        title = o.optString("title"),
        notes = o.optString("notes"),
        location = o.optString("location"),
        start = o.getLong("start"),
        end = o.getLong("end"),
        allDay = o.optBoolean("allDay"),
        color = o.optInt("color"),
        repeat = Repeat(
            freq = runCatching { Freq.valueOf(o.optString("freq", "NONE")) }.getOrDefault(Freq.NONE),
            interval = o.optInt("interval", 1).coerceAtLeast(1),
            weekdays = o.optInt("weekdays"),
            until = if (o.has("until")) o.getLong("until") else null,
            count = if (o.has("count")) o.getInt("count") else null,
        ),
        reminders = o.optJSONArray("reminders").toInts(),
        exdates = o.optJSONArray("exdates").toLongs(),
    )

    fun listToJson(events: List<Event>): String = JSONArray().also { a -> events.forEach { a.put(toJson(it)) } }.toString()

    fun listFromJson(text: String): List<Event> {
        val a = JSONArray(text)
        return (0 until a.length()).mapNotNull { runCatching { fromJson(a.getJSONObject(it)) }.getOrNull() }
    }

    private fun JSONArray?.toInts(): List<Int> = if (this == null) emptyList() else (0 until length()).map { getInt(it) }
    private fun JSONArray?.toLongs(): List<Long> = if (this == null) emptyList() else (0 until length()).map { getLong(it) }
}
