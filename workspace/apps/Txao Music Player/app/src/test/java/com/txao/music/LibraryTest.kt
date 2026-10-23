package com.txao.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryTest {
    private fun t(id: Long, title: String, artist: String, album: String, albumId: Long) =
        Track(id, title, artist, album, albumId, 1000, "content://x/$id")

    private val tracks = listOf(
        t(1, "Alpha", "Zed", "Second", 2),
        t(2, "Beta", "Zed", "First", 1),
        t(3, "Gamma", "Amy", "First", 1),
    )

    @Test fun formatsDurations() {
        assertEquals("0:05", Library.formatDuration(5_000))
        assertEquals("3:07", Library.formatDuration(187_000))
        assertEquals("1:01:01", Library.formatDuration(3_661_000))
        assertEquals("0:00", Library.formatDuration(-5))
    }

    @Test fun searchMatchesAllWordsAcrossFields() {
        assertTrue(Library.matches(tracks[0], "alp zed"))
        assertFalse(Library.matches(tracks[0], "alp amy"))
        assertTrue(Library.matches(tracks[0], "  "))
    }

    @Test fun groupsAlbumsAndArtistsSorted() {
        val albums = Library.albums(tracks)
        assertEquals(listOf("First", "Second"), albums.map { it.name })
        assertEquals("Various artists", albums[0].artist)
        assertEquals("Zed", albums[1].artist)
        assertEquals(listOf("Amy", "Zed"), Library.artists(tracks).map { it.name })
    }
}
