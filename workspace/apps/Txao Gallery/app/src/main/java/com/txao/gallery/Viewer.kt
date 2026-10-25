package com.txao.gallery

import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

private const val MAX_ZOOM = 6f
private const val SLIDESHOW_MS = 3000L

@Composable
fun ViewerScreen(
    items: List<MediaItem>, startIndex: Int, favorites: Set<Long>,
    onFavorite: (Long) -> Unit, actions: GalleryActions, onClose: () -> Unit,
    canDelete: Boolean, onEdit: ((MediaItem) -> Unit)?,
) {
    val ctx = LocalContext.current
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex)) { items.size }
    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var slideshow by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<MediaItem?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
    var pickAlbum by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val current = items[pager.currentPage]

    LaunchedEffect(slideshow) {
        while (slideshow) {
            delay(SLIDESHOW_MS)
            pager.animateScrollToPage((pager.currentPage + 1) % items.size)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - kotlin.math.abs(dragY) / 900f).coerceIn(0.25f, 1f)))) {
        HorizontalPager(
            state = pager, userScrollEnabled = !zoomed, beyondViewportPageCount = 1,
            // Swipe down to close, swipe up for details; ignored while zoomed so panning still works.
            modifier = Modifier.fillMaxSize()
                .graphicsLayer { translationY = dragY }
                .pointerInput(zoomed, pager.currentPage) {
                    if (!zoomed) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (dragY > 220f) onClose() else if (dragY < -160f) info = current
                                dragY = 0f
                            },
                            onDragCancel = { dragY = 0f },
                        ) { change, amount ->
                            dragY += amount
                            change.consume()
                        }
                    }
                },
        ) { page ->
            val item = items[page]
            if (item.isVideo) {
                VideoPage(item, active = page == pager.currentPage && !slideshow, onTap = { chrome = !chrome })
            } else {
                ZoomableImage(item, onTap = { chrome = !chrome }, onZoomed = { if (page == pager.currentPage) zoomed = it })
            }
        }

        if (chrome) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).background(Color(0x99000000))
                    .statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pager.currentPage + 1} / ${items.size}", color = Color(0xB3FFFFFF), fontSize = 12.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color(0x99000000))
                    .navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BarButton(Icons.Default.Share, "Share") { share(ctx, current) }
                if (!current.isVideo && onEdit != null) BarButton(Icons.Default.Edit, "Edit") { onEdit(current) }
                if (current.id >= 0) {
                    val fav = current.id in favorites
                    BarButton(if (fav) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite") { onFavorite(current.id) }
                }
                if (canDelete) BarButton(Icons.Default.Delete, "Delete") {
                    if (android.os.Build.VERSION.SDK_INT >= 30) {
                        actions.trash(listOf(current), true, onClose)
                    } else confirmDelete = true
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(moreOpen, { moreOpen = false }) {
                        DropdownMenuItem(text = { Text("Details") }, onClick = { info = current; moreOpen = false })
                        if (current.id >= 0) {
                            DropdownMenuItem(text = { Text("Add to album") }, onClick = { pickAlbum = true; moreOpen = false })
                        }
                        if (items.size > 1) {
                            DropdownMenuItem(
                                text = { Text(if (slideshow) "Stop slideshow" else "Slideshow") },
                                onClick = { slideshow = !slideshow; moreOpen = false },
                            )
                        }
                        if (!current.isVideo) {
                            DropdownMenuItem(text = { Text("Set as wallpaper") }, onClick = { wallpaper(ctx, current); moreOpen = false })
                        }
                    }
                }
            }
        }
    }

    if (pickAlbum) {
        AlbumPickerDialog(
            names = actions.albumNames(),
            onPick = { name -> actions.addToAlbum(name, listOf(current)); pickAlbum = false },
            onDismiss = { pickAlbum = false },
        )
    }

    info?.let { item ->
        AlertDialog(
            onDismissRequest = { info = null },
            confirmButton = { TextButton({ info = null }) { Text("Close") } },
            title = { Text("Details") },
            text = {
                val details by produceState(emptyList<Pair<String, String>>(), item) {
                    value = withContext(Dispatchers.IO) { detailsOf(ctx, item) }
                }
                Column {
                    details.forEach { (k, v) ->
                        Row(Modifier.padding(vertical = 3.dp)) {
                            Text(k, Modifier.weight(0.4f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(v, Modifier.weight(0.6f), fontSize = 13.sp)
                        }
                    }
                }
            },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this file?") },
            text = { Text("This permanently deletes ${current.name}.") },
            confirmButton = {
                TextButton({ confirmDelete = false; actions.trash(listOf(current), true, onClose) }) { Text("Delete") }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BarButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.weight(1f)) { Icon(icon, label, tint = Color.White) }
}

/** Image with pinch-zoom, pan while zoomed and double-tap to toggle. Single-finger swipes pass through to the pager at 1x. */
@Composable
private fun ZoomableImage(item: MediaItem, onTap: () -> Unit, onZoomed: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, item.uri) {
        value = withContext(Dispatchers.IO) { Thumbs.image(ctx, item, 2048) }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }

    fun clamp(o: Offset, s: Float): Offset {
        val mx = box.width * (s - 1f) / 2f
        val my = box.height * (s - 1f) / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    Box(
        Modifier.fillMaxSize().onSizeChanged { box = it }
            .pointerInput_tap(onTap = onTap, onDoubleTap = {
                if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                onZoomed(scale > 1f)
            })
            .pointerInput_zoom { zoom, pan ->
                scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
                offset = if (scale == 1f) Offset.Zero else clamp(offset + pan, scale)
                onZoomed(scale > 1f)
            }
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(it, item.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

private fun Modifier.pointerInput_tap(onTap: () -> Unit, onDoubleTap: () -> Unit): Modifier =
    this.pointerInput(Unit) {
        detectTapGestures(onTap = { onTap() }, onDoubleTap = { onDoubleTap() })
    }

/** Handles pinch always, and one-finger pan only while zoomed in so the pager can still swipe at 1x. */
private fun Modifier.pointerInput_zoom(onTransform: (zoom: Float, pan: Offset) -> Unit): Modifier =
    this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var zoomedGesture = false
            do {
                val event = awaitPointerEvent()
                val zoom = event.calculateZoom()
                val pan = event.calculatePan()
                val multi = event.changes.count { it.pressed } > 1
                if (multi) zoomedGesture = true
                if (zoomedGesture || zoom != 1f) {
                    onTransform(zoom, pan)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
            } while (event.changes.any { it.pressed })
        }
    }

@Composable
private fun VideoPage(item: MediaItem, active: Boolean, onTap: () -> Unit) {
    val ctx = LocalContext.current
    val view = remember(item.uri) {
        VideoView(ctx).apply {
            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
            setVideoURI(item.uri)
            seekTo(1)
        }
    }
    LaunchedEffect(active) { if (active) view.start() else if (view.isPlaying) view.pause() }
    androidx.compose.runtime.DisposableEffect(view) { onDispose { view.stopPlayback() } }
    AndroidView(
        factory = {
            android.widget.FrameLayout(it).apply {
                addView(view, android.widget.FrameLayout.LayoutParams(-1, -1, android.view.Gravity.CENTER))
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun share(ctx: Context, item: MediaItem) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = item.mime
        putExtra(Intent.EXTRA_STREAM, item.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(send, null))
}

private fun wallpaper(ctx: Context, item: MediaItem) {
    val i = Intent(Intent.ACTION_ATTACH_DATA).apply {
        setDataAndType(item.uri, item.mime)
        putExtra("mimeType", item.mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, null))
}

private fun detailsOf(ctx: Context, item: MediaItem): List<Pair<String, String>> {
    val out = mutableListOf<Pair<String, String>>()
    out += "Name" to item.name
    if (item.albumName.isNotEmpty()) out += "Album" to item.albumName
    out += "Date" to DateFormat.getDateTimeInstance().format(Date(item.dateMs))
    if (item.size > 0) out += "Size" to formatSize(item.size)
    if (item.width > 0) out += "Resolution" to "${item.width} × ${item.height}"
    if (item.isVideo && item.durationMs > 0) out += "Duration" to formatDuration(item.durationMs)
    out += "Type" to item.mime
    if (!item.isVideo) runCatching {
        ctx.contentResolver.openInputStream(item.uri)?.use { s ->
            val e = ExifInterface(s)
            val camera = listOfNotNull(e.getAttribute(ExifInterface.TAG_MAKE), e.getAttribute(ExifInterface.TAG_MODEL)).joinToString(" ")
            if (camera.isNotBlank()) out += "Camera" to camera
            e.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { out += "Aperture" to "f/$it" }
            e.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { out += "Exposure" to "$it s" }
            e.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { out += "ISO" to it }
            e.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { out += "Focal length" to it }
            val ll = FloatArray(2)
            if (e.getLatLong(ll)) out += "Location" to String.format(java.util.Locale.US, "%.5f, %.5f", ll[0], ll[1])
        }
    }
    return out
}
