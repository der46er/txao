package com.txao.gallery

import android.net.Uri
import org.mockito.Mockito.mock
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTest {
    private val uri: Uri = mock(Uri::class.java)
    private fun item(id: Long, name: String, date: Long, size: Long = 1, album: Long = 1, albumName: String = "Camera") =
        MediaItem(id, uri, name, "image/jpeg", false, date, size, 1, 1, 0, album, albumName)

    @Test fun sortsByDateNewestFirst() {
        val out = sortItems(listOf(item(1, "a", 10), item(2, "b", 30), item(3, "c", 20)), SortSpec())
        assertEquals(listOf(2L, 3L, 1L), out.map { it.id })
    }

    @Test fun sortsByNameAscendingIgnoringCase() {
        val out = sortItems(listOf(item(1, "b", 1), item(2, "A", 1), item(3, "c", 1)), SortSpec(SortField.Name, true))
        assertEquals(listOf(2L, 1L, 3L), out.map { it.id })
    }

    @Test fun hiddenAlbumsAreExcluded() {
        val items = listOf(item(1, "a", 1, album = 1), item(2, "b", 2, album = 2, albumName = "Screens"))
        val albums = groupAlbums(items, setOf(2L), SortSpec())
        assertEquals(listOf("Camera"), albums.map { it.name })
    }

    @Test fun searchMatchesNameAndAlbum() {
        val items = listOf(item(1, "beach.jpg", 1), item(2, "x.jpg", 2, album = 2, albumName = "Beach trip"))
        assertEquals(setOf(1L, 2L), searchItems(items, "BEACH").map { it.id }.toSet())
        assertEquals(emptyList<MediaItem>(), searchItems(items, " "))
    }

    @Test fun formatsDurationAndSize() {
        assertEquals("1:05", formatDuration(65_000))
        assertEquals("1:01:01", formatDuration(3_661_000))
        assertEquals("1.5 KB", formatSize(1536))
        assertEquals("12 B", formatSize(12))
    }

    @Test fun sampleSizeKeepsResolution() {
        assertEquals(1, sampleSize(1000, 800, 2048))
        assertEquals(4, sampleSize(8000, 6000, 2000))
    }
}
