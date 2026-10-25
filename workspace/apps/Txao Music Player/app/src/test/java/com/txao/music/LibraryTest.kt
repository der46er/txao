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

    @Test fun albumTracksFollowTrackNumber() {
        val list = listOf(
            Track(1, "B", "X", "A", 1, 1000, "u1", trackNo = 2),
            Track(2, "A", "X", "A", 1, 1000, "u2", trackNo = 1),
        )
        assertEquals(listOf("A", "B"), Library.albums(list)[0].tracks.map { it.title })
    }

    @Test fun groupsAlbumsAndArtistsSorted() {
        val albums = Library.albums(tracks)
        assertEquals(listOf("First", "Second"), albums.map { it.name })
        assertEquals("Various artists", albums[0].artist)
        assertEquals("Zed", albums[1].artist)
        assertEquals(listOf("Amy", "Zed"), Library.artists(tracks).map { it.name })
    }

    @Test fun pluralisesCounts() {
        assertEquals("1 song", Library.plural(1, "song"))
        assertEquals("2 songs", Library.plural(2, "song"))
        assertEquals("0 albums", Library.plural(0, "album"))
    }

    @Test fun sortsAlbumsByChosenMode() {
        val albums = Library.albums(listOf(
            Track(1, "a", "Zed", "Beta", 2, 1000, "u1", year = 2001),
            Track(2, "b", "Amy", "Alpha", 1, 1000, "u2", year = 1999),
            Track(3, "c", "Mid", "Gamma", 3, 1000, "u3", year = 2010),
        ))
        assertEquals(listOf("Alpha", "Beta", "Gamma"), Library.sortAlbums(albums, Library.SORT_NAME, emptyList()).map { it.name })
        assertEquals(listOf("Alpha", "Gamma", "Beta"), Library.sortAlbums(albums, Library.SORT_ARTIST, emptyList()).map { it.name })
        assertEquals(listOf("Gamma", "Beta", "Alpha"), Library.sortAlbums(albums, Library.SORT_YEAR, emptyList()).map { it.name })
        // custom order wins; albums not in it go last by name
        val custom = listOf(albums.first { it.name == "Gamma" }.key, albums.first { it.name == "Beta" }.key)
        assertEquals(listOf("Gamma", "Beta", "Alpha"), Library.sortAlbums(albums, Library.SORT_CUSTOM, custom).map { it.name })
    }
}
