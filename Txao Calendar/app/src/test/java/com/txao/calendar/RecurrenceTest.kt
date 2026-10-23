package com.txao.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class RecurrenceTest {
    private val zone = ZoneId.of("Europe/Vienna")
    private fun ms(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()
    private fun ev(start: Long, end: Long, r: Repeat = Repeat(), allDay: Boolean = false, ex: List<Long> = emptyList()) =
        Event(id = "e", title = "t", start = start, end = end, allDay = allDay, repeat = r, exdates = ex)
    private fun dates(l: List<Occurrence>) = l.map { java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }

    @Test fun singleEventOnlyInRange() {
        val e = ev(ms(2026, 3, 10, 9), ms(2026, 3, 10, 10))
        assertEquals(1, Recurrence.occurrences(e, ms(2026, 3, 10), ms(2026, 3, 11), zone).size)
        assertEquals(0, Recurrence.occurrences(e, ms(2026, 3, 11), ms(2026, 3, 12), zone).size)
    }

    @Test fun dailyWithCountAndException() {
        val s = ms(2026, 3, 1, 9)
        val e = ev(s, s + 3600_000, Repeat(Freq.DAILY, count = 5), ex = listOf(ms(2026, 3, 3, 9)))
        val d = dates(Recurrence.occurrences(e, ms(2026, 1, 1), ms(2026, 12, 1), zone))
        assertEquals(listOf(1, 2, 4, 5), d.map { it.dayOfMonth })
    }

    @Test fun weeklyOnSelectedDays() {
        // Monday 2 March 2026, repeating Mon + Wed + Fri every week for two weeks
        val s = ms(2026, 3, 2, 18)
        val mask = 1 or (1 shl 2) or (1 shl 4)
        val e = ev(s, s + 3600_000, Repeat(Freq.WEEKLY, weekdays = mask, until = ms(2026, 3, 15, 23)))
        val d = dates(Recurrence.occurrences(e, ms(2026, 3, 1), ms(2026, 4, 1), zone))
        assertEquals(listOf(2, 4, 6, 9, 11, 13), d.map { it.dayOfMonth })
    }

    @Test fun weeklyEveryOtherWeekKeepsWallClockAcrossDst() {
        val s = ms(2026, 3, 20, 9) // before the 29 March DST change in Europe
        val e = ev(s, s + 3600_000, Repeat(Freq.WEEKLY, interval = 2))
        val occ = Recurrence.occurrences(e, ms(2026, 3, 1), ms(2026, 4, 30), zone)
        assertEquals(listOf(20, 3, 17), dates(occ).map { it.dayOfMonth })
        occ.forEach { assertEquals(9, java.time.Instant.ofEpochMilli(it.start).atZone(zone).hour) }
    }

    @Test fun monthlyClampsToShortMonths() {
        val s = ms(2026, 1, 31, 12)
        val e = ev(s, s + 3600_000, Repeat(Freq.MONTHLY))
        val d = dates(Recurrence.occurrences(e, ms(2026, 1, 1), ms(2026, 5, 1), zone))
        assertEquals(listOf(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30)), d)
    }

    @Test fun yearlyAllDaySpansWholeDay() {
        val e = ev(ms(2020, 6, 5), ms(2020, 6, 6), Repeat(Freq.YEARLY), allDay = true)
        val occ = Recurrence.occurrences(e, ms(2026, 1, 1), ms(2027, 1, 1), zone)
        assertEquals(1, occ.size)
        assertEquals(ms(2026, 6, 6), occ[0].end)
    }

    @Test fun multiDayEventShowsOnEveryDay() {
        val e = ev(ms(2026, 5, 10, 20), ms(2026, 5, 12, 8))
        val idx = Days.index(listOf(e), LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 1), zone)
        assertEquals(setOf(10, 11, 12), idx.keys.map { it.dayOfMonth }.toSet())
    }

    @Test fun nextStartSkipsDeletedAndFindsFarYearly() {
        val e = ev(ms(2020, 2, 1, 8), ms(2020, 2, 1, 9), Repeat(Freq.YEARLY), ex = listOf(ms(2027, 2, 1, 8)))
        val n = Recurrence.nextStart(e, ms(2026, 12, 1), zone)
        assertNotNull(n)
        assertEquals(ms(2028, 2, 1, 8), n!!.start)
        assertNull(Recurrence.nextStart(ev(ms(2020, 1, 1), ms(2020, 1, 1, 1)), ms(2026, 1, 1), zone))
    }

    @Test fun reminderPlannerPicksEarliestFutureTrigger() {
        val s = System.currentTimeMillis() + 2 * 3600_000
        val e = ev(s, s + 3600_000).copy(reminders = listOf(10, 60))
        val t = ReminderPlanner.next(e, System.currentTimeMillis())!!
        assertEquals(s - 60 * 60_000L, t.at)
        assertNull(ReminderPlanner.next(e.copy(reminders = emptyList()), 0))
    }
}
