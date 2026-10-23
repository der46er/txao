package com.txao.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import kotlinx.coroutines.delay

private enum class LibTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists") }

@Composable
fun MusicApp(vm: PlayerViewModel, onRetryPermission: () -> Unit) {
    var tab by remember { mutableStateOf(LibTab.Songs) }
    var query by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf<Pair<String, List<Track>>?>(null) }
    var showPlayer by remember { mutableStateOf(false) }

    BackHandler(enabled = showPlayer || detail != null) {
        if (showPlayer) showPlayer = false else detail = null
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Text(
                detail?.first ?: "TXAO MUSIC",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp,
            )
            if (detail == null) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    placeholder = { Text("Search") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
                TabRow(
                    selectedTabIndex = tab.ordinal, containerColor = Color.Transparent,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    LibTab.entries.forEach {
                        Tab(selected = tab == it, onClick = { tab = it }, text = { Text(it.label) })
                    }
                }
            }
            val bottomPad = if (vm.current != null) 84.dp else 0.dp
            when {
                !vm.loaded -> Message("Allow access to your audio files to see your music.", "Grant access", onRetryPermission)
                vm.tracks.isEmpty() -> Message("No music found on this device.", null, {})
                detail != null -> TrackList(detail!!.second, vm, bottomPad)
                else -> {
                    val filtered = remember(vm.tracks, query) { vm.tracks.filter { Library.matches(it, query) } }
                    when (tab) {
                        LibTab.Songs -> TrackList(filtered, vm, bottomPad)
                        LibTab.Albums -> GroupList(
                            remember(filtered) { Library.albums(filtered) }.map { Triple(it.name, "${it.artist} · ${it.tracks.size} songs", it.tracks) },
                            bottomPad,
                        ) { name, list -> detail = name to list }
                        LibTab.Artists -> GroupList(
                            remember(filtered) { Library.artists(filtered) }.map { Triple(it.name, "${it.tracks.size} songs", it.tracks) },
                            bottomPad,
                        ) { name, list -> detail = name to list }
                    }
                }
            }
        }

        vm.current?.let { track ->
            MiniPlayer(vm, track, Modifier.align(Alignment.BottomCenter).navigationBarsPadding()) { showPlayer = true }
        }
        if (showPlayer) NowPlaying(vm) { showPlayer = false }
    }
}

@Composable
private fun Message(text: String, action: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(action) }
    }
}

@Composable
private fun TrackList(list: List<Track>, vm: PlayerViewModel, bottomPad: androidx.compose.ui.unit.Dp) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        items(list, key = { it.id }) { t ->
            var menu by remember { mutableStateOf(false) }
            Row(
                Modifier.fillMaxWidth().clickable { vm.play(list, t) }.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                        color = if (t.id == vm.currentId) MaterialTheme.colorScheme.primary else Color(0xFFE6E6E6),
                    )
                    Text("${t.artist} · ${t.album}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Library.formatDuration(t.durationMs), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Play next") }, onClick = { vm.playNext(t); menu = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupList(
    groups: List<Triple<String, String, List<Track>>>,
    bottomPad: androidx.compose.ui.unit.Dp,
    onOpen: (String, List<Track>) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = bottomPad + 16.dp)) {
        items(groups, key = { it.first + it.third.first().id }) { (name, sub, list) ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(name, list) }.padding(horizontal = 20.dp, vertical = 12.dp)) {
                Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(sub, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, track: Track, modifier: Modifier, onOpen: () -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(12.dp).clickable(onClick = onOpen),
        shape = RoundedCornerShape(20.dp), color = Glass, border = BorderStroke(1.dp, GlassBorder),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm::previous) { Icon(Icons.Default.SkipPrevious, "Previous") }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (vm.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = vm::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0) }
    var dur by remember { mutableLongStateOf(0) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(vm.currentId) {
        while (true) {
            pos = vm.position(); dur = vm.duration()
            delay(500)
        }
    }
    val track = vm.current

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
            Row {
                IconButton(onClick = { showSleep = true }) {
                    Icon(Icons.Default.Bedtime, "Sleep timer", tint = if (vm.sleepEndsAt != null) Color.White else Color(0xFF8A8A8A))
                }
                IconButton(onClick = { showQueue = true }) { Icon(Icons.AutoMirrored.Filled.QueueMusic, "Queue") }
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Glass),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Default.MusicNote, null, Modifier.size(96.dp), tint = Color(0xFF3A3A3A)) }
        Spacer(Modifier.height(28.dp))
        Text(track?.title ?: "", fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(track?.artist ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        val shown = dragging ?: if (dur > 0) pos.toFloat() / dur else 0f
        Slider(
            value = shown.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seekTo((it * dur).toLong()) }; dragging = null },
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Library.formatDuration((shown * dur).toLong()), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Library.formatDuration(dur), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::toggleShuffle) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (vm.shuffle) Color.White else Color(0xFF6A6A6A))
            }
            IconButton(onClick = vm::previous) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
            FilledIconButton(onClick = vm::togglePlay, modifier = Modifier.size(68.dp)) {
                Icon(if (vm.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, if (vm.isPlaying) "Pause" else "Play", Modifier.size(36.dp))
            }
            IconButton(onClick = vm::next) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
            IconButton(onClick = vm::cycleRepeat) {
                Icon(
                    if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat",
                    tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color(0xFF6A6A6A) else Color.White,
                )
            }
        }
        Spacer(Modifier.weight(1f))
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = MaterialTheme.colorScheme.surface) {
        LazyColumn {
            items(vm.queue.size) { i ->
                val t = vm.queue[i]
                Row(Modifier.fillMaxWidth().clickable { vm.skipToQueueItem(i) }.padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (t.id == vm.currentId) FontWeight.Black else FontWeight.Normal)
                        Text(t.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.removeFromQueue(i) }) { Icon(Icons.Default.Close, "Remove") }
                }
            }
        }
    }
    if (showSleep) ModalBottomSheet(onDismissRequest = { showSleep = false }, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(bottom = 24.dp)) {
            listOf(15, 30, 45, 60).forEach { m ->
                Text("$m minutes", Modifier.fillMaxWidth().clickable { vm.setSleepTimer(m); showSleep = false }.padding(20.dp))
            }
            if (vm.sleepEndsAt != null) {
                Text("Turn off timer", Modifier.fillMaxWidth().clickable { vm.setSleepTimer(0); showSleep = false }.padding(20.dp))
            }
        }
    }
}
