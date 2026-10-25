package com.txao.files

import android.content.Context
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt by lazy { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale.getDefault()) }

private enum class HomeTab { Files, Recents, Storage }

private class Clip(val files: List<File>, val move: Boolean)

@Composable
fun FilesApp(prefs: Prefs, resumeTick: Int, requestAccess: () -> Unit) {
    val ctx = LocalContext.current
    var bgVersion by remember { mutableIntStateOf(0) }
    val pickBg = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.openInputStream(uri)?.use { input -> prefs.backgroundFile.outputStream().use { input.copyTo(it) } }
                bgVersion++
            }
        }
    }
    FilesBackground(prefs, bgVersion, Modifier.fillMaxSize()) {
        val allowed = remember(resumeTick) { MainActivity.hasAccess() }
        if (!allowed) {
            AccessGate(requestAccess)
        } else {
            Browser(
                prefs,
                chooseBackground = { pickBg.launch("image/*") },
                clearBackground = { prefs.backgroundFile.delete(); bgVersion++ },
                hasBackground = prefs.backgroundFile.exists(),
            )
        }
    }
}

@Composable
private fun AccessGate(request: () -> Unit) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Folder, null, Modifier.size(96.dp), tint = FolderTint)
        Spacer(Modifier.height(16.dp))
        Text("Txao Files needs access to all files", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Android asks you to allow this once, in settings. Nothing leaves your phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = request, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentText)) {
            Text("Allow access")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Browser(prefs: Prefs, chooseBackground: () -> Unit, clearBackground: () -> Unit, hasBackground: Boolean) {
    val ctx = LocalContext.current
    val vols = remember { volumes(ctx) }
    val roots = remember(vols) { vols.map { it.root.path } }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Files) }
    var path by rememberSaveable { mutableStateOf(vols[0].root.path) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var clip by remember { mutableStateOf<Clip?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var layoutTick by remember { mutableIntStateOf(0) }
    var showHidden by remember { mutableStateOf(prefs.showHidden) }
    var sortField by remember { mutableStateOf(prefs.sortField) }
    var sortAsc by remember { mutableStateOf(prefs.sortAscending) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }

    val vol = vols.lastOrNull { path == it.root.path || path.startsWith(it.root.path + "/") } ?: vols[0]
    val dir = File(path)
    val layout = remember(path, layoutTick) { prefs.layoutFor(path, roots) }
    val selecting = selected.isNotEmpty()

    val entries by produceState(emptyList<Entry>(), path, refresh, showHidden, sortField, sortAsc) {
        value = withContext(Dispatchers.IO) { listDir(dir, showHidden, sortField, sortAsc) }
    }
    val results by produceState(emptyList<Entry>(), path, query, searching, showHidden, refresh) {
        value = if (searching && query.isNotBlank()) withContext(Dispatchers.IO) { searchIn(dir, query, showHidden) } else emptyList()
    }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun open(e: Entry) {
        if (e.isDir) { path = e.file.path; searching = false; query = ""; selected = emptySet() }
        else if (!openFile(ctx, e.file)) toast("No app can open this file")
    }
    fun closeSearch() { searching = false; query = "" }

    BackHandler(enabled = selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searching) { closeSearch() }
    BackHandler(enabled = !selecting && !searching && tab != HomeTab.Files) { tab = HomeTab.Files }
    BackHandler(enabled = !selecting && !searching && tab == HomeTab.Files && path != vol.root.path) {
        path = dir.parent ?: vol.root.path
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            when (tab) {
                HomeTab.Files -> {
                    TopPill(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (selecting) {
                            SelectionBar(
                                count = selected.size,
                                onClose = { selected = emptySet() },
                                onSelectAll = { selected = (if (searching) results else entries).map { it.file.path }.toSet() },
                                onShare = { shareFiles(ctx, selected.map(::File)); selected = emptySet() },
                                onCopy = { clip = Clip(selected.map(::File), false); selected = emptySet(); toast("Copied. Use + to paste.") },
                                onCut = { clip = Clip(selected.map(::File), true); selected = emptySet(); toast("Cut. Use + to paste.") },
                                onRename = { dialog = Dialog.Rename(File(selected.first())) },
                                onDelete = { dialog = Dialog.Delete(selected.map(::File)) },
                            )
                        } else {
                            BrowseBar(
                                searching = searching, query = query,
                                onQuery = { query = it },
                                onStartSearch = { searching = true },
                                onCloseSearch = ::closeSearch,
                                canGoHome = path != vol.root.path,
                                onHome = { path = vol.root.path; closeSearch() },
                                layout = layout,
                                onToggleLayout = {
                                    prefs.setLayout(path, if (layout == FolderLayout.Grid) FolderLayout.List else FolderLayout.Grid)
                                    layoutTick++
                                },
                                sortField = sortField, sortAsc = sortAsc,
                                onSort = { f, asc ->
                                    sortField = f; sortAsc = asc; prefs.sortField = f; prefs.sortAscending = asc
                                },
                                showHidden = showHidden,
                                onShowHidden = { showHidden = it; prefs.showHidden = it },
                                hasCustomLayout = prefs.hasCustomLayout(path),
                                onResetLayout = { prefs.setLayout(path, null); layoutTick++ },
                                hasBackground = hasBackground,
                                onChooseBackground = chooseBackground,
                                onClearBackground = clearBackground,
                            )
                        }
                    }
                    Crumbs(vols, vol, path, onOpen = { path = it; closeSearch(); selected = emptySet() })
                    val showing = if (searching && query.isNotBlank()) results else entries
                    if (showing.isEmpty()) {
                        Box(Modifier.fillMaxSize().padding(bottom = 80.dp), contentAlignment = Alignment.Center) {
                            Text(
                                if (searching && query.isNotBlank()) "No matches" else "This folder is empty",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (layout == FolderLayout.Grid && !(searching && query.isNotBlank())) {
                        LazyVerticalGrid(
                            GridCells.Adaptive(84.dp),
                            Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            items(showing, key = { it.file.path }) { e ->
                                GridTile(e, e.file.path in selected,
                                    onClick = { if (selecting) selected = toggle(selected, e) else open(e) },
                                    onLong = { selected = toggle(selected, e) })
                            }
                            item { Spacer(Modifier.height(88.dp)) }
                        }
                    } else {
                        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
                            items(showing, key = { it.file.path }) { e ->
                                ListRow(e, e.file.path in selected, subtitleParent = searching && query.isNotBlank(),
                                    onClick = { if (selecting) selected = toggle(selected, e) else open(e) },
                                    onLong = { selected = toggle(selected, e) })
                            }
                            item { Spacer(Modifier.height(88.dp)) }
                        }
                    }
                }
                HomeTab.Recents -> {
                    val recents by produceState(emptyList<Entry>(), vol, showHidden, refresh) {
                        value = withContext(Dispatchers.IO) { recentFiles(vols[0].root, showHidden) }
                    }
                    Text("Recents", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp))
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
                        items(recents, key = { it.file.path }) { e ->
                            ListRow(e, false, subtitleParent = true, onClick = { open(e) }, onLong = {})
                        }
                    }
                }
                HomeTab.Storage -> StorageTab(vols, onOpen = { v -> path = v.root.path; tab = HomeTab.Files })
            }
            NavigationBar(containerColor = NavSurface, contentColor = Color.White) {
                NavigationBarItem(tab == HomeTab.Files, { tab = HomeTab.Files }, { Icon(Icons.Filled.Folder, null) }, label = { Text("Files") }, colors = navColors())
                NavigationBarItem(tab == HomeTab.Recents, { tab = HomeTab.Recents }, { Icon(Icons.Filled.Schedule, null) }, label = { Text("Recents") }, colors = navColors())
                NavigationBarItem(tab == HomeTab.Storage, { tab = HomeTab.Storage }, { Icon(Icons.Filled.Storage, null) }, label = { Text("Storage") }, colors = navColors())
            }
        }

        if (tab == HomeTab.Files && !selecting) {
            var fabMenu by remember { mutableStateOf(false) }
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 18.dp, bottom = 96.dp)) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFF8E9BD0)).clickable { fabMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "New", Modifier.size(32.dp), tint = Color(0xFF1C2140)) }
                DropdownMenu(fabMenu, { fabMenu = false }) {
                    DropdownMenuItem({ Text("New folder") }, { fabMenu = false; dialog = Dialog.NewFolder })
                    DropdownMenuItem({ Text("New file") }, { fabMenu = false; dialog = Dialog.NewFile })
                    clip?.let { c ->
                        DropdownMenuItem({ Text("Paste ${c.files.size} here") }, {
                            fabMenu = false
                            val ok = c.files.count { transfer(it, dir, c.move) }
                            if (c.move) clip = null
                            refresh++
                            toast(if (ok == c.files.size) "Done" else "Some items could not be pasted")
                        })
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        Dialog.NewFolder -> NameDialog("New folder", "", "Create", { dialog = null }) { n ->
            dialog = null
            if (!File(dir, n).mkdirs()) toast("Could not create folder")
            refresh++
        }
        Dialog.NewFile -> NameDialog("New file", "", "Create", { dialog = null }) { n ->
            dialog = null
            if (!runCatching { File(dir, n).createNewFile() }.getOrDefault(false)) toast("Could not create file")
            refresh++
        }
        is Dialog.Rename -> NameDialog("Rename", d.file.name, "Rename", { dialog = null }) { n ->
            dialog = null
            if (!d.file.renameTo(File(d.file.parentFile, n))) toast("Could not rename")
            selected = emptySet(); refresh++
        }
        is Dialog.Delete -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(if (d.files.size == 1) "Delete \"${d.files[0].name}\"?" else "Delete ${d.files.size} items?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton({
                    dialog = null
                    val failed = d.files.count { !it.deleteRecursively() }
                    if (failed > 0) toast("$failed could not be deleted")
                    selected = emptySet(); refresh++
                }) { Text("Delete", color = Color(0xFFFFB4AB)) }
            },
            dismissButton = { TextButton({ dialog = null }) { Text("Cancel") } },
        )
        null -> {}
    }
}

