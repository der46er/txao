package com.txao.gallery

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val dateMs: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val albumId: Long,
    val albumName: String,
    /** Trash only: when the system deletes the item for good (0 = unknown). */
    val expiresMs: Long = 0L,
)

data class Album(val id: Long, val name: String, val items: List<MediaItem>) {
    val cover: MediaItem get() = items.first()
}

/** How the timeline groups items under headers. */
enum class Grouping(val label: String) { Day("Days"), Month("Months") }

enum class SortField { Date, Name, Size }

data class SortSpec(val field: SortField = SortField.Date, val ascending: Boolean = false)

fun sortItems(items: List<MediaItem>, spec: SortSpec): List<MediaItem> {
    val base = when (spec.field) {
        SortField.Date -> compareBy<MediaItem> { it.dateMs }
        SortField.Name -> compareBy<MediaItem, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortField.Size -> compareBy<MediaItem> { it.size }
    }
    return items.sortedWith(if (spec.ascending) base else base.reversed())
}

fun groupAlbums(items: List<MediaItem>, hidden: Set<Long>, spec: SortSpec): List<Album> =
    items.filter { it.albumId !in hidden }
        .groupBy { it.albumId }
        .map { (id, list) ->
            val sorted = sortItems(list, SortSpec(SortField.Date, false))
            Album(id, sorted.first().albumName, sorted)
        }
        .sortedWith(
            if (spec.field == SortField.Name) compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            else compareByDescending { it.items.first().dateMs }
        )

private fun monthFormat(locale: Locale) = SimpleDateFormat("MMMM yyyy", locale)

fun monthLabel(ms: Long, locale: Locale = Locale.getDefault()): String = monthFormat(locale).format(Date(ms))

fun isScreenshot(item: MediaItem): Boolean =
    item.albumName.contains("screenshot", ignoreCase = true) || item.name.startsWith("Screenshot", ignoreCase = true)

/**
 * Every word of [query] must match the file name, album, month ("october 2026"), or a keyword:
 * "video(s)", "photo(s)", "screenshot(s)", "favorite(s)".
 */
fun searchItems(items: List<MediaItem>, query: String, favorites: Set<Long> = emptySet()): List<MediaItem> {
    val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return emptyList()
    return items.filter { item ->
        val month = monthLabel(item.dateMs).lowercase()
        val keywords = buildList {
            add(if (item.isVideo) "video" else "photo")
            if (isScreenshot(item)) add("screenshot")
            if (item.id in favorites) add("favorite")
        }
        words.all { w ->
            item.name.contains(w, ignoreCase = true) || item.albumName.contains(w, ignoreCase = true) ||
                month.contains(w) || (w.length > 2 && keywords.any { it.startsWith(w.removeSuffix("s")) })
        }
    }
}

/** A "memory": photos and videos taken on today's date in an earlier year. */
data class Memory(val yearsAgo: Int, val items: List<MediaItem>) {
    val title: String get() = if (yearsAgo == 1) "1 year ago" else "$yearsAgo years ago"
}

fun onThisDay(items: List<MediaItem>, nowMs: Long = System.currentTimeMillis()): List<Memory> {
    val now = java.util.Calendar.getInstance().apply { timeInMillis = nowMs }
    val cal = java.util.Calendar.getInstance()
    return items.groupBy { item ->
        cal.timeInMillis = item.dateMs
        if (cal.get(java.util.Calendar.MONTH) == now.get(java.util.Calendar.MONTH) &&
            cal.get(java.util.Calendar.DAY_OF_MONTH) == now.get(java.util.Calendar.DAY_OF_MONTH)
        ) now.get(java.util.Calendar.YEAR) - cal.get(java.util.Calendar.YEAR) else 0
    }.filterKeys { it > 0 }
        .map { (years, list) -> Memory(years, sortItems(list, SortSpec())) }
        .sortedBy { it.yearsAgo }
}

