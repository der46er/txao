package com.txao.files

import android.content.Context
import android.content.ContentResolver
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.os.Environment
import android.os.storage.StorageManager
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.Locale

data class Entry(
    val file: File,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    /** Direct children for folders, 0 for files. */
    val items: Int,
) {
    val name: String get() = file.name
}

data class Volume(val label: String, val root: File)

enum class Kind { Folder, Image, Video, Audio, Document, Archive, Apk, Other }

fun entryOf(f: File): Entry {
    val dir = f.isDirectory
    return Entry(f, dir, if (dir) 0 else f.length(), f.lastModified(), if (dir) f.list()?.size ?: 0 else 0)
}

fun listDir(dir: File, showHidden: Boolean, field: SortField, ascending: Boolean): List<Entry> {
    val all = dir.listFiles()?.filter { showHidden || !it.name.startsWith(".") }?.map(::entryOf) ?: return emptyList()
    return sortEntries(all, field, ascending)
}

/** Folders always come first; inside each group the chosen order applies. */
fun sortEntries(list: List<Entry>, field: SortField, ascending: Boolean): List<Entry> {
    val cmp: Comparator<Entry> = when (field) {
        SortField.Name -> compareBy { it.name.lowercase(Locale.ROOT) }
        SortField.Date -> compareBy { it.modified }
        SortField.Size -> compareBy { it.size }
    }
    val ordered = if (ascending) cmp else cmp.reversed()
    return list.sortedWith(compareByDescending<Entry> { it.isDir }.then(ordered))
}

fun searchIn(dir: File, query: String, showHidden: Boolean, limit: Int = 300): List<Entry> {
    val q = query.trim().lowercase(Locale.ROOT)
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<Entry>()
    dir.walkTopDown()
        .onEnter { showHidden || !it.name.startsWith(".") || it == dir }
        .forEach {
            if (it != dir && it.name.lowercase(Locale.ROOT).contains(q) && (showHidden || !it.name.startsWith("."))) out.add(entryOf(it))
            if (out.size >= limit) return out
        }
    return out
}

/** Newest files under [root], skipping hidden folders and Android/ (app-private data). */
fun recentFiles(root: File, showHidden: Boolean, limit: Int = 100): List<Entry> {
    val out = ArrayList<File>()
    root.walkTopDown()
        .onEnter { it == root || ((showHidden || !it.name.startsWith(".")) && !(it.parentFile == root && it.name == "Android")) }
        .forEach { if (it.isFile && (showHidden || !it.name.startsWith("."))) out.add(it) }
    return out.sortedByDescending { it.lastModified() }.take(limit).map(::entryOf)
}

fun volumes(context: Context): List<Volume> {
    val list = ArrayList<Volume>()
    list.add(Volume("Internal", Environment.getExternalStorageDirectory()))
    val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    for (v in sm.storageVolumes) {
        val dir = v.directory ?: continue
        if (v.isPrimary || !v.state.equals(Environment.MEDIA_MOUNTED, true)) continue
        list.add(Volume(v.getDescription(context) ?: "SD card", dir))
    }
    return list
}

fun kindOf(f: File): Kind {
    if (f.isDirectory) return Kind.Folder
    return when (f.extension.lowercase(Locale.ROOT)) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif" -> Kind.Image
        "mp4", "mkv", "webm", "mov", "3gp", "avi", "m4v" -> Kind.Video
        "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav", "amr" -> Kind.Audio
        "txt", "md", "pdf", "doc", "docx", "odt", "xls", "xlsx", "ppt", "pptx", "csv", "json", "xml", "html", "log", "rtf" -> Kind.Document
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz" -> Kind.Archive
        "apk", "apks", "xapk" -> Kind.Apk
        else -> Kind.Other
    }
}

fun humanSize(bytes: Long): String {
    if (bytes < 1000) return "$bytes B"
    val units = arrayOf("kB", "MB", "GB", "TB")
    var v = bytes / 1000.0
    var i = 0
    while (v >= 1000 && i < units.lastIndex) { v /= 1000; i++ }
    return String.format(Locale.getDefault(), if (v >= 100 || i == 0) "%.0f %s" else "%.1f %s", v, units[i])
}

fun itemsLabel(n: Int) = if (n == 1) "1 item" else "$n items"

fun mimeOf(f: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase(Locale.ROOT)) ?: "*/*"

fun uriOf(context: Context, f: File) = FileProvider.getUriForFile(context, "com.txao.files.fileprovider", f)

