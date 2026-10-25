package com.txao.files

import android.content.Context
import java.io.File

enum class FolderLayout { Grid, List }

enum class SortField { Name, Date, Size }

/**
 * Which layout a folder opens in. Every folder can have its own choice; a folder
 * without one falls back to a default: storage roots (the "main folders") are a
 * grid, everything below them is a list.
 */
object LayoutRules {
    fun key(path: String) = "layout:" + normalize(path)

    fun normalize(path: String) = if (path.length > 1) path.trimEnd('/') else path

    fun default(path: String, roots: Collection<String>): FolderLayout =
        if (roots.any { normalize(it) == normalize(path) }) FolderLayout.Grid else FolderLayout.List

    fun resolve(stored: String?, path: String, roots: Collection<String>): FolderLayout =
        FolderLayout.entries.firstOrNull { it.name == stored } ?: default(path, roots)
}

/** On-device settings: per-folder layout, sort order, hidden files, wallpaper. */
class Prefs(private val context: Context) {
    private val sp = context.getSharedPreferences("txao_files", Context.MODE_PRIVATE)

    fun layoutFor(path: String, roots: Collection<String>): FolderLayout =
        LayoutRules.resolve(sp.getString(LayoutRules.key(path), null), path, roots)

    /** Saves [layout] for this folder only; null forgets it so the default applies again. */
    fun setLayout(path: String, layout: FolderLayout?) {
        val e = sp.edit()
        if (layout == null) e.remove(LayoutRules.key(path)) else e.putString(LayoutRules.key(path), layout.name)
        e.apply()
    }

    fun hasCustomLayout(path: String) = sp.contains(LayoutRules.key(path))

    var showHidden: Boolean
        get() = sp.getBoolean("show_hidden", false)
        set(v) = sp.edit().putBoolean("show_hidden", v).apply()

    var sortField: SortField
        get() = SortField.entries.getOrElse(sp.getInt("sort_field", 0)) { SortField.Name }
        set(v) = sp.edit().putInt("sort_field", v.ordinal).apply()

    var sortAscending: Boolean
        get() = sp.getBoolean("sort_asc", true)
        set(v) = sp.edit().putBoolean("sort_asc", v).apply()

    /** Optional user-picked wallpaper, stored privately inside the app. */
    val backgroundFile: File get() = File(context.filesDir, "background.img")
}
