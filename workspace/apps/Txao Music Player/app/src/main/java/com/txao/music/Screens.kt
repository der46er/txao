package com.txao.music

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.filled.DragHandle
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@Composable
fun SettingsScreen(prefs: TxaoPrefs, vm: PlayerViewModel, back: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { back() }
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppearanceSection(prefs)
            HiddenSection(vm)
            SettingsSection("Privacy") {
                Text(
                    "No analytics, no ads, no accounts, no trackers. Your music is played from this device only and the app never uses the network. Edits to song info are written to the files on this device.",
                    style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(16.dp),
                )
            }
            val info = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
            Text(
                "Txao Music Player  ·  version ${info.versionName} (build ${info.longVersionCode})",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Box(Modifier.height(24.dp))
        }
    }
}

@Composable
fun EditScreen(vm: PlayerViewModel, tracks: List<Track>, kind: EditKind, back: () -> Unit, onRequestWrite: (List<Uri>, () -> Unit) -> Unit, allFilesAccess: Boolean, onGrantAllFiles: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val single = kind == EditKind.Song
    val showArtist = kind != EditKind.Album
    val showAlbum = kind != EditKind.Artist
    val showYear = kind != EditKind.Artist
    val heading = when (kind) { EditKind.Song -> "Edit info"; EditKind.Album -> "Edit album"; EditKind.Artist -> "Edit artist" }
    fun common(f: (Track) -> String): String = tracks.map(f).distinct().singleOrNull().orEmpty()

    val initTitle = if (single) tracks[0].title else ""
    val initArtist = common { it.artist.takeIf { a -> a != "Unknown artist" }.orEmpty() }
    val initAlbum = common { it.album.takeIf { a -> a != "Unknown album" }.orEmpty() }
    val initYear = common { if (it.year > 0) it.year.toString() else "" }
    val initTrack = if (single && tracks[0].trackNo > 0) tracks[0].trackNo.toString() else ""

    var title by remember { mutableStateOf(initTitle) }
    var artist by remember { mutableStateOf(initArtist) }
    var album by remember { mutableStateOf(initAlbum) }
    var albumArtist by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("") }
    var year by remember { mutableStateOf(initYear) }
    var trackNo by remember { mutableStateOf(initTrack) }
    var initAlbumArtist by remember { mutableStateOf("") }
    var initGenre by remember { mutableStateOf("") }
    var cover by remember { mutableStateOf<Pair<Bitmap, ByteArray>?>(null) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tracks) {
        if (single || kind == EditKind.Album) {
            val (aa, g) = withContext(Dispatchers.IO) { MediaEditor.readExtra(ctx, tracks[0]) }
            albumArtist = aa; initAlbumArtist = aa; genre = g; initGenre = g
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            cover = runCatching {
                val raw = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val bmp = Art.decode(raw, 1000)!!
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                bmp to out.toByteArray()
            }.getOrNull()
            if (cover == null) message = "Couldn't read that image"
        }
    }

    fun changed(now: String, before: String): String? = if (now.trim() != before.trim()) now.trim() else null

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { back() }
            Text(heading, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).clickable { pick.launch("image/*") }) {
                    val picked = cover
                    if (picked != null) Image(picked.first.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Artwork(if (kind == EditKind.Artist) artistArtKey(vm, initArtist, tracks.first().uri) else tracks.first().uri, 120.dp, Modifier.fillMaxSize(), RoundedCornerShape(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxaoButton(if (kind == EditKind.Artist) "Choose picture" else "Choose cover", Icons.Filled.ImageIcon, primary = false) { pick.launch("image/*") }
                    Text(if (kind == EditKind.Artist) "Artist picture, kept in this app." else "Saved inside MP3 files, or kept in this app for other formats.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            if (!single) Text("Changes apply to all ${Library.plural(tracks.size, "song")} here.", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (single) Field("Title", title) { title = it }
            if (showArtist) Field(if (kind == EditKind.Artist) "Artist name" else "Artist", artist) { artist = it }
            if (showAlbum) Field(if (kind == EditKind.Album) "Album title" else "Album", album) { album = it }
            if (showAlbum) Field("Album artist", albumArtist) { albumArtist = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showYear) Field("Year", year, Modifier.weight(1f), number = true) { year = it.filter(Char::isDigit).take(4) }
                if (single) Field("Track no.", trackNo, Modifier.weight(1f), number = true) { trackNo = it.filter(Char::isDigit).take(3) }
            }
            Field("Genre", genre) { genre = it }
            if (!allFilesAccess && android.os.Build.VERSION.SDK_INT >= 30 && tracks.any(MediaEditor::isMp3)) {
                GlassCard(Modifier.fillMaxWidth(), radius = 22.dp) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Android asks for confirmation every time a song file is changed. Allow \"All files access\" once to skip that. It's only used to save your edits, and nothing leaves your device.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant)
                        TxaoButton("Allow once", primary = false, onClick = onGrantAllFiles)
                    }
                }
            }
            message?.let { Text(it, color = if (it.startsWith("Saved")) c.primary else c.error) }
            Text(
                "MP3 files get the new tags written into the file. For other formats (WAV, FLAC, M4A, …) the change is kept in this app and applied on top of your library; the file itself isn't modified.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )
        }
        TxaoButton(if (saving) "Saving…" else "Save", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            if (saving) return@TxaoButton
            val artistPicture = if (kind == EditKind.Artist) cover?.second else null
            val edit = TagEdit(
                title = if (single) changed(title, initTitle)?.takeIf { it.isNotBlank() } else null,
                artist = if (showArtist) changed(artist, initArtist) else null,
                album = if (showAlbum) changed(album, initAlbum) else null,
                albumArtist = if (showAlbum) changed(albumArtist, initAlbumArtist) else null,
                genre = changed(genre, initGenre),
                year = if (showYear) changed(year, initYear)?.let { it.toIntOrNull() ?: 0 } else null,
                trackNo = if (single) changed(trackNo, initTrack)?.let { it.toIntOrNull() ?: 0 } else null,
                // An artist has no cover tag: their picture is kept in the app instead.
                cover = if (kind == EditKind.Artist) null else cover?.second,
            )
            val noTags = edit.title == null && edit.artist == null && edit.album == null && edit.albumArtist == null &&
                edit.genre == null && edit.year == null && edit.trackNo == null && edit.cover == null
            val newArtist = edit.artist?.ifBlank { null } ?: initArtist
            fun finish() {
                if (kind == EditKind.Artist && (artistPicture != null || newArtist != initArtist)) vm.updateArtistImage(initArtist, newArtist, artistPicture)
            }
            if (noTags) { finish(); back(); return@TxaoButton }
            saving = true
            val save = { vm.saveEdits(tracks, edit, onDone = { msg -> saving = false; message = msg; if (msg.startsWith("Saved")) { finish(); back() } }) }
            if (tracks.none(MediaEditor::isMp3)) { save(); return@TxaoButton }
            onRequestWrite(tracks.map { Uri.parse(it.uri) }) { save() }
        }
    }
}

