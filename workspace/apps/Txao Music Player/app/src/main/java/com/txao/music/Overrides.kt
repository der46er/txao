package com.txao.music

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Edits for files whose tags can't be rewritten in place (anything but MP3). They are kept in this app and applied on top of
 * what MediaStore reports, so every format can be edited; the audio file itself is not touched.
 */
object Overrides {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("overrides", Context.MODE_PRIVATE)

    fun coverFile(ctx: Context, id: Long) = File(File(ctx.filesDir, "covers"), "$id.jpg")

    fun save(ctx: Context, id: Long, edit: TagEdit) {
        val o = prefs(ctx).getString(id.toString(), null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        edit.title?.takeIf { it.isNotBlank() }?.let { o.put("title", it.trim()) }
        edit.artist?.let { o.put("artist", it.trim()) }
        edit.album?.let { o.put("album", it.trim()) }
        edit.year?.let { o.put("year", it) }
        edit.trackNo?.let { o.put("track", it) }
        prefs(ctx).edit().putString(id.toString(), o.toString()).apply()
        edit.cover?.let {
            val f = coverFile(ctx, id)
            f.parentFile?.mkdirs()
            f.writeBytes(it)
        }
    }

    /** [json] is the saved override for [t], if any. */
    fun merge(t: Track, json: String?): Track {
        val o = json?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return t
        return t.copy(
            title = if (o.has("title")) o.getString("title").ifBlank { t.title } else t.title,
            artist = if (o.has("artist")) o.getString("artist").ifBlank { "Unknown artist" } else t.artist,
            album = if (o.has("album")) o.getString("album").ifBlank { "Unknown album" } else t.album,
            year = if (o.has("year")) o.getInt("year") else t.year,
            trackNo = if (o.has("track")) o.getInt("track") else t.trackNo,
        )
    }

    fun apply(ctx: Context, tracks: List<Track>): List<Track> {
        val p = prefs(ctx)
        if (p.all.isEmpty()) return tracks
        return tracks.map { merge(it, p.getString(it.id.toString(), null)) }
    }
}