private sealed interface Dialog {
    data object NewFolder : Dialog
    data object NewFile : Dialog
    data class Rename(val file: File) : Dialog
    data class Delete(val files: List<File>) : Dialog
}

private fun toggle(set: Set<String>, e: Entry) = if (e.file.path in set) set - e.file.path else set + e.file.path

@Composable
private fun navColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White,
    selectedTextColor = Color(0xFFB4BEEB),
    indicatorColor = Color(0xFF3A4366),
    unselectedIconColor = Color(0xFFD6D9E8),
    unselectedTextColor = Color(0xFFD6D9E8),
)

@Composable
private fun TopPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(100.dp)).background(BarSurface).padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun BarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick) { Icon(icon, desc, tint = Color.White) }
}

@Composable
private fun RowScope.BrowseBar(
    searching: Boolean, query: String, onQuery: (String) -> Unit, onStartSearch: () -> Unit, onCloseSearch: () -> Unit,
    canGoHome: Boolean, onHome: () -> Unit,
    layout: FolderLayout, onToggleLayout: () -> Unit,
    sortField: SortField, sortAsc: Boolean, onSort: (SortField, Boolean) -> Unit,
    showHidden: Boolean, onShowHidden: (Boolean) -> Unit,
    hasCustomLayout: Boolean, onResetLayout: () -> Unit,
    hasBackground: Boolean, onChooseBackground: () -> Unit, onClearBackground: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    if (searching) {
        IconButton(onCloseSearch) { Icon(Icons.Filled.ArrowBack, "Close search", tint = Color.White) }
    } else {
        Icon(Icons.Filled.Search, "Search", Modifier.padding(start = 12.dp).size(26.dp).clickable(onClick = onStartSearch), tint = Color.White)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }
    BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier.weight(1f).padding(horizontal = 14.dp).focusRequester(focus).let {
            if (searching) it else it.clickable(onClick = onStartSearch)
        },
        enabled = searching,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search", color = Color(0xFF9EA3B8), fontSize = 20.sp)
                inner()
            }
        },
    )
    if (searching) {
        if (query.isNotEmpty()) BarIcon(Icons.Filled.Close, "Clear") { onQuery("") }
        return
    }
    if (canGoHome) BarIcon(Icons.Filled.Home, "Storage root", onHome)
    // Shows the layout this folder would switch to; the choice is remembered for this folder only.
    BarIcon(if (layout == FolderLayout.Grid) Icons.Filled.ViewList else Icons.Filled.GridView,
        if (layout == FolderLayout.Grid) "Show this folder as a list" else "Show this folder as a grid", onToggleLayout)
    Box {
        BarIcon(Icons.AutoMirrored.Filled.Sort, "Sort") { sortMenu = true }
        DropdownMenu(sortMenu, { sortMenu = false }) {
            for (f in SortField.entries) {
                DropdownMenuItem(
                    { Text(f.name) },
                    { onSort(f, if (f == sortField) !sortAsc else true); sortMenu = false },
                    trailingIcon = if (f == sortField) ({ Icon(if (sortAsc) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward, null) }) else null,
                )
            }
        }
    }
    Box {
        BarIcon(Icons.Filled.MoreVert, "More") { moreMenu = true }
        DropdownMenu(moreMenu, { moreMenu = false }) {
            DropdownMenuItem({ Text(if (showHidden) "Hide hidden files" else "Show hidden files") }, { onShowHidden(!showHidden); moreMenu = false })
            if (hasCustomLayout) DropdownMenuItem({ Text("Use default layout here") }, { onResetLayout(); moreMenu = false })
            DropdownMenuItem({ Text("Choose background image") }, { onChooseBackground(); moreMenu = false })
            if (hasBackground) DropdownMenuItem({ Text("Remove background image") }, { onClearBackground(); moreMenu = false })
        }
    }
}

