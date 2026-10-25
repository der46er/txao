package com.txao.music

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Writes tag edits into the audio file (MP3) and into the MediaStore row (all formats). */
object MediaEditor {
    fun isMp3(t: Track) = t.mime == "audio/mpeg"

    /** Album artist and genre aren't in [Track]; read them from the file when the editor opens. */
    fun readExtra(ctx: Context, t: Track): Pair<String, String> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, Uri.parse(t.uri))
            (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty()) to
                (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE).orEmpty())
        } catch (_: Exception) {
            "" to ""
        } finally {
            runCatching { r.release() }
        }
    }

    /** Throws if the file can't be written (SecurityException when write access hasn't been granted). */
    fun write(ctx: Context, t: Track, edit: TagEdit) {
        val uri = Uri.parse(t.uri)
        if (isMp3(t)) {
            val tmp = File.createTempFile("tag", ".mp3", ctx.cacheDir)
            try {
                ctx.contentResolver.openInputStream(uri)!!.use { i -> tmp.outputStream().buffered().use { o -> Id3.rewrite(i, edit, o) } }
                ctx.contentResolver.openOutputStream(uri, "wt")!!.use { o -> tmp.inputStream().use { it.copyTo(o) } }
            } finally {
                tmp.delete()
            }
        }
        val v = ContentValues()
        edit.title?.takeIf { it.isNotBlank() }?.let { v.put(MediaStore.Audio.Media.TITLE, it.trim()) }
        edit.artist?.let { v.put(MediaStore.Audio.Media.ARTIST, it.trim()) }
        edit.album?.let { v.put(MediaStore.Audio.Media.ALBUM, it.trim()) }
        edit.year?.let { v.put(MediaStore.Audio.Media.YEAR, it) }
        edit.trackNo?.let { v.put(MediaStore.Audio.Media.TRACK, it) }
        if (Build.VERSION.SDK_INT >= 30) {
            edit.albumArtist?.let { v.put(MediaStore.Audio.Media.ALBUM_ARTIST, it.trim()) }
            edit.genre?.let { v.put(MediaStore.Audio.Media.GENRE, it.trim()) }
        }
        if (v.size() > 0) ctx.contentResolver.update(uri, v, null, null)
    }
}
