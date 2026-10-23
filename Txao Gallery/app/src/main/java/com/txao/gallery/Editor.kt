package com.txao.gallery

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class FilterPreset(val label: String) { None("Original"), Vivid("Vivid"), Mono("Mono"), Warm("Warm"), Cool("Cool"), Fade("Fade") }

enum class CropPreset(val label: String, val ratio: Float?) {
    Free("Original", null), Square("1:1", 1f), Photo("4:3", 4f / 3f), Wide("16:9", 16f / 9f), Portrait("3:4", 3f / 4f), Tall("9:16", 9f / 16f)
}

data class EditState(
    val quarterTurns: Int = 0,
    val flip: Boolean = false,
    val crop: CropPreset = CropPreset.Free,
    val brightness: Float = 0f,   // -1..1
    val contrast: Float = 1f,     // 0.5..1.5
    val saturation: Float = 1f,   // 0..2
    val filter: FilterPreset = FilterPreset.None,
)

private fun colorMatrix(s: EditState): ColorMatrix {
    val cm = ColorMatrix()
    cm.setSaturation(s.saturation)
    val preset = when (s.filter) {
        FilterPreset.None -> null
        FilterPreset.Vivid -> ColorMatrix().apply { setSaturation(1.5f) }
        FilterPreset.Mono -> ColorMatrix().apply { setSaturation(0f) }
        FilterPreset.Warm -> ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 8f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0.88f, 0f, -6f, 0f, 0f, 0f, 1f, 0f))
        FilterPreset.Cool -> ColorMatrix(floatArrayOf(0.9f, 0f, 0f, 0f, -4f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.12f, 0f, 10f, 0f, 0f, 0f, 1f, 0f))
        FilterPreset.Fade -> ColorMatrix(floatArrayOf(0.85f, 0f, 0f, 0f, 30f, 0f, 0.85f, 0f, 0f, 30f, 0f, 0f, 0.85f, 0f, 30f, 0f, 0f, 0f, 1f, 0f))
    }
    if (preset != null) cm.postConcat(preset)
    val c = s.contrast
    val t = (1f - c) * 128f + s.brightness * 100f
    cm.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    return cm
}

/** Applies rotate, flip, crop and colour edits to [src]. */
fun renderEdit(src: Bitmap, s: EditState): Bitmap {
    val m = Matrix().apply {
        postRotate(90f * (s.quarterTurns % 4))
        if (s.flip) postScale(-1f, 1f)
    }
    val oriented = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
    val r = centerCrop(oriented.width, oriented.height, s.crop.ratio)
    val out = Bitmap.createBitmap(r[2], r[3], Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(colorMatrix(s)) }
    Canvas(out).drawBitmap(oriented, android.graphics.Rect(r[0], r[1], r[0] + r[2], r[1] + r[3]), android.graphics.Rect(0, 0, r[2], r[3]), paint)
    return out
}

/** Saves [bmp] as a new JPEG in Pictures/Txao. The original is never touched. */
private fun saveCopy(context: Context, bmp: Bitmap, originalName: String): Boolean {
    val base = originalName.substringBeforeLast('.', originalName)
    val name = "${base}_edit_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Txao")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Txao").apply { mkdirs() }
            @Suppress("DEPRECATION")
            put(MediaStore.MediaColumns.DATA, File(dir, name).absolutePath)
        }
    }
    val cr = context.contentResolver
    val uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } ?: error("no stream")
        if (Build.VERSION.SDK_INT >= 29) {
            cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
        true
    } catch (e: Exception) {
        cr.delete(uri, null, null)
        false
    }
}

private enum class EditTab(val label: String) { Crop("Crop"), Adjust("Adjust"), Filters("Filters") }

@Composable
fun EditorScreen(item: MediaItem, onClose: () -> Unit, onSaved: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(EditState()) }
    var tab by remember { mutableStateOf(EditTab.Crop) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val source by androidx.compose.runtime.produceState<Bitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { Thumbs.bitmap(ctx, item, 1280) }
    }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(source, state) {
        val src = source ?: return@LaunchedEffect
        preview = withContext(Dispatchers.Default) { renderEdit(src, state).asImageBitmap() }
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Text("EDIT", color = Color.White, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
            if (failed) Text("Couldn't save", color = Color(0xFFFF8A80), fontSize = 12.sp, modifier = Modifier.padding(end = 8.dp))
            Button(
                enabled = source != null && !saving,
                onClick = {
                    saving = true; failed = false
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            val full = Thumbs.bitmap(ctx, item, 4096) ?: return@withContext false
                            saveCopy(ctx, renderEdit(full, state), item.name)
                        }
                        saving = false
                        if (ok) onSaved() else failed = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentText),
            ) { Text(if (saving) "Saving…" else "Save copy", fontWeight = FontWeight.Bold) }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            preview?.let { Image(it, null, Modifier.fillMaxSize().padding(8.dp), contentScale = ContentScale.Fit) }
        }

        Column(
            Modifier.fillMaxWidth().background(Color(0xFF141824)).navigationBarsPadding().padding(vertical = 8.dp),
        ) {
            when (tab) {
                EditTab.Crop -> {
                    LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            IconButton(onClick = { state = state.copy(quarterTurns = (state.quarterTurns + 1) % 4) }) {
                                Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate", tint = Color.White)
                            }
                        }
                        item {
                            IconButton(onClick = { state = state.copy(flip = !state.flip) }) { Icon(Icons.Default.Flip, "Flip", tint = Color.White) }
                        }
                        items(CropPreset.entries) { p ->
                            GlassChip(if (state.crop == p) "● ${p.label}" else p.label) { state = state.copy(crop = p) }
                        }
                    }
                }
                EditTab.Adjust -> Column(Modifier.padding(horizontal = 16.dp)) {
                    LabeledSlider("Brightness", state.brightness, -1f..1f) { state = state.copy(brightness = it) }
                    LabeledSlider("Contrast", state.contrast, 0.5f..1.5f) { state = state.copy(contrast = it) }
                    LabeledSlider("Saturation", state.saturation, 0f..2f) { state = state.copy(saturation = it) }
                }
                EditTab.Filters -> LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(FilterPreset.entries) { f ->
                        GlassChip(if (state.filter == f) "● ${f.label}" else f.label) { state = state.copy(filter = f) }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (t in EditTab.entries) {
                    Text(
                        t.label.uppercase(), fontWeight = FontWeight.Black, fontSize = 13.sp,
                        color = if (t == tab) Accent else Color(0x99FFFFFF),
                        modifier = Modifier.clickable { tab = t }.padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color.White, fontSize = 13.sp, modifier = Modifier.width(92.dp))
        Slider(value = value, onValueChange = onChange, valueRange = range, modifier = Modifier.weight(1f))
    }
}
