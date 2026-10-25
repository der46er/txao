package com.txao.files

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val thumbCache = LruCache<String, ImageBitmap>(160)

private fun decodeThumb(f: File, px: Int): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.path, bounds)
    if (bounds.outWidth <= 0) return null
    var s = 1
    while (bounds.outWidth / (s * 2) >= px && bounds.outHeight / (s * 2) >= px) s *= 2
    BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })?.asImageBitmap()
}.getOrNull()

fun iconFor(kind: Kind): ImageVector = when (kind) {
    Kind.Folder -> Icons.Filled.Folder
    Kind.Image -> Icons.Filled.Image
    Kind.Video -> Icons.Filled.Movie
    Kind.Audio -> Icons.Filled.MusicNote
    Kind.Document -> Icons.Filled.Description
    Kind.Archive -> Icons.Filled.FolderZip
    Kind.Apk -> Icons.Filled.Android
    Kind.Other -> Icons.Filled.InsertDriveFile
}

/** Folder glyph, image preview or file-type icon, drawn in a [size] square. */
@Composable
fun FileGlyph(file: File, isDir: Boolean, size: Dp, modifier: Modifier = Modifier) {
    val kind = if (isDir) Kind.Folder else kindOf(file)
    if (kind == Kind.Folder) {
        Icon(Icons.Filled.Folder, null, modifier.size(size), tint = FolderTint)
        return
    }
    if (kind == Kind.Image) {
        val px = with(LocalDensity.current) { size.roundToPx() }
        val key = file.path + "#" + file.lastModified()
        val bmp by produceState<ImageBitmap?>(thumbCache.get(key), key) {
            if (value == null) {
                value = withContext(Dispatchers.IO) { decodeThumb(file, px) }?.also { thumbCache.put(key, it) }
            }
        }
        bmp?.let {
            Image(it, null, modifier.size(size).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            return
        }
    }
    Box(
        modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Color(0x33CDB8FF)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconFor(kind), null, Modifier.size(size * 0.55f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
