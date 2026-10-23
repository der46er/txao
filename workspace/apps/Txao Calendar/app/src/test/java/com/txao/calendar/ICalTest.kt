package com.txao.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ICalTest {
    private val zone = ZoneId.of("Europe/Vienna")

    @Test fun roundTripKeepsFields() {
        val e = Event(
            id = "abc", title = "Dinner, with; friends", notes = "Line one\nLine two", location = "Café",
            start = 1_800_000_000_000, end = 1_800_003_600_000, color = 0,
            repeat = Repeat(Freq.WEEKLY, interval = 2, weekdays = 1 or (1 shl 3), count = 6),
            reminders = listOf(10, 60), exdates = listOf(1_800_000_000_000 + 14 * 86_400_000L),
        )
        val back = ICal.parse(ICal.write(listOf(e), zone), zone).single()
        assertEquals(e.id, back.id)
        assertEquals(e.title, back.title)
        assertEquals(e.notes, back.notes)
        assertEquals(e.location, back.location)
        assertEquals(e.start, back.start)
        assertEquals(e.end, back.end)
        assertEquals(e.repeat, back.repeat)
        assertEquals(e.reminders.sorted(), back.reminders.sorted())
        assertEquals(e.exdates, back.exdates)
    }

    @Test fun parsesAllDayFoldedLinesAndDuration() {
        val text = "BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nUID:x1\r\nSUMMARY:Very long\r\n  title\r\nDTSTART;VALUE=DATE:20260704\r\nEND:VEVENT\r\n" +
            "BEGIN:VEVENT\r\nUID:x2\r\nDTSTART:20260704T100000Z\r\nDURATION:PT90M\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n"
        val l = ICal.parse(text, zone)
        assertEquals(2, l.size)
        assertEquals("Very long title", l[0].title)
        assertTrue(l[0].allDay)
        assertEquals(24 * 3600_000L, l[0].end - l[0].start)
        assertEquals(90 * 60_000L, l[1].end - l[1].start)
        assertEquals(Repeat(Freq.DAILY, count = 3), l[1].repeat)
    }

    @Test fun ignoresEventsWithoutStart() {
        assertEquals(0, ICal.parse("BEGIN:VEVENT\nSUMMARY:x\nEND:VEVENT", zone).size)
    }

    @Test fun jsonRoundTrip() {
        val e = Event("i", "t", "n", "l", 1, 2, true, 3, Repeat(Freq.MONTHLY, 2, 0, 99, null), listOf(-540, 10), listOf(5))
        assertEquals(e, EventJson.listFromJson(EventJson.listToJson(listOf(e))).single())
    }
}
