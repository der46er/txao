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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.LaunchedEffect

private val Muted = Color(0xB3FFFFFF)

@Composable
private fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
) {
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
private fun Chip(label: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(GlassCard).border(1.dp, GlassBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SortColumnsMenu(
    expanded: Boolean, onDismiss: () -> Unit,
    sort: SortSpec, columns: Int,
    onSort: (SortSpec) -> Unit, onColumns: (Int) -> Unit,
) {
    DropdownMenu(expanded, onDismiss) {
        for (f in SortField.entries) {
            DropdownMenuItem(
                text = { Text((if (sort.field == f) "● " else "○ ") + "Sort by ${f.name.lowercase()}") },
                onClick = { onSort(sort.copy(field = f)); onDismiss() },
            )
        }
        DropdownMenuItem(
            text = { Text(if (sort.ascending) "Order: oldest / A–Z first" else "Order: newest / Z–A first") },
            onClick = { onSort(sort.copy(ascending = !sort.ascending)); onDismiss() },
        )
        DropdownMenuItem(text = { Text("Fewer columns") }, onClick = { onColumns(columns - 1); onDismiss() })
        DropdownMenuItem(text = { Text("More columns") }, onClick = { onColumns(columns + 1); onDismiss() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumsScreen(
    albums: List<Album>, hiddenAlbums: List<Album>, loaded: Boolean,
    sort: SortSpec, columns: Int, trashSupported: Boolean,
    onSort: (SortSpec) -> Unit, onColumns: (Int) -> Unit,
    onAlbum: (Album) -> Unit, onAll: () -> Unit, onFavorites: () -> Unit, onTrash: () -> Unit, onSearch: () -> Unit,
    onHide: (Album) -> Unit, onUnhide: (Album) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var showHidden by remember { mutableStateOf(false) }
    var albumMenu by remember { mutableStateOf<Album?>(null) }

    Column(Modifier.fillMaxSize().background(GlassBg)) {
        TopBar("Txao Gallery", null) {
            IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search", tint = Color.White) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                SortColumnsMenu(menu, { menu = false }, sort, columns, onSort, onColumns)
            }
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("All", { Icon(Icons.Default.PhotoLibrary, null, tint = Color.White) }, onAll)
            Chip("Favorites", { Icon(Icons.Default.Favorite, null, tint = Color.White) }, onFavorites)
            if (trashSupported) Chip("Trash", { Icon(Icons.Default.Delete, null, tint = Color.White) }, onTrash)
            if (hiddenAlbums.isNotEmpty()) Chip("Hidden", { Icon(Icons.Default.Close, null, tint = Color.White) }, { showHidden = true })
        }
        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (loaded) "No photos or videos found" else "Loading…", color = Muted)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                items(albums, key = { it.id }) { album ->
                    Column(
                        Modifier.combinedClickable(onClick = { onAlbum(album) }, onLongClick = { albumMenu = album }),
                    ) {
                        Box {
                            Thumb(album.cover, 400, Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)))
                            DropdownMenu(albumMenu?.id == album.id, { albumMenu = null }) {
                                DropdownMenuItem(text = { Text("Hide album") }, onClick = { onHide(album); albumMenu = null })
                            }
                        }
                        Text(album.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        Text("${album.items.size} items", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showHidden) {
        AlertDialog(
            onDismissRequest = { showHidden = false },
            confirmButton = { TextButton({ showHidden = false }) { Text("Done") } },
            title = { Text("Hidden albums") },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridScreen(
    title: String, source: Source, base: List<MediaItem>, sort: SortSpec, columns: Int,
    favorites: Set<Long>, onBack: () -> Unit,
    onOpen: (List<MediaItem>, Int) -> Unit,
    onColumns: (Int) -> Unit, onSort: (SortSpec) -> Unit,
    actions: GalleryActions,
) {
    val isTrash = source == Source.Trash
    var query by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var confirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    val ctx = LocalContext.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(source) { if (source == Source.Search) focus.requestFocus() }

    val items = remember(base, sort, query, source) {
        val filtered = if (source == Source.Search) searchItems(base, query) else base
        sortItems(filtered, sort)
    }
    val indexOf = remember(items) { items.withIndex().associate { it.value.id to it.index } }
    val grouped = source != Source.Search && sort.field == SortField.Date
    val sections = remember(items, grouped) {
        if (grouped) groupByDay(items) else listOf("" to items)
    }
    val selecting = selected.isNotEmpty()
    val chosen = items.filter { it.id in selected }
    val px = (1080 / columns).coerceAtLeast(160)

    fun afterAction() { selected = emptySet() }

    Column(Modifier.fillMaxSize().background(GlassBg)) {
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
                    IconButton(onClick = { actions.trash(chosen, false, ::afterAction) }) {
                        Icon(Icons.Default.Restore, "Restore", tint = Color.White)
                    }
                    IconButton(onClick = { actions.deleteForever(chosen, ::afterAction) }) {
                        Icon(Icons.Default.DeleteForever, "Delete forever", tint = Color.White)
                    }
                } else {
                    IconButton(onClick = {
                        val run = { actions.trash(chosen, true, ::afterAction) }
                        if (android.os.Build.VERSION.SDK_INT >= 30) run() else confirm = run
                    }) { Icon(Icons.Default.Delete, "Delete", tint = Color.White) }
                }
            }
        } else if (source == Source.Search) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Search by file or album name", color = Muted)
                        inner()
                    },
                )
            }
        } else {
            TopBar(title, onBack) {
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More", tint = Color.White) }
                    SortColumnsMenu(menu, { menu = false }, sort, columns, onSort, onColumns)
                }
            }
        }

        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when {
                        source == Source.Search && query.isBlank() -> "Type to search"
                        source == Source.Favorites -> "No favorites yet"
                        isTrash -> "Trash is empty"
                        else -> "Nothing here"
                    },
                    color = Muted,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = Modifier.navigationBarsPadding(),
            ) {
                sections.forEach { (label, list) ->
                    if (label.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h-$label") {
                            Text(
                                label, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp, top = 14.dp, bottom = 6.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { item ->
                        Thumb(
                            item, px,
                            Modifier.aspectRatio(1f).combinedClickable(
                                onClick = {
                                    if (selecting) {
                                        selected = if (item.id in selected) selected - item.id else selected + item.id
                                    } else if (isTrash) {
                                        selected = setOf(item.id)
                                    } else {
                                        onOpen(items, indexOf.getValue(item.id))
                                    }
                                },
                                onLongClick = { selected = selected + item.id },
                            ),
                            favorite = item.id in favorites,
                            selected = if (selecting) item.id in selected else null,
                        )
                    }
                }
            }
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
