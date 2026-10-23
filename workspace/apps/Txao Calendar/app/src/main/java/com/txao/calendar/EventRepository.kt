package com.txao.calendar

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import java.io.File

/** Events live in one JSON file in app-private storage. Writes go through a temp file so a crash can't corrupt them. */
class EventRepository(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "events.json"))

    @Synchronized
    fun load(): List<Event> = runCatching {
        if (file.exists()) EventJson.listFromJson(file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    @Synchronized
    fun save(events: List<Event>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(EventJson.listToJson(events))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

class CalendarSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 1 = Monday ... 7 = Sunday (java.time.DayOfWeek values). */
    private val weekStartState = mutableIntStateOf(prefs.getInt("weekStart", 1))
    var weekStart: Int
        get() = weekStartState.intValue
        set(v) { weekStartState.intValue = v; prefs.edit().putInt("weekStart", v).apply() }

    var showWeekNumbers: Boolean
        get() = prefs.getBoolean("weekNumbers", false)
        set(v) = prefs.edit().putBoolean("weekNumbers", v).apply()

    /** Reminder minutes pre-selected on a new event; -1 means none. */
    var defaultReminder: Int
        get() = prefs.getInt("defaultReminder", 10)
        set(v) = prefs.edit().putInt("defaultReminder", v).apply()
}
