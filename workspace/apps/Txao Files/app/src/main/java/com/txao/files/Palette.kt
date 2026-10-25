package com.txao.files

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** "#RRGGBB" colour codes as typed by the user. */
object Hex {
    /** Parses "#RRGGBB" or "RRGGBB" into an opaque ARGB int, or null when it is not a valid code. */
    fun parse(text: String): Int? {
        val t = text.trim().removePrefix("#")
        if (t.length != 6 || !t.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
        return 0xFF000000.toInt() or t.toInt(16)
    }

    fun format(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)
}

/** Ten built-in folder colours; the first one is the default folder tint. */
val DefaultPalette = listOf(
    "#A9B1DB", "#EF5350", "#FFA726", "#FFEE58", "#66BB6A",
    "#26A69A", "#42A5F5", "#5C6BC0", "#AB47BC", "#EC407A",
)

/** Moves [index] by [delta] places; out-of-range moves leave the list as is. */
fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
    val to = index + delta
    if (index !in indices || to !in indices) return this
    val m = toMutableList()
    m.add(to, m.removeAt(index))
    return m
}

/** Folder colours chosen by the user, keyed by folder path. Reading [get] inside Compose tracks changes. */
class FolderColors(private val prefs: Prefs) {
    private val map = mutableStateMapOf<String, Int>().apply { putAll(prefs.folderColors) }

    fun get(path: String): Color? = map[LayoutRules.normalize(path)]?.let { Color(it) }

    fun set(path: String, argb: Int?) {
        val key = LayoutRules.normalize(path)
        if (argb == null) map.remove(key) else map[key] = argb
        prefs.folderColors = HashMap(map)
    }
}

val LocalFolderColors = staticCompositionLocalOf<FolderColors?> { null }
