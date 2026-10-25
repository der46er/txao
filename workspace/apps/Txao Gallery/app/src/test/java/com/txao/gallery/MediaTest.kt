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

    private fun ms(y: Int, m: Int, d: Int): Long =
        java.util.Calendar.getInstance().apply { clear(); set(y, m, d, 12, 0) }.timeInMillis

    @Test fun onThisDayGroupsByYearsAgo() {
        val items = listOf(
            item(1, "a", ms(2024, 9, 23)), item(2, "b", ms(2025, 9, 23)),
            item(3, "c", ms(2025, 9, 24)), item(4, "d", ms(2026, 9, 23)),
        )
        val memories = onThisDay(items, ms(2026, 9, 23))
        assertEquals(listOf(1, 2), memories.map { it.yearsAgo })
        assertEquals(listOf(2L), memories[0].items.map { it.id })
        assertEquals("1 year ago", memories[0].title)
        assertEquals("2 years ago", memories[1].title)
    }

    @Test fun timelineHasHeaderPerDayAndIndexesCells() {
        val items = listOf(item(1, "a", ms(2026, 9, 23)), item(2, "b", ms(2026, 9, 23)), item(3, "c", ms(2026, 8, 1)))
        val entries = buildEntries(items, grouped = true)
        assertEquals(5, entries.size)
        assertEquals(listOf(0, 1, 2), entries.filterIsInstance<Entry.Cell>().map { it.index })
        assertEquals(2, entries.count { it is Entry.Header })
        assertEquals(3, buildEntries(items, grouped = false).size)
    }

    @Test fun scrubberMapsFractionToIndex() {
        assertEquals(0, scrubIndex(0f, 100))
        assertEquals(99, scrubIndex(1f, 100))
        assertEquals(49, scrubIndex(0.5f, 100))
        assertEquals(0, scrubIndex(0.7f, 0))
    }

    @Test fun searchUnderstandsKeywordsAndMonths() {
        val shot = item(1, "Screenshot_1.png", ms(2026, 9, 5), albumName = "Screenshots")
        val vid = item(2, "clip.mp4", ms(2026, 9, 6)).copy(isVideo = true, mime = "video/mp4")
        val photo = item(3, "img.jpg", ms(2025, 0, 6))
        val all = listOf(shot, vid, photo)
        assertEquals(listOf(1L), searchItems(all, "screenshots").map { it.id })
        assertEquals(listOf(2L), searchItems(all, "videos").map { it.id })
        assertEquals(listOf(3L), searchItems(all, "2025").map { it.id })
        assertEquals(listOf(3L), searchItems(all, "favorites", setOf(3L)).map { it.id })
    }

    @Test fun centerCropKeepsRatio() {
        assertEquals(listOf(0, 0, 400, 300), centerCrop(400, 300, null).toList())
        assertEquals(listOf(50, 0, 300, 300), centerCrop(400, 300, 1f).toList())
        assertEquals(listOf(0, 75, 400, 150), centerCrop(400, 300, 400f / 150f).toList())
    }

    @Test fun monthGroupingMergesDaysAndHeadersCarryIds() {
        val items = listOf(item(1, "a", ms(2026, 9, 23)), item(2, "b", ms(2026, 9, 2)), item(3, "c", ms(2026, 8, 1)))
        val byDay = buildEntries(items, grouped = true, grouping = Grouping.Day).filterIsInstance<Entry.Header>()
        val byMonth = buildEntries(items, grouped = true, grouping = Grouping.Month).filterIsInstance<Entry.Header>()
        assertEquals(3, byDay.size)
        assertEquals(2, byMonth.size)
        assertEquals(listOf(1L, 2L), byMonth[0].ids)
    }

    @Test fun trashDaysLeftRoundsUp() {
        val day = 86_400_000L
        assertEquals(null, trashDaysLeft(item(1, "a", 1)))
        assertEquals(30, trashDaysLeft(item(1, "a", 1).copy(expiresMs = 30 * day), 0))
        assertEquals(1, trashDaysLeft(item(1, "a", 1).copy(expiresMs = 1000), 0))
        assertEquals(0, trashDaysLeft(item(1, "a", 1).copy(expiresMs = 5), 10))
    }

    @Test fun customAlbumsRoundTrip() {
        val albums = mapOf("Trip" to setOf(1L, 2L), "Empty" to emptySet())
        assertEquals(albums, decodeAlbums(encodeAlbums(albums)))
        assertEquals(emptyMap<String, Set<Long>>(), decodeAlbums("not json"))
        assertEquals(emptyMap<String, Set<Long>>(), decodeAlbums(null))
    }
}
