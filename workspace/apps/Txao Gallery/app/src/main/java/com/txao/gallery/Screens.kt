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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
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
private val TileShape = RoundedCornerShape(16.dp)

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
    sort: SortSpec, columns: Int, grouping: Grouping,
    onSort: (SortSpec) -> Unit, onColumns: (Int) -> Unit, onGrouping: (Grouping) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
        DropdownMenu(open, { open = false }) {
            for (f in SortField.entries) {
                DropdownMenuItem(
                    text = { Text("Sort by ${f.name.lowercase()}") },
                    trailingIcon = if (sort.field == f) ({ Icon(Icons.Default.Check, null, tint = Accent) }) else null,
                    onClick = { onSort(sort.copy(field = f)); open = false },
                )
            }
            DropdownMenuItem(
                text = { Text(if (sort.ascending) "Order: oldest / A–Z first" else "Order: newest / Z–A first") },
                onClick = { onSort(sort.copy(ascending = !sort.ascending)); open = false },
            )
            for (g in Grouping.entries) {
                DropdownMenuItem(
                    text = { Text((if (grouping == g) "● " else "○ ") + "Group by ${g.label.lowercase()}") },
                    onClick = { onGrouping(g); open = false },
                )
            }
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
    grouping: Grouping = Grouping.Day,
    customAlbum: String? = null,
    header: (@Composable () -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
    topBar: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pickAlbum by remember { mutableStateOf(false) }
    var moreOpen by remember { mutableStateOf(false) }
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
                if (!isTrash) {
                    val allFav = chosen.isNotEmpty() && chosen.all { it.id in favorites }
                    IconButton(onClick = { actions.setFavorite(chosen, !allFav); done() }) {
                        Icon(if (allFav) Icons.Default.FavoriteBorder else Icons.Default.Favorite, "Favorite", tint = Color.White)
                    }
                }
                if (isTrash) {
                    IconButton(onClick = { actions.trash(chosen, false, ::done) }) { Icon(Icons.Default.Restore, "Restore", tint = Color.White) }
                    IconButton(onClick = { actions.deleteForever(chosen, ::done) }) { Icon(Icons.Default.DeleteForever, "Delete forever", tint = Color.White) }
                } else {
                    IconButton(onClick = {
                        val run = { actions.trash(chosen, true, ::done) }
                        if (android.os.Build.VERSION.SDK_INT >= 30) run() else confirm = run
                    }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                }
                Box {
                    IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                    DropdownMenu(moreOpen, { moreOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(if (selected.size == items.size) "Select none" else "Select all") },
                            onClick = { selected = if (selected.size == items.size) emptySet() else items.map { it.id }.toSet(); moreOpen = false },
                        )
                        if (!isTrash) {
                            DropdownMenuItem(text = { Text("Add to album") }, onClick = { pickAlbum = true; moreOpen = false })
                        }
                        if (customAlbum != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from this album") },
                                onClick = { actions.removeFromAlbum(customAlbum, chosen); done(); moreOpen = false },
                            )
                        }
                    }
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
                items = items, grouped = sort.field == SortField.Date, columns = columns, grouping = grouping,
                onSelectGroup = { ids -> selected = if (selected.containsAll(ids)) selected - ids.toSet() else selected + ids },
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

    if (pickAlbum) {
        AlbumPickerDialog(
            names = actions.albumNames(),
            onPick = { name -> actions.addToAlbum(name, chosen); pickAlbum = false; done() },
            onDismiss = { pickAlbum = false },
        )
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
    grouping: Grouping, onGrouping: (Grouping) -> Unit, customAlbums: Map<String, Set<Long>>,
    recents: List<String>, onSearched: (String) -> Unit, onClearRecents: () -> Unit,
) {
    val navPad = 88.dp
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
                        grouping = grouping,
                        onOpen = onOpen, onColumns = onColumns, actions = actions, bottomPadding = navPad,
                        header = if (memories.isNotEmpty()) ({ MemoriesRow(memories) { onOpen(it.items, 0) } }) else null,
                        topBar = { TopBar("Txao Photos", null) { ColumnsSortMenu(sort, columns, grouping, onSort, onColumns, onGrouping) } },
                    )
                }
            }
            Tab.Search -> SearchTab(visible, albums, favorites, columns, onOpen, onColumns, onSource, actions, navPad, recents, onSearched, onClearRecents)
            Tab.Library -> LibraryTab(
                visible, albums, hiddenAlbums, trashedCount, trashSupported, onSource, onHide, onUnhide, navPad,
                customAlbums, actions,
            )
        }

        // Floating glass navigation bar
        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp)
                .clip(RoundedCornerShape(32.dp)).background(Color(0xE60F131C)).border(1.dp, GlassBorder, RoundedCornerShape(32.dp))
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
    recents: List<String>, onSearched: (String) -> Unit, onClearRecents: () -> Unit,
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearched(query) }),
                textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = SolidColor(Color.White), modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Search your photos", color = Muted)
                        inner()
                    }
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
            onOpen = { list, i -> onSearched(query); onOpen(list, i) },
            onColumns = onColumns, actions = actions, bottomPadding = navPad,
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
                if (recents.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Clear", color = Muted, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onClearRecents).padding(4.dp))
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recents) { r -> GlassChip(r, Icons.Default.History) { query = r } }
                    }
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
    customAlbums: Map<String, Set<Long>>, actions: GalleryActions,
) {
    var albumMenu by remember { mutableStateOf<Album?>(null) }
    var customMenu by remember { mutableStateOf<String?>(null) }
    var newAlbum by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    val videos = remember(visible) { visible.count { it.isVideo } }
    val screenshots = remember(visible) { visible.count { isScreenshot(it) } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Library", null) {
            IconButton(onClick = { newAlbum = true }) { Icon(Icons.Default.Add, "New album", tint = Color.White) }
            if (hiddenAlbums.isNotEmpty()) {
                IconButton(onClick = { showHidden = true }) { Icon(Icons.Default.PhotoLibrary, "Hidden folders", tint = Color.White) }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(6),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = navPad),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            val tiles = buildList {
                add(Triple("Favorites", Icons.Default.Favorite, Source.Favorites))
                add(Triple("Videos ($videos)", Icons.Default.Movie, Source.Videos))
                add(Triple("Screenshots ($screenshots)", Icons.Default.Screenshot, Source.Screenshots))
                if (trashSupported) add(Triple("Trash ($trashedCount)", Icons.Default.Delete, Source.Trash))
            }
            items(tiles, key = { it.first }, span = { GridItemSpan(3) }) { (label, icon, source) ->
                Row(
                    Modifier.fillMaxWidth().clip(TileShape).background(GlassCard).border(1.dp, GlassBorder, TileShape)
                        .clickable { onSource(label.substringBefore(" ("), source) }.padding(horizontal = 12.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, null, tint = Accent, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (customAlbums.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("My albums", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                }
                items(customAlbums.keys.sortedBy { it.lowercase() }, key = { "c$it" }, span = { GridItemSpan(2) }) { name ->
                    val members = customAlbums[name] ?: emptySet()
                    val mine = visible.filter { it.id in members }
                    Column(Modifier.combinedClickable(onClick = { onSource(name, Source.Custom(name)) }, onLongClick = { customMenu = name })) {
                        Box {
                            if (mine.isNotEmpty()) {
                                Thumb(mine.maxByOrNull { it.dateMs }!!, 360, Modifier.fillMaxWidth().aspectRatio(1f).clip(TileShape))
                            } else {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(TileShape).background(GlassCard), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Collections, null, tint = Muted)
                                }
                            }
                            DropdownMenu(customMenu == name, { customMenu = null }) {
                                DropdownMenuItem(text = { Text("Delete album") }, onClick = { actions.deleteAlbum(name); customMenu = null })
                            }
                        }
                        Text(name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                        Text("${mine.size} items", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("On this device", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            }
            items(albums, key = { "a${it.id}" }, span = { GridItemSpan(2) }) { album ->
                Column(Modifier.combinedClickable(onClick = { onSource(album.name, Source.OfAlbum(album.id)) }, onLongClick = { albumMenu = album })) {
                    Box {
                        Thumb(album.cover, 360, Modifier.fillMaxWidth().aspectRatio(1f).clip(TileShape))
                        DropdownMenu(albumMenu?.id == album.id, { albumMenu = null }) {
                            DropdownMenuItem(text = { Text("Hide folder") }, onClick = { onHide(album); albumMenu = null })
                        }
                    }
                    Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Text("${album.items.size} items", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }

    if (newAlbum) {
        AlbumPickerDialog(
            names = emptyList(),
            onPick = { name -> actions.addToAlbum(name, emptyList()); newAlbum = false },
            onDismiss = { newAlbum = false },
        )
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
    title: String, base: List<MediaItem>, source: Source, sort: SortSpec, columns: Int, grouping: Grouping,
    favorites: Set<Long>, onBack: () -> Unit, onOpen: (List<MediaItem>, Int) -> Unit,
    onColumns: (Int) -> Unit, onSort: (SortSpec) -> Unit, onGrouping: (Grouping) -> Unit, actions: GalleryActions,
) {
    val items = remember(base, sort) { sortItems(base, sort) }
    val isTrash = source == Source.Trash
    val trashHeader: (@Composable () -> Unit)? = if (isTrash && items.isNotEmpty()) ({
        val soonest = items.mapNotNull { trashDaysLeft(it) }.minOrNull()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (soonest != null) "Items are deleted for good after 30 days. Next removal in $soonest day(s)."
                else "Items are deleted for good after 30 days.",
                color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f),
            )
            TextButton({ actions.deleteForever(items) }) { Text("Empty trash", color = Accent) }
        }
    }) else null
    MediaBrowser(
        items = items, sort = sort, columns = columns, favorites = favorites, isTrash = isTrash,
        grouping = grouping, customAlbum = (source as? Source.Custom)?.name, header = trashHeader,
        onOpen = onOpen, onColumns = onColumns, actions = actions,
        topBar = { TopBar(title, onBack) { ColumnsSortMenu(sort, columns, grouping, onSort, onColumns, onGrouping) } },
    )
}
