package com.txao.files

import android.content.Context

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

    /** Length of the fade between folders and layouts, in milliseconds (0.1 s to 0.9 s). */
    var fadeMs: Int
        get() = sp.getInt("fade_ms", 200).coerceIn(100, 900)
        set(v) = sp.edit().putInt("fade_ms", v.coerceIn(100, 900)).apply()

    /** Version whose release notes the user has already seen. */
    var lastSeenVersion: String
        get() = sp.getString("last_seen_version", "") ?: ""
        set(v) = sp.edit().putString("last_seen_version", v).apply()

    /** Palette offered when colouring folders, as "#RRGGBB" codes in the user's order. */
    var palette: List<String>
        get() = sp.getString("palette", null)?.split(',')?.filter { Hex.parse(it) != null } ?: DefaultPalette
        set(v) = sp.edit().putString("palette", v.joinToString(",")).apply()

    var folderColors: Map<String, Int>
        get() = (sp.getString("folder_colors", "") ?: "").lines().mapNotNull {
            val i = it.lastIndexOf('\t')
            val c = if (i > 0) it.substring(i + 1).toIntOrNull() else null
            if (c == null) null else it.substring(0, i) to c
        }.toMap()
        set(v) = sp.edit().putString("folder_colors", v.entries.joinToString("\n") { it.key + "\t" + it.value }).apply()

    /** Folder the Files tab opens in; empty means the internal storage root. */
    var homePath: String
        get() = sp.getString("home_path", "") ?: ""
        set(v) = sp.edit().putString("home_path", v).apply()

    /** "device" (fingerprint / phone lock) or "password". */
    var vaultMode: String
        get() = sp.getString("vault_mode", "device") ?: "device"
        set(v) = sp.edit().putString("vault_mode", v).apply()

    /** The wrapped vault master key and, in password mode, the password salt (both Base64). */
    var vaultBlob: String
        get() = sp.getString("vault_blob", "") ?: ""
        set(v) = sp.edit().putString("vault_blob", v).apply()

    var vaultSalt: String
        get() = sp.getString("vault_salt", "") ?: ""
        set(v) = sp.edit().putString("vault_salt", v).apply()
}
