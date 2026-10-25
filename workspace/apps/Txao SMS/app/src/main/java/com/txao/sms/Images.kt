package com.txao.sms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes pictures (contact photos, backgrounds, MMS attachments) downsampled, with a small in-memory cache. */
object Images {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(uri: Uri, maxPx: Int): ImageBitmap? = cache.get("$uri@$maxPx")?.asImageBitmap()

    fun load(ctx: Context, uri: Uri, maxPx: Int): ImageBitmap? {
        val key = "$uri@$maxPx"
        cache.get(key)?.let { return it.asImageBitmap() }
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            cache.put(key, bmp)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
}

/** The picture behind [uri], loaded off the main thread; null while loading or when there is none. */
@Composable
fun rememberImage(uri: Uri?, maxPx: Int = 512): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState<ImageBitmap?>(uri?.let { Images.cached(it, maxPx) }, uri, maxPx) {
        value = if (uri == null) null else Images.cached(uri, maxPx) ?: withContext(Dispatchers.IO) { Images.load(ctx, uri, maxPx) }
    }
    return state.value
}
