package com.txao.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.BitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Cover art for a track, keyed by the track's content Uri string.
 * Order: the system thumbnail (embedded art or the album's art, Android 10+), then the embedded picture read directly.
 * Results (and misses) are cached so scrolling never re-reads files.
 */
object Art {
    private val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 8).toInt().coerceAtMost(48 * 1024 * 1024)) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val misses = java.util.Collections.synchronizedSet(HashSet<String>())

    fun peek(key: String?, px: Int): Bitmap? = key?.let { cache.get("$it@$px") }

    fun load(ctx: Context, key: String, px: Int): Bitmap? {
        val ck = "$key@$px"
        cache.get(ck)?.let { return it }
        if (ck in misses) return null
        val uri = Uri.parse(key)
        val bmp = fromOverride(ctx, uri, px) ?: fromThumbnail(ctx, uri, px) ?: fromEmbedded(ctx, uri, px)
        if (bmp != null) cache.put(ck, bmp) else misses.add(ck)
        return bmp
    }

    /** Cover chosen in the editor for a non-MP3 file. */
    private fun fromOverride(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return null
        val f = Overrides.coverFile(ctx, id)
        return if (f.exists()) runCatching { decode(f.readBytes(), px) }.getOrNull() else null
    }

    /** Forget cached covers and misses, e.g. after tags or covers were edited. */
    fun clear() {
        cache.evictAll()
        misses.clear()
    }

    private fun fromThumbnail(ctx: Context, uri: Uri, px: Int): Bitmap? =
        if (Build.VERSION.SDK_INT >= 29) runCatching { ctx.contentResolver.loadThumbnail(uri, Size(px, px), null) }.getOrNull() else null

    private fun fromEmbedded(ctx: Context, uri: Uri, px: Int): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(ctx, uri)
            r.embeddedPicture?.let { decode(it, px) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Decodes [data] no larger than about [px] on its long side. */
    fun decode(data: ByteArray, px: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= px && bounds.outHeight / (sample * 2) >= px) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}

/** Gives the media notification and lock screen the same cover art the app shows. */
@UnstableApi
class ArtBitmapLoader(private val ctx: Context) : BitmapLoader {
    private val exec = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())

    override fun supportsMimeType(mimeType: String) = mimeType.startsWith("image/")

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        exec.submit<Bitmap> { Art.decode(data, 512) ?: error("undecodable image") }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        exec.submit<Bitmap> { Art.load(ctx, uri.toString(), 512) ?: error("no cover art") }
}
