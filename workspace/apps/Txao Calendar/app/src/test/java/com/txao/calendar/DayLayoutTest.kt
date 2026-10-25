package com.txao.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class DayLayoutTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private val day = LocalDate.of(2026, 3, 10)
    private fun ms(h: Int, m: Int = 0, d: LocalDate = day) = LocalDateTime.of(d, java.time.LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
    private fun occ(id: String, s: Long, e: Long, allDay: Boolean = false) = Occurrence(Event(id, start = s, end = e, allDay = allDay), s, e)

    @Test fun separateEventsGetFullWidth() {
        val p = DayLayout.place(day, listOf(occ("a", ms(9), ms(10)), occ("b", ms(11), ms(12))), zone)
        assertEquals(listOf(1, 1), p.map { it.columns })
        assertEquals(listOf(0, 0), p.map { it.column })
    }

    @Test fun overlappingEventsSitSideBySide() {
        val p = DayLayout.place(day, listOf(occ("a", ms(9), ms(11)), occ("b", ms(10), ms(12)), occ("c", ms(13), ms(14))), zone).associateBy { it.occ.event.id }
        assertEquals(2, p.getValue("a").columns)
        assertEquals(2, p.getValue("b").columns)
        assertEquals(setOf(0, 1), setOf(p.getValue("a").column, p.getValue("b").column))
        assertEquals(1, p.getValue("c").columns)
    }

    @Test fun reusesFreedColumn() {
        val p = DayLayout.place(day, listOf(occ("a", ms(9), ms(12)), occ("b", ms(9, 30), ms(10)), occ("c", ms(10, 30), ms(11))), zone).associateBy { it.occ.event.id }
        assertEquals(2, p.getValue("a").columns)
        assertEquals(p.getValue("b").column, p.getValue("c").column)
    }

    @Test fun shortEventsGetMinimumHeightAndAllDayIsSkipped() {
        val p = DayLayout.place(day, listOf(occ("a", ms(9), ms(9)), occ("d", ms(0), ms(0, 0, day.plusDays(1)), allDay = true)), zone)
        assertEquals(1, p.size)
        assertTrue(p[0].bottomMin - p[0].topMin >= DayLayout.MIN_MINUTES)
    }

    @Test fun eventContinuingFromYesterdayStartsAtMidnight() {
        val p = DayLayout.place(day, listOf(occ("a", ms(22, 0, day.minusDays(1)), ms(2))), zone).single()
        assertEquals(0, p.topMin)
        assertEquals(120, p.bottomMin)
    }
}
