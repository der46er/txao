package com.txao.calendar

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

/** The calendars created inside the app. There is always at least one ("My calendar"). */
class CalendarRepository(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "calendars.json"))

    @Synchronized
    fun load(): List<Cal> {
        val saved = runCatching { if (file.exists()) CalJson.listFromJson(file.readText()) else emptyList() }.getOrDefault(emptyList())
        return if (saved.any { it.id == LOCAL_CALENDAR }) saved else listOf(Cal(LOCAL_CALENDAR, "My calendar")) + saved
    }

    @Synchronized
    fun save(cals: List<Cal>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(CalJson.listToJson(cals))
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
    }
}

enum class ViewMode { SCHEDULE, DAY, THREE_DAYS, WEEK, MONTH }

class CalendarSettings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** 1 = Monday ... 7 = Sunday (java.time.DayOfWeek values). */
    private val weekStartState = mutableIntStateOf(prefs.getInt("weekStart", 1))
    var weekStart: Int
        get() = weekStartState.intValue
        set(v) { weekStartState.intValue = v; prefs.edit().putInt("weekStart", v).apply() }

    private val weekNumbersState = mutableStateOf(prefs.getBoolean("weekNumbers", false))
    var showWeekNumbers: Boolean
        get() = weekNumbersState.value
        set(v) { weekNumbersState.value = v; prefs.edit().putBoolean("weekNumbers", v).apply() }

    /** Reminder minutes pre-selected on a new event; -1 means none. */
    var defaultReminder: Int
        get() = prefs.getInt("defaultReminder", 10)
        set(v) = prefs.edit().putInt("defaultReminder", v).apply()

    private val viewState = mutableStateOf(
        runCatching { ViewMode.valueOf(prefs.getString("view", null) ?: "") }.getOrDefault(ViewMode.MONTH),
    )
    var view: ViewMode
        get() = viewState.value
        set(v) { viewState.value = v; prefs.edit().putString("view", v.name).apply() }

    private val deviceState = mutableStateOf(prefs.getBoolean("deviceCalendars", false))
    /** Whether calendars from the phone's calendar provider are shown (needs the calendar permission). */
    var deviceCalendars: Boolean
        get() = deviceState.value
        set(v) { deviceState.value = v; prefs.edit().putBoolean("deviceCalendars", v).apply() }

    private val hiddenState = mutableStateOf<Set<String>>(prefs.getStringSet("hiddenCalendars", emptySet()).orEmpty().toSet())
    /** Ids of calendars whose checkbox is off in the drawer. */
    var hiddenCalendars: Set<String>
        get() = hiddenState.value
        set(v) { hiddenState.value = v; prefs.edit().putStringSet("hiddenCalendars", v).apply() }

    private val durationState = mutableIntStateOf(prefs.getInt("duration", 60))
    /** Length in minutes of a new event. */
    var defaultDuration: Int
        get() = durationState.intValue
        set(v) { durationState.intValue = v; prefs.edit().putInt("duration", v).apply() }
}