@Composable
private fun RowScope.SelectionBar(
    count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onShare: () -> Unit,
    onCopy: () -> Unit, onCut: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    BarIcon(Icons.Filled.Close, "Cancel selection", onClose)
    Text("$count selected", Modifier.weight(1f), fontSize = 18.sp, maxLines = 1)
    BarIcon(Icons.Filled.Share, "Share", onShare)
    BarIcon(Icons.Filled.Delete, "Delete", onDelete)
    Box {
        BarIcon(Icons.Filled.MoreVert, "More") { menu = true }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Select all") }, { onSelectAll(); menu = false })
            DropdownMenuItem({ Text("Copy") }, { onCopy(); menu = false })
            DropdownMenuItem({ Text("Cut") }, { onCut(); menu = false })
            if (count == 1) DropdownMenuItem({ Text("Rename") }, { onRename(); menu = false })
        }
    }
}

/** Storage chip followed by one clickable segment per folder; the last one is highlighted. */
@Composable
private fun Crumbs(vols: List<Volume>, vol: Volume, path: String, onOpen: (String) -> Unit) {
    var volMenu by remember { mutableStateOf(false) }
    val rel = path.removePrefix(vol.root.path).trim('/').split('/').filter { it.isNotEmpty() }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(100.dp), color = BarSurface, border = BorderStroke(1.dp, Color(0x40FFFFFF)),
                modifier = Modifier.clip(RoundedCornerShape(100.dp)).clickable { if (vols.size > 1) volMenu = true else onOpen(vol.root.path) },
            ) { Text(vol.label, Modifier.padding(horizontal = 18.dp, vertical = 12.dp), fontSize = 20.sp, color = Color.White) }
            DropdownMenu(volMenu, { volMenu = false }) {
                for (v in vols) DropdownMenuItem({ Text(v.label) }, { volMenu = false; onOpen(v.root.path) })
            }
        }
        var acc = vol.root.path
        rel.forEachIndexed { i, seg ->
            acc = "$acc/$seg"
            val target = acc
            Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFFB4BEEB))
            Text(
                seg, fontSize = 20.sp, maxLines = 1,
                color = if (i == rel.lastIndex) Color(0xFFB4BEEB) else Color.White,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(target) }.padding(horizontal = 6.dp, vertical = 8.dp),
            )
        }
    }
}

