package com.txao.music

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player

private sealed interface Route {
    data object Home : Route
    data class Detail(val title: String, val subtitle: String, val tracks: List<Track>, val round: Boolean) : Route
    data object Player : Route
    data object Settings : Route
    data class Edit(val tracks: List<Track>) : Route
}

private val Tabs = listOf("Songs", "Albums", "Artists")

@Composable
fun MusicApp(
    vm: PlayerViewModel,
    prefs: TxaoPrefs,
    onRequestPermission: () -> Unit,
    onRequestWrite: (List<Uri>, () -> Unit) -> Unit,
    allFilesAccess: Boolean,
    onGrantAllFiles: () -> Unit,
) {
    val stack = remember { mutableStateListOf<Route>(Route.Home) }
    val route = stack.last()
    fun push(r: Route) { stack.add(r) }
    fun pop() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    BackHandler(stack.size > 1) { pop() }

    var tab by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    val songsState = rememberLazyListState()
    val albumsState = rememberLazyGridState()
    val artistsState = rememberLazyListState()

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                (fadeIn(tween(TxaoMotion.ScreenInMs)) + scaleIn(initialScale = TxaoMotion.ScreenInitialScale, animationSpec = TxaoMotion.screenScale)) togetherWith
                    fadeOut(tween(TxaoMotion.ScreenOutMs))
            },
            contentKey = { it::class },
            label = "route",
        ) { r ->
            when (r) {
                Route.Home -> HomeScreen(
                    vm, tab, { tab = it }, query, { query = it }, songsState, albumsState, artistsState,
                    onRequestPermission, ::push, { push(Route.Settings) },
                )
                is Route.Detail -> DetailScreen(vm, r, ::pop) { push(it) }
                Route.Player -> PlayerScreen(vm, ::pop)
                Route.Settings -> SettingsScreen(prefs, ::pop)
                is Route.Edit -> EditScreen(vm, r.tracks, ::pop, onRequestWrite, allFilesAccess, onGrantAllFiles)
            }
        }

        val showMini = vm.current != null && (route is Route.Home || route is Route.Detail)
        AnimatedVisibility(
            showMini, Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) { vm.current?.let { MiniPlayer(vm, it) { push(Route.Player) } } }
    }
}

// Space the list leaves at the bottom so the mini player never covers the last rows.
private val MiniClearance = 108.dp

private fun Modifier.screen() = this.fillMaxSize().systemBarsPadding()

@Composable
private fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
        Text(
            title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = if (onBack != null) 12.dp else 8.dp),
        )
        actions()
    }
}

