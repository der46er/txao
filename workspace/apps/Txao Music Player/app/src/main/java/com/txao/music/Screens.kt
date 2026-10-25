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
fun SettingsScreen(prefs: TxaoPrefs, back: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { back() }
            Text("Settings", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppearanceSection(prefs)
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
fun EditScreen(vm: PlayerViewModel, tracks: List<Track>, back: () -> Unit, onRequestWrite: (List<Uri>, () -> Unit) -> Unit) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val single = tracks.size == 1
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
        if (single) {
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
            Text(if (single) "Edit info" else "Edit ${tracks.size} songs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).clickable { pick.launch("image/*") }) {
                    val picked = cover
                    if (picked != null) Image(picked.first.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Artwork(tracks.first().uri, 120.dp, Modifier.fillMaxSize(), RoundedCornerShape(24.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxaoButton("Choose cover", Icons.Filled.ImageIcon, primary = false) { pick.launch("image/*") }
                    Text("Cover art is saved inside MP3 files.", style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
            }
            if (!single) Text("Only the fields you fill in are changed on all ${tracks.size} songs.", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            if (single) Field("Title", title) { title = it }
            Field("Artist", artist) { artist = it }
            Field("Album", album) { album = it }
            Field("Album artist", albumArtist) { albumArtist = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Field("Year", year, Modifier.weight(1f), number = true) { year = it.filter(Char::isDigit).take(4) }
                if (single) Field("Track no.", trackNo, Modifier.weight(1f), number = true) { trackNo = it.filter(Char::isDigit).take(3) }
            }
            Field("Genre", genre) { genre = it }
            message?.let { Text(it, color = if (it.startsWith("Saved")) c.primary else c.error) }
            Text(
                "MP3 files get the new tags written into the file. For other formats the change is saved in your library only and may revert if the library is rescanned.",
                style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
            )
        }
        TxaoButton(if (saving) "Saving…" else "Save", modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            if (saving) return@TxaoButton
            val edit = TagEdit(
                title = if (single) changed(title, initTitle)?.takeIf { it.isNotBlank() } else null,
                artist = changed(artist, initArtist),
                album = changed(album, initAlbum),
                albumArtist = changed(albumArtist, initAlbumArtist),
                genre = changed(genre, initGenre),
                year = changed(year, initYear)?.let { it.toIntOrNull() ?: 0 },
                trackNo = if (single) changed(trackNo, initTrack)?.let { it.toIntOrNull() ?: 0 } else null,
                cover = cover?.second,
            )
            val nothing = edit.title == null && edit.artist == null && edit.album == null && edit.albumArtist == null &&
                edit.genre == null && edit.year == null && edit.trackNo == null && edit.cover == null
            if (nothing) { back(); return@TxaoButton }
            saving = true
            onRequestWrite(tracks.map { Uri.parse(it.uri) }) {
                vm.saveEdits(tracks, edit, onDone = { msg ->
                    saving = false
                    message = msg
                    if (msg.startsWith("Saved") && !msg.contains("Cover art")) back()
                })
            }
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