/** Rows of the timeline: day headers interleaved with media cells. */
sealed interface Entry {
    val month: String
    data class Header(val label: String, override val month: String, val ids: List<Long> = emptyList()) : Entry
    data class Cell(val item: MediaItem, val index: Int, override val month: String) : Entry
}

fun buildEntries(items: List<MediaItem>, grouped: Boolean, grouping: Grouping = Grouping.Day): List<Entry> {
    val out = ArrayList<Entry>(items.size + 32)
    if (!grouped) {
        items.forEachIndexed { i, it -> out.add(Entry.Cell(it, i, monthLabel(it.dateMs))) }
        return out
    }
    var next = 0
    for ((label, list) in groupItems(items, grouping)) {
        val month = monthLabel(list.first().dateMs)
        out.add(Entry.Header(label, month, list.map { it.id }))
        for (it in list) out.add(Entry.Cell(it, next++, month))
    }
    return out
}

/** Maps a scrubber position (0..1) to an entry index. */
fun scrubIndex(fraction: Float, size: Int): Int =
    if (size <= 0) 0 else (fraction.coerceIn(0f, 1f) * (size - 1)).toInt()

/** Groups consecutive items sharing the same calendar day or month, keeping the given order. */
fun groupItems(items: List<MediaItem>, grouping: Grouping, locale: Locale = Locale.getDefault()): List<Pair<String, List<MediaItem>>> {
    val fmt = SimpleDateFormat(if (grouping == Grouping.Day) "EEEE, d MMMM yyyy" else "MMMM yyyy", locale)
    val out = ArrayList<Pair<String, MutableList<MediaItem>>>()
    for (item in items) {
        val label = fmt.format(Date(item.dateMs))
        if (out.isEmpty() || out.last().first != label) out.add(label to mutableListOf(item))
        else out.last().second.add(item)
    }
    return out
}

fun groupByDay(items: List<MediaItem>, locale: Locale = Locale.getDefault()): List<Pair<String, List<MediaItem>>> =
    groupItems(items, Grouping.Day, locale)

/** Whole days until the system empties the trash for [item]; null when unknown. */
fun trashDaysLeft(item: MediaItem, nowMs: Long = System.currentTimeMillis()): Int? =
    if (item.expiresMs <= 0) null else ((item.expiresMs - nowMs + 86_399_999L) / 86_400_000L).toInt().coerceAtLeast(0)

/** Album names mapped to the ids of their items, as kept in [Prefs]. */
fun encodeAlbums(albums: Map<String, Set<Long>>): String {
    val root = org.json.JSONObject()
    for ((name, ids) in albums) root.put(name, org.json.JSONArray(ids.toList()))
    return root.toString()
}

fun decodeAlbums(json: String?): Map<String, Set<Long>> {
    if (json.isNullOrBlank()) return emptyMap()
    return runCatching {
        val root = org.json.JSONObject(json)
        val out = LinkedHashMap<String, Set<Long>>()
        for (name in root.keys()) {
            val arr = root.getJSONArray(name)
            out[name] = (0 until arr.length()).map { arr.getLong(it) }.toSet()
        }
        out
    }.getOrDefault(emptyMap())
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var v = bytes.toDouble()
    var i = -1
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return String.format(Locale.US, "%.1f %s", v, units[i])
}

/** Largest centered rectangle of aspect [ratio] (width / height) inside w x h, as left, top, width, height. */
fun centerCrop(w: Int, h: Int, ratio: Float?): IntArray {
    if (ratio == null || ratio <= 0f || w <= 0 || h <= 0) return intArrayOf(0, 0, w, h)
    val current = w.toFloat() / h
    return if (current > ratio) {
        val nw = (h * ratio).toInt().coerceIn(1, w)
        intArrayOf((w - nw) / 2, 0, nw, h)
    } else {
        val nh = (w / ratio).toInt().coerceIn(1, h)
        intArrayOf(0, (h - nh) / 2, w, nh)
    }
}
