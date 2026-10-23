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
)

data class Album(val id: Long, val name: String, val items: List<MediaItem>) {
    val cover: MediaItem get() = items.first()
}

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

fun searchItems(items: List<MediaItem>, query: String): List<MediaItem> {
    val q = query.trim()
    if (q.isEmpty()) return emptyList()
    return items.filter { it.name.contains(q, ignoreCase = true) || it.albumName.contains(q, ignoreCase = true) }
}

/** Groups consecutive items sharing the same calendar day, keeping the given order. */
fun groupByDay(items: List<MediaItem>, locale: Locale = Locale.getDefault()): List<Pair<String, List<MediaItem>>> {
    val fmt = SimpleDateFormat("EEEE, d MMMM yyyy", locale)
    val out = ArrayList<Pair<String, MutableList<MediaItem>>>()
    for (item in items) {
        val label = fmt.format(Date(item.dateMs))
        if (out.isEmpty() || out.last().first != label) out.add(label to mutableListOf(item))
        else out.last().second.add(item)
    }
    return out
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
