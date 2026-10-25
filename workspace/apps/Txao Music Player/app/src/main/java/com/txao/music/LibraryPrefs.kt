package com.txao.music

import android.content.Context
import java.io.File

/** What the user hid and how albums are ordered. Private to this app. */
class LibraryPrefs(context: Context) {
    private val p = context.getSharedPreferences("library", Context.MODE_PRIVATE)

    var hiddenSongs: Set<Long>
        get() = (p.getStringSet("hidden_songs", emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
        set(v) { p.edit().putStringSet("hidden_songs", v.map { it.toString() }.toSet()).apply() }

    var hiddenAlbums: Set<String>
        get() = p.getStringSet("hidden_albums", emptySet()) ?: emptySet()
        set(v) { p.edit().putStringSet("hidden_albums", v).apply() }

    var albumSort: Int
        get() = p.getInt("album_sort", Library.SORT_NAME)
        set(v) { p.edit().putInt("album_sort", v).apply() }

    var albumOrder: List<String>
        get() = (p.getString("album_order", "") ?: "").split('\n').filter { it.isNotEmpty() }
        set(v) { p.edit().putString("album_order", v.joinToString("\n")).apply() }
}

/** Artist pictures chosen in the editor, kept in the app (artists have no cover tag). */
object ArtistImages {
    private fun file(ctx: Context, name: String) = File(File(ctx.filesDir, "artists"), "${name.lowercase().hashCode().toUInt().toString(16)}.jpg")

    fun has(ctx: Context, name: String) = file(ctx, name).exists()

    fun read(ctx: Context, name: String): ByteArray? = file(ctx, name).takeIf { it.exists() }?.readBytes()

    fun save(ctx: Context, name: String, bytes: ByteArray) {
        val f = file(ctx, name)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    /** Keeps the picture when an artist is renamed. */
    fun rename(ctx: Context, from: String, to: String) {
        if (from.equals(to, ignoreCase = true)) return
        val src = file(ctx, from)
        if (src.exists()) src.copyTo(file(ctx, to), overwrite = true).also { src.delete() }
    }
}