@Composable
private fun Field(label: String, value: String, modifier: Modifier = Modifier.fillMaxWidth(), number: Boolean = false, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text),
    )
}

/** Songs and albums that were hidden, with a way to bring each one back. */
@Composable
private fun HiddenSection(vm: PlayerViewModel) {
    val c = MaterialTheme.colorScheme
    val songs = vm.hiddenSongIds.let { vm.hiddenSongList() }
    val albums = vm.hiddenAlbumKeys.let { vm.hiddenAlbumList() }
    SettingsSection("Hidden from library") {
        if (songs.isEmpty() && albums.isEmpty()) {
            Text("Nothing is hidden. Use Hide song in a song's menu, or Hide album on an album page.", style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
        albums.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(a.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Album · ${a.artist}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { vm.restoreAlbum(a.key) }) { Text("Restore") }
            }
        }
        songs.forEach { t ->
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(t.title, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text("Song · ${t.artist}", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                androidx.compose.material3.TextButton(onClick = { vm.restoreSong(t.id) }) { Text("Restore") }
            }
        }
    }
}

/** Drag the handle of an album up or down to put albums in your own order; saved when you go back. */
@Composable
fun ReorderScreen(vm: PlayerViewModel, back: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val rowHeight = 76.dp
    val rowPx = with(androidx.compose.ui.platform.LocalDensity.current) { rowHeight.toPx() }
    val order = remember {
        androidx.compose.runtime.mutableStateListOf<Album>().apply {
            addAll(Library.sortAlbums(Library.albums(vm.tracks), vm.albumSort, vm.albumOrder))
        }
    }
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    fun done() { vm.setAlbumOrder(order.map { it.key }); back() }
    androidx.activity.compose.BackHandler { done() }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().height(68.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Done") { done() }
            Text("Reorder albums", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Text("Hold the handle and drag. Sorting switches to My order.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(order.toList(), key = { it.key }) { album ->
                val dragging = dragKey == album.key
                Box(
                    Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 5.dp)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .zIndex(if (dragging) 1f else 0f),
                ) {
                    GlassCard(Modifier.fillMaxSize(), radius = 22.dp, tint = if (dragging) c.primary.copy(alpha = 0.2f) else null) {
                        Row(Modifier.fillMaxSize().padding(start = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Artwork(album.tracks.first().uri, 48.dp, Modifier.size(48.dp), RoundedCornerShape(12.dp))
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(album.name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                Text(album.artist, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                            }
                            Box(
                                Modifier.size(52.dp).pointerInput(album.key) {
                                    detectDragGestures(
                                        onDragStart = { dragKey = album.key; dragOffset = 0f },
                                        onDragEnd = { dragKey = null; dragOffset = 0f },
                                        onDragCancel = { dragKey = null; dragOffset = 0f },
                                    ) { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        val from = order.indexOfFirst { it.key == album.key }
                                        val to = (from + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                        if (to != from) {
                                            order.add(to, order.removeAt(from))
                                            // The row moved by whole slots; keep it under the finger.
                                            dragOffset -= (to - from) * rowPx
                                        }
                                    }
                                },
                                contentAlignment = Alignment.Center,
                            ) { androidx.compose.material3.Icon(Icons.Filled.DragHandle, "Drag to reorder", tint = c.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}