@Composable
private fun HomeScreen(
    vm: PlayerViewModel, tab: Int, setTab: (Int) -> Unit, query: String, setQuery: (String) -> Unit,
    songsState: LazyListState, albumsState: LazyGridState, artistsState: LazyListState,
    onRequestPermission: () -> Unit, push: (Route) -> Unit, openSettings: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val focus = remember { FocusRequester() }
    val filtered = remember(vm.tracks, query) { vm.tracks.filter { Library.matches(it, query) } }
    val albums = remember(filtered) { Library.albums(filtered) }
    val artists = remember(filtered) { Library.artists(filtered) }

    Column(Modifier.screen()) {
        TopBar("Music") {
            GlassIconButton(Icons.Filled.Search, "Search", tinted = searching) { searching = !searching; if (!searching) setQuery("") }
            GlassIconButton(Icons.Filled.Settings, "Settings") { openSettings() }
        }
        AnimatedVisibility(searching, enter = fadeIn() + androidx.compose.animation.expandVertically(), exit = fadeOut() + androidx.compose.animation.shrinkVertically()) {
            GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), radius = 22.dp) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Icon(Icons.Filled.Search, null, tint = c.onSurfaceVariant)
                    Box(Modifier.weight(1f).padding(start = 12.dp)) {
                        if (query.isEmpty()) Text("Search songs, albums, artists", color = c.onSurfaceVariant)
                        BasicTextField(
                            query, setQuery, singleLine = true, modifier = Modifier.fillMaxWidth().focusRequester(focus),
                            textStyle = TextStyle(color = c.onSurface, fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                            cursorBrush = SolidColor(c.primary),
                        )
                    }
                }
            }
            androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
        TabPills(tab, setTab)

        when {
            !vm.loaded -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Allow access to your audio files to see your music. Nothing leaves this device.", color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                        TxaoButton("Allow access", onClick = onRequestPermission)
                    }
                }
            }
            vm.tracks.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No music found on this device.", color = c.onSurfaceVariant) }
            filtered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Nothing matches your search.", color = c.onSurfaceVariant) }
            else -> when (tab) {
                0 -> LazyColumn(
                    state = songsState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = MiniClearance),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { TxaoButton("Shuffle all · ${filtered.size}", Icons.Filled.Shuffle, Modifier.fillMaxWidth()) { vm.play(filtered, shuffled = true) } }
                    items(filtered, key = { it.id }) { t ->
                        SongRow(t, t.id == vm.currentId, { vm.play(filtered, t) }, { vm.playNext(t) }, { push(Route.Edit(listOf(t))) })
                    }
                }
                1 -> LazyVerticalGrid(
                    GridCells.Fixed(2), state = albumsState,
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = MiniClearance),
                    verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    gridItems(albums, key = { it.id.toString() + it.name }) { a ->
                        AlbumCard(a) { push(Route.Detail(a.name, a.artist, a.tracks, false)) }
                    }
                }
                else -> LazyColumn(
                    state = artistsState, contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = MiniClearance),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(artists, key = { it.name }) { a ->
                        ArtistRow(a) { push(Route.Detail(a.name, "${a.tracks.size} songs", a.tracks, true)) }
                    }
                }
            }
        }
    }
}

/** Songs / Albums / Artists as one glass pill; the chosen tab fills with the lavender tint. */
@Composable
private fun TabPills(selected: Int, onSelect: (Int) -> Unit) {
    val c = MaterialTheme.colorScheme
    GlassCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), radius = 100.dp) {
        Row(Modifier.padding(4.dp)) {
            Tabs.forEachIndexed { i, label ->
                val on = selected == i
                val bg by animateColorAsState(if (on) c.primary.copy(alpha = 0.36f) else Color.Transparent, label = "tab")
                Box(
                    Modifier.weight(1f).height(40.dp).clip(CircleShape).background(bg).clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) { Text(label, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, color = if (on) c.onSurface else c.onSurfaceVariant) }
            }
        }
    }
}

@Composable
private fun DetailScreen(vm: PlayerViewModel, d: Route.Detail, back: () -> Unit, push: (Route) -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(Modifier.screen()) {
        TopBar("", onBack = back) {
            GlassIconButton(Icons.Filled.Edit, "Edit info") { push(Route.Edit(d.tracks)) }
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = MiniClearance),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Artwork(d.tracks.first().uri, 240.dp, Modifier.size(220.dp), if (d.round) CircleShape else RoundedCornerShape(32.dp))
                    Text(d.title, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Text(d.subtitle, color = c.onSurfaceVariant, textAlign = TextAlign.Center)
                    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TxaoButton("Play", Icons.Filled.PlayArrow, Modifier.weight(1f)) { vm.play(d.tracks) }
                        TxaoButton("Shuffle", Icons.Filled.Shuffle, Modifier.weight(1f), primary = false) { vm.play(d.tracks, shuffled = true) }
                    }
                }
            }
            items(d.tracks, key = { it.id }) { t ->
                SongRow(t, t.id == vm.currentId, { vm.play(d.tracks, t) }, { vm.playNext(t) }, { push(Route.Edit(listOf(t))) })
            }
        }
    }
}