private fun Modifier.selectedBg(selected: Boolean, radius: Int = 16): Modifier =
    if (selected) clip(RoundedCornerShape(radius.dp)).background(Color(0x55CDB8FF)) else this

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(e: Entry, selected: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Column(
        Modifier.padding(2.dp).selectedBg(selected).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FileGlyph(e.file, e.isDir, 68.dp)
        Spacer(Modifier.height(6.dp))
        Text(e.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(e: Entry, selected: Boolean, subtitleParent: Boolean, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).selectedBg(selected).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileGlyph(e.file, e.isDir, 44.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, fontSize = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (subtitleParent) e.file.parentFile?.name.orEmpty() else if (e.isDir) itemsLabel(e.items) else humanSize(e.size)
            Text(sub, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!e.isDir) {
            Spacer(Modifier.width(8.dp))
            Text(dateFmt.format(Date(e.modified)), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StorageTab(vols: List<Volume>, onOpen: (Volume) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Text("Storage", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 16.dp))
        for (v in vols) {
            val stat = remember(v) { runCatching { StatFs(v.root.path) }.getOrNull() }
            val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
            val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(28.dp)).background(BarSurface)
                    .clickable { onOpen(v) }.padding(20.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Storage, null, tint = FolderTint)
                    Spacer(Modifier.width(12.dp))
                    Text(v.label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
                if (total > 0) {
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(100.dp)),
                        color = Accent, trackColor = Color(0x33FFFFFF),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${humanSize(total - free)} used of ${humanSize(total)} · ${humanSize(free)} free", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun NameDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val valid = text.isNotBlank() && !text.contains('/')
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, singleLine = true) },
        confirmButton = { TextButton({ onOk(text.trim()) }, enabled = valid) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
