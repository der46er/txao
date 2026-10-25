package com.txao.files

import android.content.Context

data class Release(val version: String, val notes: List<String>)

/** Release notes, newest first. Add an entry here with every version bump. */
object Changelog {
    val releases = listOf(
        Release("1.2.7", listOf(
            "Fixed the squeezed, glitching layout while switching between grid and list folders",
        )),
        Release("1.2.6", listOf(
            "Plain fade between folders, no sideways sliding",
            "Fade length is adjustable in Settings (0.1 s to 0.9 s)",
            "New Settings screen and this What's new list",
        )),
        Release("1.2.5", listOf(
            "Switching between a grid folder and a list folder no longer glitches",
            "Folders fade in and items move smoothly",
            "Bigger, flat folder icons in the 4-column grid",
        )),
        Release("1.2.4", listOf(
            "Smaller launcher icon with clear margins",
            "Folder grid always shows 4 per row",
            "More space between the + button and the bottom bar",
        )),
        Release("1.2.3", listOf(
            "Readable text and light status bar icons",
            "Redesigned launcher icon",
        )),
        Release("1.2.1", listOf(
            "Denser Fossify-like sizes",
            "Locked tab for private files, protected by your phone lock",
            "Storage usage moved to the top of Recents",
        )),
        Release("1.2.0", listOf(
            "New Fossify-style look with search bar, breadcrumb and bottom bar",
            "Grid or list layout remembered separately for every folder",
            "Optional custom background image",
        )),
    )
}

@Suppress("DEPRECATION")
fun appVersion(context: Context): String =
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
