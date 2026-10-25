package com.txao.music

/** One audio file on the device. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: String,
    val trackNo: Int = 0,
    val year: Int = 0,
    val mime: String = "",
)

data class Album(val id: Long, val name: String, val artist: String, val tracks: List<Track>)

data class Artist(val name: String, val tracks: List<Track>)

/** Pure helpers for presenting and filtering the library; no Android dependencies. */
object Library {
    fun formatDuration(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    fun matches(track: Track, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        return q.split(Regex("\\s+")).all { word ->
            track.title.contains(word, ignoreCase = true) ||
                track.artist.contains(word, ignoreCase = true) ||
                track.album.contains(word, ignoreCase = true)
        }
    }

    fun albums(tracks: List<Track>): List<Album> =
        tracks.groupBy { it.albumId to it.album }
            .map { (key, list) ->
                Album(key.first, key.second, list.map { it.artist }.distinct().singleOrNull() ?: "Various artists", list.sortedWith(compareBy({ it.trackNo }, { it.title.lowercase() })))
            }
            .sortedBy { it.name.lowercase() }

    fun artists(tracks: List<Track>): List<Artist> =
        tracks.groupBy { it.artist }
            .map { (name, list) -> Artist(name, list) }
            .sortedBy { it.name.lowercase() }

    /** Index to jump to when "previous" is pressed: restart the track if it has played a few seconds. */
    fun restartThresholdMs(): Long = 3000
}
