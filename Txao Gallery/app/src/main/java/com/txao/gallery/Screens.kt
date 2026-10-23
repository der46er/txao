package com.txao.gallery

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Muted = Color(0xB3FFFFFF)
private val GlassShape = RoundedCornerShape(24.dp)

enum class Tab(val label: String, val icon: ImageVector) {
    Photos("Photos", Icons.Default.Photo),
    Search("Search", Icons.Default.Search),
    Library("Library", Icons.Default.Collections),
}

@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        Text(
            title.uppercase(), color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
        )
        actions()
    }
}

@Composable
fun GlassChip(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(GlassCard).border(1.dp, GlassBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ColumnsSortMenu(
    sort: SortSpec, columns: Int, onSort: (SortSpec) -> Unit, onColumns: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
        DropdownMenu(open, { open = false }) {
            for (f in SortField.entries) {
                DropdownMenuItem(
                    text = { Text((if (sort.field == f) "● " else "○ ") + "Sort by ${f.name.lowercase()}") },
                    onClick = { onSort(sort.copy(field = f)); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text(if (sort.ascending) "Order: oldest / A–Z first" else "Order: newest / Z–A first") },
                onClick = { onSort(sort.copy(ascending = !sort.ascending)); open = false },
            )
            DropdownMenuItem(text = { Text("Larger photos") }, onClick = { onColumns(columns - 1); open = false })
            DropdownMenuItem(text = { Text("Smaller photos") }, onClick = { onColumns(columns + 1); open = false })
        }
    }
}

/** Timeline plus selection mode (share, delete, restore). Shared by Photos, Search results and folder views. */
@Composable
fun MediaBrowser(
    items: List<MediaItem>, sort: SortSpec, columns: Int, favorites: Set<Long>,
    isTrash: Boolean, onOpen: (List<MediaItem>, Int) -> Unit, onColumns: (Int) -> Unit,
    actions: GalleryActions, modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    topBar: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    val selecting = selected.isNotEmpty()
    val chosen = items.filter { it.id in selected }
    fun done() { selected = emptySet() }

    Column(modifier.fillMaxSize()) {
        if (selecting) {
            TopBar("${selected.size} selected", { selected = emptySet() }) {
                IconButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                        type = "*/*"
                        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(chosen.map { it.uri }))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    ctx.startActivity(Intent.createChooser(send, null))
                }) { Icon(Icons.Default.Share, "Share", tint = Color.White) }
                if (isTrash) {
                    IconButton(onClick = { actions.trash(chosen, false, ::done) }) { Icon(Icons.Default.Restore, "Restore", tint = Color.White) }
                    IconButton(onClick = { actions.deleteForever(chosen, ::done) }) { Icon(Icons.Default.DeleteForever, "Delete forever", tint = Color.White) }
                } else {
                    IconButton(onClick = {
                        val run = { actions.trash(chosen, true, ::done) }
                        if (android.os.Build.VERSION.SDK_INT >= 30) run() else confirm = run
                    }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                }
            }
        } else {
            topBar()
        }

        if (items.isEmpty() && header == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (isTrash) "Trash is empty" else "Nothing here", color = Muted)
            }
        } else {
            Timeline(
                items = items, grouped = sort.field == SortField.Date, columns = columns,
                favorites = favorites, selected = selected,
                onClick = { item, index ->
                    when {
                        selecting -> selected = if (item.id in selected) selected - item.id else selected + item.id
                        isTrash -> selected = setOf(item.id)
                        else -> onOpen(items, index)
                    }
                },
                onLongClick = { selected = selected + it.id },
                onColumns = onColumns, header = header, bottomPadding = bottomPadding,
                modifier = Modifier.weight(1f),
            )
        }
    }

    confirm?.let { run ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Delete ${chosen.size} item(s)?") },
            text = { Text("This permanently deletes the files.") },
            confirmButton = { TextButton({ confirm = null; run() }) { Text("Delete") } },
            dismissButton = { TextButton({ confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MemoriesRow(memories: List<Memory>, onOpen: (Memory) -> Unit) {
    Column(Modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text("On this day", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(start = 14.dp, bottom = 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(memories, key = { it.yearsAgo }) { m ->
                Box(Modifier.size(width = 130.dp, height = 180.dp).clip(RoundedCornerShape(20.dp)).clickable { onOpen(m) }) {
                    Thumb(m.items.first(), 400, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        Text(m.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${m.items.size} items", color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    tab: Tab, onTab: (Tab) -> Unit,
    visible: List<MediaItem>, albums: List<Album>, hiddenAlbums: List<Album>, trashedCount: Int,
    loaded: Boolean, favorites: Set<Long>, sort: SortSpec, columns: Int, trashSupported: Boolean,
    onSort: (SortSpec) -> Unit, onColumns: (Int) -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit, onSource: (String, Source) -> Unit,
    onHide: (Album) -> Unit, onUnhide: (Album) -> Unit, actions: GalleryActions,
) {
    val navPad = 96.dp
    Box(Modifier.fillMaxSize()) {
        when (tab) {
            Tab.Photos -> {
                val memories = remember(visible) { onThisDay(visible) }
                val sorted = remember(visible, sort) { sortItems(visible, sort) }
                if (sorted.isEmpty()) {
                    Column(Modifier.fillMaxSize()) {
                        TopBar("Txao Photos", null)
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (loaded) "No photos or videos found" else "Loading…", color = Muted)
                        }
                    }
                } else {
                    MediaBrowser(
                        items = sorted, sort = sort, columns = columns, favorites = favorites, isTrash = false,
                        onOpen = onOpen, onColumns = onColumns, actions = actions, bottomPadding = navPad,
                        header = if (memories.isNotEmpty()) ({ MemoriesRow(memories) { onOpen(it.items, 0) } }) else null,
                        topBar = { TopBar("Txao Photos", null) { ColumnsSortMenu(sort, columns, onSort, onColumns) } },
                    )
                }
            }
            Tab.Search -> SearchTab(visible, albums, favorites, columns, onOpen, onColumns, onSource, actions, navPad)
            Tab.Library -> LibraryTab(
                visible, albums, hiddenAlbums, trashedCount, trashSupported, onSource, onHide, onUnhide, navPad,
            )
        }

        // Floating glass navigation bar
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)
                .clip(RoundedCornerShape(32.dp)).background(Color(0xCC141824)).border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            for (t in Tab.entries) {
                val on = t == tab
                Row(
                    Modifier.clip(RoundedCornerShape(26.dp)).background(if (on) Accent else Color.Transparent)
                        .clickable { onTab(t) }.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(t.icon, t.label, tint = if (on) AccentText else Color.White, modifier = Modifier.size(22.dp))
                    if (on) Text(t.label, color = AccentText, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    visible: List<MediaItem>, albums: List<Album>, favorites: Set<Long>, columns: Int,
    onOpen: (List<MediaItem>, Int) -> Unit, onColumns: (Int) -> Unit,
    onSource: (String, Source) -> Unit, actions: GalleryActions, navPad: androidx.compose.ui.unit.Dp,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(visible, query, favorites) { searchItems(visible, query, favorites) }
    val months = remember(visible) { visible.map { monthLabel(it.dateMs) }.distinct().take(12) }

    @Composable
    fun SearchField() {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(28.dp)).background(GlassCard).border(1.dp, GlassBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Search, null, tint = Muted)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White), modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("Search your photos", color = Muted)
                    inner()
                },
            )
            if (query.isNotEmpty()) {
                Icon(Icons.Default.Close, "Clear", tint = Muted, modifier = Modifier.clickable { query = "" })
            }
        }
    }

    if (query.isNotBlank()) {
        MediaBrowser(
            items = results, sort = SortSpec(), columns = columns, favorites = favorites, isTrash = false,
            onOpen = onOpen, onColumns = onColumns, actions = actions, bottomPadding = navPad,
            topBar = { SearchField() },
        )
    } else {
        Column(Modifier.fillMaxSize()) {
            SearchField()
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Categories", color = Color.White, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassChip("Videos", Icons.Default.Movie) { query = "video" }
                    GlassChip("Screenshots", Icons.Default.Screenshot) { query = "screenshot" }
                    GlassChip("Favorites", Icons.Default.Favorite) { query = "favorite" }
                }
                if (months.isNotEmpty()) {
                    Text("Dates", color = Color.White, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(months) { m -> GlassChip(m) { query = m } }
                    }
                }
                if (albums.isNotEmpty()) {
                    Text("Folders", color = Color.White, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(albums, key = { it.id }) { a -> GlassChip(a.name) { onSource(a.name, Source.OfAlbum(a.id)) } }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibraryTab(
    visible: List<MediaItem>, albums: List<Album>, hiddenAlbums: List<Album>, trashedCount: Int, trashSupported: Boolean,
    onSource: (String, Source) -> Unit, onHide: (Album) -> Unit, onUnhide: (Album) -> Unit, navPad: androidx.compose.ui.unit.Dp,
) {
    var albumMenu by remember { mutableStateOf<Album?>(null) }
    var showHidden by remember { mutableStateOf(false) }
    val videos = remember(visible) { visible.count { it.isVideo } }
    val screenshots = remember(visible) { visible.count { isScreenshot(it) } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Library", null) {
            if (hiddenAlbums.isNotEmpty()) {
                IconButton(onClick = { showHidden = true }) { Icon(Icons.Default.PhotoLibrary, "Hidden folders", tint = Color.White) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = navPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val tiles = buildList {
                add(Triple("Favorites", Icons.Default.Favorite, Source.Favorites))
                add(Triple("Videos ($videos)", Icons.Default.Movie, Source.Videos))
                add(Triple("Screenshots ($screenshots)", Icons.Default.Screenshot, Source.Screenshots))
                if (trashSupported) add(Triple("Trash ($trashedCount)", Icons.Default.Delete, Source.Trash))
            }
            items(tiles, key = { it.first }) { (label, icon, source) ->
                Row(
                    Modifier.fillMaxWidth().clip(GlassShape).background(GlassCard).border(1.dp, GlassBorder, GlassShape)
                        .clickable { onSource(label.substringBefore(" ("), source) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Folders on device", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
            }
            items(albums, key = { "a${it.id}" }) { album ->
                Column(Modifier.combinedClickable(onClick = { onSource(album.name, Source.OfAlbum(album.id)) }, onLongClick = { albumMenu = album })) {
                    Box {
                        Thumb(album.cover, 400, Modifier.fillMaxWidth().aspectRatio(1f).clip(GlassShape))
                        DropdownMenu(albumMenu?.id == album.id, { albumMenu = null }) {
                            DropdownMenuItem(text = { Text("Hide folder") }, onClick = { onHide(album); albumMenu = null })
                        }
                    }
                    Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    Text("${album.items.size} items", color = Muted, fontSize = 12.sp)
                }
            }
        }
    }

    if (showHidden) {
        AlertDialog(
            onDismissRequest = { showHidden = false },
            confirmButton = { TextButton({ showHidden = false }) { Text("Done") } },
            title = { Text("Hidden folders") },
            text = {
                Column {
                    hiddenAlbums.forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("${a.name} (${a.items.size})", Modifier.weight(1f))
                            TextButton({ onUnhide(a) }) { Text("Unhide") }
                        }
                    }
                }
            },
        )
    }
}

@Composable
fun GridScreen(
    title: String, base: List<MediaItem>, source: Source, sort: SortSpec, columns: Int,
    favorites: Set<Long>, onBack: () -> Unit, onOpen: (List<MediaItem>, Int) -> Unit,
    onColumns: (Int) -> Unit, onSort: (SortSpec) -> Unit, actions: GalleryActions,
) {
    val items = remember(base, sort) { sortItems(base, sort) }
    MediaBrowser(
        items = items, sort = sort, columns = columns, favorites = favorites, isTrash = source == Source.Trash,
        onOpen = onOpen, onColumns = onColumns, actions = actions,
        topBar = { TopBar(title, onBack) { ColumnsSortMenu(sort, columns, onSort, onColumns) } },
    )
}
