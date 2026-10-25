package com.txao.music

import org.junit.Assert.assertEquals
import org.junit.Test

class OverridesTest {
    private val t = Track(7, "file_123", "Unknown artist", "Unknown album", 1, 1000, "content://x/7")

    @Test fun appliesSavedFields() {
        val m = Overrides.merge(t, """{"title":"Real name","artist":"Band","year":2001,"track":4}""")
        assertEquals("Real name", m.title)
        assertEquals("Band", m.artist)
        assertEquals("Unknown album", m.album)
        assertEquals(2001, m.year)
        assertEquals(4, m.trackNo)
    }

    @Test fun blankArtistFallsBackToUnknown() {
        assertEquals("Unknown artist", Overrides.merge(t.copy(artist = "Old"), """{"artist":""}""").artist)
    }

    @Test fun noOverrideLeavesTrackAlone() {
        assertEquals(t, Overrides.merge(t, null))
        assertEquals(t, Overrides.merge(t, "not json"))
    }
}
