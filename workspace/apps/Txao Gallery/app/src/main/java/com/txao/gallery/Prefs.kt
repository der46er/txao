package com.txao.gallery

import android.content.Context

/** Small on-device settings store: favourites, hidden albums, sort order, grid size. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("txao_gallery", Context.MODE_PRIVATE)

    var favorites: Set<Long>
        get() = readIds("favorites")
        set(v) = writeIds("favorites", v)

    var hiddenAlbums: Set<Long>
        get() = readIds("hidden_albums")
        set(v) = writeIds("hidden_albums", v)

    var sort: SortSpec
        get() = SortSpec(
            SortField.entries.getOrElse(sp.getInt("sort_field", 0)) { SortField.Date },
            sp.getBoolean("sort_asc", false),
        )
        set(v) = sp.edit().putInt("sort_field", v.field.ordinal).putBoolean("sort_asc", v.ascending).apply()

    var columns: Int
        get() = sp.getInt("columns", 3).coerceIn(2, 6)
        set(v) = sp.edit().putInt("columns", v.coerceIn(2, 6)).apply()

    private fun readIds(key: String): Set<Long> =
        (sp.getStringSet(key, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()

    private fun writeIds(key: String, v: Set<Long>) {
        sp.edit().putStringSet(key, v.map { it.toString() }.toSet()).apply()
    }
}
