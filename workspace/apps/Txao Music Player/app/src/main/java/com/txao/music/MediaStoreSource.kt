package com.txao.music

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

object MediaStoreSource {
    fun albumArtUri(albumId: Long) =
        ContentUris.withAppendedId(android.net.Uri.parse("content://media/external/audio/albumart"), albumId)

    fun loadTracks(context: Context): List<Track> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val out = ArrayList<Track>()
        context.contentResolver.query(
            collection, projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 0",
            null, "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val trackId = c.getLong(id)
                out += Track(
                    id = trackId,
                    title = c.getString(title) ?: "Unknown",
                    artist = c.getString(artist)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    album = c.getString(album) ?: "Unknown album",
                    albumId = c.getLong(albumId),
                    durationMs = c.getLong(duration),
                    uri = ContentUris.withAppendedId(collection, trackId).toString(),
                )
            }
        }
        return out
    }
}
