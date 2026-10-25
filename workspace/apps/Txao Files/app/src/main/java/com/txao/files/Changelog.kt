package com.txao.files

import android.content.Context

data class Release(val version: String, val notes: List<String>)

/** Release notes, newest first. Add an entry here with every version bump. */
object Changelog {
    val releases = listOf(
        Release("1.3.4", listOf(
            "Hidden files (names starting with a dot) are now shown by default, and folders tell you how many are hidden if you turn that off",
            "Settings has a button that opens the All files access screen",
            "Also declares the classic storage permissions some phones check",
        )),
        Release("1.3.3", listOf(
            "Recents now also scans the real folders, so files that other apps never registered show up too",
            "Folders Android keeps closed to apps (like Android/data) say so instead of looking empty",
            "Settings shows whether All files access is allowed",
        )),
        Release("1.3.2", listOf(
            "Every folder loads 20 items at a time and adds 20 more as you scroll, so big folders open instantly",
        )),
        Release("1.3.1", listOf(
            "Sharing a folder now works: it is zipped and shared as one file",
            "Sharing several files and folders at once",
        )),
        Release("1.3.0", listOf(
            "Recents loads 20 files at a time and fills in as you scroll",
            "Color any folder: 10 default colors or your own hex code",
            "Settings: manage and reorder your folder colors, choose the home folder",
            "Locked files are now really encrypted; unlock with fingerprint / phone lock or your own password",
            "Files locked by older versions are encrypted the first time you unlock",
            "Removed the custom background image",
        )),
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
