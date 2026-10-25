package com.txao.phone

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decodes contact photos from content URIs, downsampled, with a small in-memory cache. */
object Photos {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun cached(uri: String, maxPx: Int): ImageBitmap? = cache.get("$uri@$maxPx")?.asImageBitmap()

    fun load(ctx: Context, uri: String, maxPx: Int): ImageBitmap? {
        val key = "$uri@$maxPx"
        cache.get(key)?.let { return it.asImageBitmap() }
        return try {
            val u = Uri.parse(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            ctx.contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
            val bmp = ctx.contentResolver.openInputStream(u)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return null
            cache.put(key, bmp)
            bmp.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    /** Scales a picked image down and encodes it for storing as a contact photo. */
    fun encodeForContact(ctx: Context, uri: Uri, maxPx: Int = 720): ByteArray? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        bmp?.let {
            val scale = maxPx.toFloat() / maxOf(it.width, it.height)
            val out = if (scale < 1f) Bitmap.createScaledBitmap(it, (it.width * scale).toInt().coerceAtLeast(1), (it.height * scale).toInt().coerceAtLeast(1), true) else it
            java.io.ByteArrayOutputStream().also { s -> out.compress(Bitmap.CompressFormat.JPEG, 90, s) }.toByteArray()
        }
    } catch (_: Exception) {
        null
    }
}

/** Looks up a contact photo for a phone number; supplied once at the top of the app. */
val LocalPhotoResolver = compositionLocalOf<(String) -> String?> { { null } }

@Composable
fun rememberPhoto(uri: String?, maxPx: Int = 256): ImageBitmap? {
    val ctx = LocalContext.current
    val state = produceState(uri?.let { Photos.cached(it, maxPx) }, uri, maxPx) {
        value = if (uri == null) null else Photos.cached(uri, maxPx) ?: withContext(Dispatchers.IO) { Photos.load(ctx, uri, maxPx) }
    }
    return state.value
}

private val AvatarTints = listOf(
    Color(0xFFB79CFF), Color(0xFF8FA3D6), Color(0xFFD7A6C6), Color(0xFF8FE3B5), Color(0xFFF2C58F), Color(0xFF8FD6E3),
)

fun avatarTint(name: String): Color = AvatarTints[(name.hashCode() and 0x7fffffff) % AvatarTints.size]

/** Contact photo when there is one (given directly or found by number), otherwise a tinted initial. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp, number: String? = null, photoUri: String? = null) {
    val resolver = LocalPhotoResolver.current
    val uri = photoUri ?: number?.let(resolver)
    val photo = rememberPhoto(uri, if (size > 80.dp) 512 else 192)
    val tint = avatarTint(name)
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, name, Modifier.size(size), contentScale = ContentScale.Crop)
        } else {
            Text(name.trim().firstOrNull()?.uppercase() ?: "#", fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