@Composable
private fun MiniPlayer(vm: PlayerViewModel, track: Track, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val frac = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
    GlassCard(Modifier.navigationBarsPadding().padding(start = 12.dp, end = 12.dp, bottom = 12.dp).fillMaxWidth(), tint = c.surfaceContainerHigh.copy(alpha = 0.7f)) {
        Column {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Artwork(track.uri, 52.dp, Modifier.size(52.dp), RoundedCornerShape(16.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                }
                GlassIconButton(Icons.Filled.SkipPrevious, "Previous", size = 44.dp) { vm.previous() }
                GlassIconButton(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Play", tinted = true) { vm.togglePlay() }
                GlassIconButton(Icons.Filled.SkipNext, "Next", size = 44.dp) { vm.next() }
            }
            Box(Modifier.fillMaxWidth().height(3.dp)) {
                Box(Modifier.fillMaxWidth(frac).height(3.dp).background(c.primary.copy(alpha = 0.8f)))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerScreen(vm: PlayerViewModel, close: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val track = vm.current
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    val shown = dragging ?: if (vm.durationMs > 0) vm.positionMs.toFloat() / vm.durationMs else 0f

    Column(Modifier.screen().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(60.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Filled.KeyboardArrowDown, "Close") { close() }
            Text("Now playing", Modifier.weight(1f), textAlign = TextAlign.Center, color = c.onSurfaceVariant)
            GlassIconButton(Icons.AutoMirrored.Filled.QueueMusic, "Queue") { showQueue = true }
        }
        Spacer(Modifier.weight(1f))
        Artwork(track?.uri, 360.dp, Modifier.fillMaxWidth().aspectRatio(1f), RoundedCornerShape(36.dp))
        Spacer(Modifier.height(24.dp))
        Text(track?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(track?.artist.orEmpty(), color = c.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(12.dp))
        Slider(
            value = shown.coerceIn(0f, 1f), onValueChange = { dragging = it },
            onValueChangeFinished = { dragging?.let { vm.seekTo((it * vm.durationMs).toLong()) }; dragging = null },
            colors = SliderDefaults.colors(thumbColor = c.primary, activeTrackColor = c.primary, inactiveTrackColor = c.onSurface.copy(alpha = 0.16f)),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(Library.formatDuration((shown * vm.durationMs).toLong()), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
            Text(Library.formatDuration(vm.durationMs), style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Filled.Shuffle, "Shuffle", tinted = vm.shuffle) { vm.toggleShuffle() }
            GlassIconButton(Icons.Filled.SkipPrevious, "Previous", size = 56.dp) { vm.previous() }
            GlassIconButton(if (vm.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, if (vm.isPlaying) "Pause" else "Play", size = 78.dp, tinted = true) { vm.togglePlay() }
            GlassIconButton(Icons.Filled.SkipNext, "Next", size = 56.dp) { vm.next() }
            GlassIconButton(
                if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat, "Repeat",
                tinted = vm.repeatMode != Player.REPEAT_MODE_OFF,
            ) { vm.cycleRepeat() }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.Filled.Bedtime, "Sleep timer", tinted = vm.sleepEndsAt != null) { showSleep = true }
            vm.sleepEndsAt?.let { end ->
                Text("  ${((end - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)} min", color = c.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.weight(1f))
    }

    if (showQueue) ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = c.surfaceContainer) {
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(vm.queue.size) { i ->
                val t = vm.queue[i]
                GlassCard(Modifier.fillMaxWidth(), radius = 20.dp, tint = if (t.id == vm.currentId) c.primary.copy(alpha = 0.16f) else null) {
                    Row(Modifier.fillMaxWidth().clickable { vm.skipToQueueItem(i) }.padding(start = 10.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Artwork(t.uri, 44.dp, Modifier.size(44.dp), RoundedCornerShape(12.dp))
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(t.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
                        }
                        GlassIconButton(Icons.Filled.Close, "Remove", size = 44.dp) { vm.removeFromQueue(i) }
                    }
                }
            }
        }
    }
    if (showSleep) ModalBottomSheet(onDismissRequest = { showSleep = false }, containerColor = c.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { m ->
                TxaoButton("$m minutes", modifier = Modifier.fillMaxWidth(), primary = false) { vm.setSleepTimer(m); showSleep = false }
            }
            if (vm.sleepEndsAt != null) TxaoButton("Turn off timer", modifier = Modifier.fillMaxWidth(), primary = false) { vm.setSleepTimer(0); showSleep = false }
        }
    }
}