fun openFile(context: Context, f: File): Boolean {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(context, f), mimeOf(f))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    return try { context.startActivity(i); true } catch (_: Exception) { false }
}

fun shareFiles(context: Context, files: List<File>) {
    val list = files.filter { it.isFile }
    if (list.isEmpty()) return
    val i = if (list.size == 1) {
        Intent(Intent.ACTION_SEND).setType(mimeOf(list[0])).putExtra(Intent.EXTRA_STREAM, uriOf(context, list[0]))
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list.map { uriOf(context, it) }))
    }
    i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try { context.startActivity(Intent.createChooser(i, null)) } catch (_: Exception) {}
}

/** Copies (or moves) [src] into [dir], picking "name (2)" style names when taken. Returns success. */
fun transfer(src: File, dir: File, move: Boolean): Boolean {
    if (src.isDirectory && (dir == src || dir.path.startsWith(src.path + "/"))) return false
    if (move && src.parentFile == dir) return true
    var target = File(dir, src.name)
    var n = 2
    while (target.exists()) {
        val base = if (src.isFile) src.nameWithoutExtension else src.name
        val ext = if (src.isFile && src.extension.isNotEmpty()) "." + src.extension else ""
        target = File(dir, "$base ($n)$ext"); n++
    }
    if (move && src.renameTo(target)) return true
    val ok = if (src.isDirectory) src.copyRecursively(target, false) else runCatching { src.copyTo(target); true }.getOrDefault(false)
    if (ok && move) src.deleteRecursively()
    return ok
}

class RecentPage(val entries: List<Entry>, val nextOffset: Int, val end: Boolean)

/**
 * One page of the newest files, read from the media index (fast, sorted, pageable) instead of
 * walking the whole storage. [offset] counts index rows, so pass back [RecentPage.nextOffset].
 */
fun recentPage(context: Context, offset: Int, limit: Int, showHidden: Boolean): RecentPage {
    val uri = MediaStore.Files.getContentUri("external")
    val data = MediaStore.Files.FileColumns.DATA
    var next = offset
    val out = ArrayList<Entry>()
    var rounds = 0
    while (out.size < limit && rounds++ < 12) {
        val args = Bundle().apply {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, "$data NOT LIKE ? AND $data NOT LIKE ?")
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, arrayOf("%/Android/%", "%/.%"))
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED))
            putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
            putInt(ContentResolver.QUERY_ARG_LIMIT, limit)
            putInt(ContentResolver.QUERY_ARG_OFFSET, next)
        }
        var rows = 0
        context.contentResolver.query(uri, arrayOf(data), args, null)?.use { c ->
            while (c.moveToNext()) {
                rows++
                val f = File(c.getString(0) ?: continue)
                if (f.isFile && (showHidden || !f.name.startsWith("."))) out.add(entryOf(f))
            }
        }
        next += rows
        if (rows < limit) return RecentPage(out, next, true)
    }
    if (offset == 0 && out.isEmpty()) {
        // Nothing indexed (fresh phone, or the index is off): fall back to scanning once.
        return RecentPage(recentFiles(Environment.getExternalStorageDirectory(), showHidden, 60), 0, true)
    }
    return RecentPage(out, next, false)
}

/**
 * Files ready to hand to the share sheet: plain files as they are, folders zipped into the app cache
 * (the cache is wiped at the start of every share, so nothing piles up).
 */
fun prepareShare(context: Context, files: List<File>): List<File> {
    val dir = File(context.cacheDir, "share")
    dir.deleteRecursively()
    dir.mkdirs()
    return files.mapNotNull { f ->
        when {
            f.isFile -> f
            f.isDirectory -> runCatching { zipFolder(f, File(dir, f.name + ".zip")) }.getOrNull()
            else -> null
        }
    }
}

private fun zipFolder(folder: File, target: File): File {
    ZipOutputStream(target.outputStream().buffered()).use { zip ->
        folder.walkTopDown().forEach { f ->
            val name = folder.name + "/" + f.relativeTo(folder).path.replace(File.separatorChar, '/')
            when {
                f == folder -> zip.putNextEntry(ZipEntry(folder.name + "/")).also { zip.closeEntry() }
                f.isDirectory -> zip.putNextEntry(ZipEntry("$name/")).also { zip.closeEntry() }
                f.isFile -> {
                    zip.putNextEntry(ZipEntry(name))
                    runCatching { f.inputStream().use { it.copyTo(zip) } }
                    zip.closeEntry()
                }
            }
        }
    }
    return target
}
