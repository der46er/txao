package com.txao.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Decodes and caches grid thumbnails and viewer-sized images. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 6).toInt().coerceAtMost(96 * 1024 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun thumbnail(context: Context, item: MediaItem, px: Int): ImageBitmap? {
        val key = "t${item.uri}@$px"
        cache.get(key)?.let { return it.asImageBitmap() }
        val bmp = runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.loadThumbnail(item.uri, Size(px, px), null)
            } else if (item.isVideo) {
                videoFrame(context, item)
            } else {
                decode(context, item, px)
            }
        }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp.asImageBitmap()
    }

    /** Full-screen image, down-sampled so it fits within [maxPx] on its longest side. */
    fun image(context: Context, item: MediaItem, maxPx: Int): ImageBitmap? {
        val key = "i${item.uri}@$maxPx"
        cache.get(key)?.let { return it.asImageBitmap() }
        val bmp = runCatching { decode(context, item, maxPx) }.getOrNull() ?: return null
        cache.put(key, bmp)
        return bmp.asImageBitmap()
    }

    fun videoFrame(context: Context, item: MediaItem): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, item.uri)
            r.getFrameAtTime(0)
        } finally {
            r.release()
        }
    }

    private fun decode(context: Context, item: MediaItem, maxPx: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxPx)
        }
        val raw = cr.openInputStream(item.uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = exifRotation(context, item)
        if (rotation == 0f) return raw
        val m = android.graphics.Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    private fun exifRotation(context: Context, item: MediaItem): Float = runCatching {
        context.contentResolver.openInputStream(item.uri)?.use {
            when (android.media.ExifInterface(it).getAttributeInt(
                android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL
            )) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }
    }.getOrNull() ?: 0f
}

/** Largest power-of-two sample size that keeps the longest side at or above [maxPx]. */
fun sampleSize(w: Int, h: Int, maxPx: Int): Int {
    var s = 1
    val longest = maxOf(w, h)
    while (longest / (s * 2) >= maxPx) s *= 2
    return s
}
