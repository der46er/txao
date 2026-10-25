package com.txao.calendar

/** Google Calendar's event colour set (Tomato ... Graphite). Colours are stored as ARGB ints, 0 = use the calendar's colour. */
val EventPalette: List<Pair<String, Int>> = listOf(
    "Tomato" to 0xFFD50000.toInt(), "Flamingo" to 0xFFE67C73.toInt(), "Tangerine" to 0xFFF4511E.toInt(),
    "Banana" to 0xFFF6BF26.toInt(), "Sage" to 0xFF33B679.toInt(), "Basil" to 0xFF0B8043.toInt(),
    "Peacock" to 0xFF039BE5.toInt(), "Blueberry" to 0xFF3F51B5.toInt(), "Lavender" to 0xFF7986CB.toInt(),
    "Grape" to 0xFF8E24AA.toInt(), "Graphite" to 0xFF616161.toInt(),
)

/** Colour given to a new calendar. */
val DefaultCalendarColor: Int = 0xFF039BE5.toInt()

/** Colours that older builds stored as an index (1..7); mapped once when loading. */
val LegacyColors: List<Int> = listOf(
    0, 0xFF8FA3D6.toInt(), 0xFF7FD6C2.toInt(), 0xFFE6C77A.toInt(), 0xFFF0A07C.toInt(),
    0xFFD7A6C6.toInt(), 0xFF9AD27A.toInt(), 0xFFE88A9A.toInt(),
)
