package com.txao.files

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFmt by lazy { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

private enum class HomeTab { Files, Recents, Locked }

private class Clip(val files: List<File>, val move: Boolean)

@Composable
fun FilesApp(prefs: Prefs, resumeTick: Int, requestAccess: () -> Unit) {
    val folderColors = remember { FolderColors(prefs) }
    FilesBackground(Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalFolderColors provides folderColors) {
            val allowed = remember(resumeTick) { MainActivity.hasAccess() }
            if (!allowed) AccessGate(requestAccess) else Browser(prefs, folderColors)
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
private fun Browser(prefs: Prefs, folderColors: FolderColors) {
    val ctx = LocalContext.current
    val vols = remember { volumes(ctx) }
    var homePath by remember { mutableStateOf(prefs.homePath) }
    val roots = remember(vols, homePath) { vols.map { it.root.path } + listOfNotNull(homePath.ifEmpty { null }) }
    var tab by rememberSaveable { mutableStateOf(HomeTab.Files) }
    var path by rememberSaveable { mutableStateOf(prefs.homePath.takeIf { it.isNotEmpty() && File(it).isDirectory } ?: vols[0].root.path) }
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
    var master by remember { mutableStateOf<ByteArray?>(null) }
    var afterUnlock by remember { mutableStateOf<(() -> Unit)?>(null) }
    var vaultMode by remember { mutableStateOf(prefs.vaultMode) }
    val scope = rememberCoroutineScope()
    var fadeMs by remember { mutableIntStateOf(prefs.fadeMs) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showNews by remember { mutableStateOf(prefs.lastSeenVersion != appVersion(ctx)) }
    val vaultDir = remember { File(ctx.filesDir, "locked").apply { mkdirs() } }
    LaunchedEffect(tab) {
        if (tab != HomeTab.Locked) master = null
        File(ctx.cacheDir, "vault_open").deleteRecursively()
    }

    val vol = vols.lastOrNull { path == it.root.path || path.startsWith(it.root.path + "/") } ?: vols[0]
    val dir = File(path)
    val layout = remember(path, layoutTick, roots) { prefs.layoutFor(path, roots) }
    val selecting = selected.isNotEmpty()

    var entries by remember { mutableStateOf(emptyList<Entry>()) }
    val results by produceState(emptyList<Entry>(), path, query, searching, showHidden, refresh) {
        value = if (searching && query.isNotBlank()) withContext(Dispatchers.IO) { searchIn(dir, query, showHidden) } else emptyList()
    }

    fun toast(m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()
    fun open(e: Entry) {
        if (e.isDir) { path = e.file.path; searching = false; query = ""; selected = emptySet() }
        else if (!openFile(ctx, e.file)) toast("No app can open this file")
    }
    fun closeSearch() { searching = false; query = "" }
    fun releaseIfIdle() { if (tab != HomeTab.Locked) master = null }
    fun finishUnlock(m: ByteArray) {
        master = m
        scope.launch(Dispatchers.IO) { Vault.migrateLegacy(m, vaultDir) }
        val cb = afterUnlock
        afterUnlock = null
        cb?.invoke()
    }
    /** Runs [then] once the Locked folder is open: asks for the phone lock or the password first. */
    fun unlockVault(then: () -> Unit) {
        if (master != null) { then(); return }
        afterUnlock = then
        if (prefs.vaultMode == "password" && prefs.vaultBlob.isNotEmpty()) { dialog = Dialog.Password; return }
        authenticateWithPhone(
            ctx,
            onOk = {
                val blob = prefs.vaultBlob
                val m = runCatching {
                    if (blob.isEmpty()) VaultKeys.newMaster().also { prefs.vaultBlob = VaultKeys.wrapDevice(it); prefs.vaultMode = "device" }
                    else VaultKeys.unwrapDevice(blob)
                }.getOrNull()
                if (m == null) { afterUnlock = null; toast("Could not unlock") } else finishUnlock(m)
            },
            onFail = { afterUnlock = null; toast(it) },
        )
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && selecting) { selected = emptySet() }
    BackHandler(enabled = !selecting && searching) { closeSearch() }
    BackHandler(enabled = !selecting && !searching && tab != HomeTab.Files) { tab = HomeTab.Files }
    BackHandler(enabled = !selecting && !searching && tab == HomeTab.Files && path != vol.root.path) {
        path = dir.parent ?: vol.root.path
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            when (tab) {
                HomeTab.Files -> {
                    TopPill(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        if (selecting) {
                            SelectionBar(
                                count = selected.size,
                                onClose = { selected = emptySet() },
                                onSelectAll = { selected = (if (searching) results else entries).map { it.file.path }.toSet() },
                                onShare = { shareFiles(ctx, selected.map(::File)); selected = emptySet() },
                                onCopy = { clip = Clip(selected.map(::File), false); selected = emptySet(); toast("Copied. Use + to paste.") },
                                onCut = { clip = Clip(selected.map(::File), true); selected = emptySet(); toast("Cut. Use + to paste.") },
                                onRename = { dialog = Dialog.Rename(File(selected.first())) },
                                canColor = selected.all { File(it).isDirectory },
                                onColor = { dialog = Dialog.Recolor(selected.map(::File)) },
                                onLock = {
                                    val files = selected.map(::File)
                                    unlockVault {
                                        val m = master ?: return@unlockVault
                                        scope.launch {
                                            val ok = withContext(Dispatchers.IO) { files.count { Vault.lock(m, it, vaultDir) } }
                                            toast(if (ok == files.size) "Locked and encrypted" else "Some items could not be locked")
                                            selected = emptySet(); refresh++; releaseIfIdle()
                                        }
                                    }
                                },
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
                                onSettings = { showSettings = true },
                            )
                        }
                    }
                    Crumbs(vols, vol, path, onOpen = { path = it; closeSearch(); selected = emptySet() })
                    val searchActive = searching && query.isNotBlank()
                    if (searchActive) {
                        if (results.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth().padding(bottom = 64.dp), contentAlignment = Alignment.Center) {
                                Text("No matches", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
                                items(results, key = { it.file.path }) { e ->
                                    ListRow(e, e.file.path in selected, subtitleParent = true,
                                        onClick = { if (selecting) selected = toggle(selected, e) else open(e) },
                                        onLong = { selected = toggle(selected, e) })
                                }
                                item { Spacer(Modifier.height(100.dp)) }
                            }
                        }
                    } else {
                        // Each (folder, layout) pair is its own screen with its own state, so moving
                        // between a grid folder and a list folder never mixes their items.
                        AnimatedContent(
                            targetState = path to layout,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            // Pure crossfade. Size changes are snapped: animating them squeezed the incoming
                            // folder into a narrow column while the previous one faded out.
                            transitionSpec = {
                                (fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs))) using SizeTransform(clip = false) { _, _ -> snap() }
                            },
                            label = "folder",
                        ) { (p, l) ->
                            FolderContent(
                                dir = File(p), layout = l, refresh = refresh, showHidden = showHidden,
                                sortField = sortField, sortAsc = sortAsc, selected = selected,
                                onEntries = { if (p == path) entries = it },
                                onClick = { e -> if (selecting) selected = toggle(selected, e) else open(e) },
                                onLong = { e -> selected = toggle(selected, e) },
                            )
                        }
                    }
                }
                HomeTab.Recents -> RecentsTab(vols, showHidden, refresh, onVolume = { v -> path = v.root.path; tab = HomeTab.Files }, onOpen = ::open)
                HomeTab.Locked -> LockedTab(
                    vault = vaultDir, refresh = refresh, unlocked = master != null,
                    onUnlock = { unlockVault { refresh++ } },
                    onOpen = { e ->
                        val m = master
                        if (e.isDir) toast("Long-press a folder to move it out or delete it")
                        else if (m != null) scope.launch {
                            val out = withContext(Dispatchers.IO) {
                                runCatching { File(ctx.cacheDir, "vault_open/" + e.name).also { Vault.decryptTo(m, e.file, it) } }.getOrNull()
                            }
                            if (out == null || !openFile(ctx, out)) toast("Could not open this file")
                        }
                    },
                    onLong = { dialog = Dialog.Vault(it.file) },
                )
            }
            BottomBar(tab) { tab = it }
        }

        if (tab == HomeTab.Files && !selecting) {
            var fabMenu by remember { mutableStateOf(false) }
            Box(Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 92.dp)) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF8E9BD0)).clickable { fabMenu = true },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Add, "New", Modifier.size(26.dp), tint = Color(0xFF1C2140)) }
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

    if (showSettings) {
        SettingsScreen(
            prefs, fadeMs, { fadeMs = it; prefs.fadeMs = it },
            showHidden, { showHidden = it; prefs.showHidden = it },
            vols, homePath, { homePath = it; prefs.homePath = it },
            vaultMode,
            onUsePassword = { unlockVault { dialog = Dialog.SetPassword } },
            onUseDevice = {
                unlockVault {
                    val m = master ?: return@unlockVault
                    val blob = runCatching { VaultKeys.wrapDevice(m) }.getOrNull()
                    if (blob == null) toast("Could not switch") else {
                        prefs.vaultBlob = blob; prefs.vaultSalt = ""; prefs.vaultMode = "device"; vaultMode = "device"
                        toast("Locked now opens with your phone lock")
                    }
                    releaseIfIdle()
                }
            },
            onClose = { showSettings = false },
        )
    }
    if (showNews) {
        WhatsNewDialog(Changelog.releases.take(3)) { showNews = false; prefs.lastSeenVersion = appVersion(ctx) }
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
        is Dialog.Vault -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = { Text(d.file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            confirmButton = {
                TextButton({
                    dialog = null
                    val m = master
                    val out = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (m != null) scope.launch {
                        val ok = withContext(Dispatchers.IO) { Vault.unlockOut(m, d.file, out) }
                        toast(if (ok) "Decrypted to Download" else "Could not move out")
                        refresh++
                    }
                }) { Text("Move to Download") }
            },
            dismissButton = { TextButton({ dialog = Dialog.Delete(listOf(d.file)) }) { Text("Delete", color = Color(0xFFFFB4AB)) } },
        )
        is Dialog.Recolor -> ColorDialog(prefs.palette, onDismiss = { dialog = null }) { argb ->
            d.files.forEach { folderColors.set(it.path, argb) }
            dialog = null; selected = emptySet()
        }
        Dialog.Password -> PasswordDialog(
            onDismiss = { dialog = null; afterUnlock = null },
        ) { pw ->
            scope.launch {
                val m = withContext(Dispatchers.IO) { VaultKeys.unwrapPassword(prefs.vaultBlob, prefs.vaultSalt, pw.toCharArray()) }
                if (m == null) toast("Wrong password") else { dialog = null; finishUnlock(m) }
            }
        }
        Dialog.SetPassword -> SetPasswordDialog(onDismiss = { dialog = null; releaseIfIdle() }) { pw ->
            val m = master
            dialog = null
            if (m != null) scope.launch {
                val (salt, blob) = withContext(Dispatchers.IO) { VaultKeys.wrapPassword(m, pw.toCharArray()) }
                prefs.vaultSalt = salt; prefs.vaultBlob = blob; prefs.vaultMode = "password"; vaultMode = "password"
                toast("Locked now opens with your password")
                releaseIfIdle()
            }
        }
        null -> {}
    }
}

@Composable
private fun FolderContent(
    dir: File, layout: FolderLayout, refresh: Int, showHidden: Boolean, sortField: SortField, sortAsc: Boolean,
    selected: Set<String>, onEntries: (List<Entry>) -> Unit, onClick: (Entry) -> Unit, onLong: (Entry) -> Unit,
) {
    val loaded by produceState<List<Entry>?>(null, dir.path, refresh, showHidden, sortField, sortAsc) {
        value = withContext(Dispatchers.IO) { listDir(dir, showHidden, sortField, sortAsc) }
    }
    val list = loaded
    if (list == null) {
        // Keep the full size while loading so the container never collapses mid-transition.
        Box(Modifier.fillMaxSize())
        return
    }
    LaunchedEffect(list) { onEntries(list) }
    if (list.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(bottom = 64.dp), contentAlignment = Alignment.Center) {
            Text("This folder is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else if (layout == FolderLayout.Grid) {
        LazyVerticalGrid(GridCells.Fixed(4), Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)) {
            items(list, key = { it.file.path }) { e ->
                GridTile(e, e.file.path in selected, Modifier.animateItem(), onClick = { onClick(e) }, onLong = { onLong(e) })
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(list, key = { it.file.path }) { e ->
                ListRow(e, e.file.path in selected, subtitleParent = false, modifier = Modifier.animateItem(), onClick = { onClick(e) }, onLong = { onLong(e) })
            }
            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

private sealed interface Dialog {
    data object NewFolder : Dialog
    data object NewFile : Dialog
    data class Rename(val file: File) : Dialog
    data class Delete(val files: List<File>) : Dialog
    data class Vault(val file: File) : Dialog
    data class Recolor(val files: List<File>) : Dialog
    data object Password : Dialog
    data object SetPassword : Dialog
}

private fun toggle(set: Set<String>, e: Entry) = if (e.file.path in set) set - e.file.path else set + e.file.path

/** Same translucent surface as the search pill, so bar and content read as one piece. */
@Composable
private fun BottomBar(tab: HomeTab, onSelect: (HomeTab) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(100.dp)).background(BarSurface).padding(4.dp),
    ) {
        for ((t, icon, label) in listOf(
            Triple(HomeTab.Files, Icons.Filled.Folder, "Files"),
            Triple(HomeTab.Recents, Icons.Filled.Schedule, "Recents"),
            Triple(HomeTab.Locked, Icons.Filled.Lock, "Locked"),
        )) {
            val on = tab == t
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(100.dp))
                    .background(if (on) Color(0x553A4366) else Color.Transparent)
                    .clickable { onSelect(t) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(icon, null, Modifier.size(22.dp), tint = if (on) Color(0xFFB4BEEB) else Color(0xFFD6D9E8))
                Text(label, fontSize = 11.sp, color = if (on) Color(0xFFB4BEEB) else Color(0xFFD6D9E8))
            }
        }
    }
}

@Composable
private fun TopPill(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(100.dp)).background(BarSurface).padding(horizontal = 4.dp),
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
    onSettings: () -> Unit,
) {
    var sortMenu by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    if (searching) {
        IconButton(onCloseSearch) { Icon(Icons.Filled.ArrowBack, "Close search", tint = Color.White) }
    } else {
        Icon(Icons.Filled.Search, "Search", Modifier.padding(start = 12.dp).size(22.dp).clickable(onClick = onStartSearch), tint = Color.White)
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) runCatching { focus.requestFocus() } }
    BasicTextField(
        value = query, onValueChange = onQuery, singleLine = true,
        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
        cursorBrush = SolidColor(Accent),
        modifier = Modifier.weight(1f).padding(horizontal = 14.dp).focusRequester(focus).let {
            if (searching) it else it.clickable(onClick = onStartSearch)
        },
        enabled = searching,
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) Text("Search", color = Color(0xFF9EA3B8), fontSize = 16.sp)
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
            DropdownMenuItem({ Text("Settings") }, { onSettings(); moreMenu = false })
        }
    }
}

@Composable
private fun RowScope.SelectionBar(
    count: Int, onClose: () -> Unit, onSelectAll: () -> Unit, onShare: () -> Unit,
    onCopy: () -> Unit, onCut: () -> Unit, onRename: () -> Unit, canColor: Boolean, onColor: () -> Unit, onLock: () -> Unit, onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    BarIcon(Icons.Filled.Close, "Cancel selection", onClose)
    Text("$count selected", Modifier.weight(1f), fontSize = 16.sp, maxLines = 1)
    BarIcon(Icons.Filled.Share, "Share", onShare)
    BarIcon(Icons.Filled.Delete, "Delete", onDelete)
    Box {
        BarIcon(Icons.Filled.MoreVert, "More") { menu = true }
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem({ Text("Select all") }, { onSelectAll(); menu = false })
            DropdownMenuItem({ Text("Copy") }, { onCopy(); menu = false })
            DropdownMenuItem({ Text("Cut") }, { onCut(); menu = false })
            if (canColor) DropdownMenuItem({ Text("Folder color") }, { onColor(); menu = false })
            DropdownMenuItem({ Text("Move to Locked") }, { onLock(); menu = false })
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
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Surface(
                shape = RoundedCornerShape(100.dp), color = BarSurface, border = BorderStroke(1.dp, Color(0x40FFFFFF)),
                modifier = Modifier.clip(RoundedCornerShape(100.dp)).clickable { if (vols.size > 1) volMenu = true else onOpen(vol.root.path) },
            ) { Text(vol.label, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), fontSize = 15.sp, color = Color.White) }
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
                seg, fontSize = 15.sp, maxLines = 1,
                color = if (i == rel.lastIndex) Color(0xFFB4BEEB) else Color.White,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onOpen(target) }.padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

private fun Modifier.selectedBg(selected: Boolean, radius: Int = 16): Modifier =
    if (selected) clip(RoundedCornerShape(radius.dp)).background(Color(0x55CDB8FF)) else this

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridTile(e: Entry, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    // The glyph fills most of the column, like Fossify's big flat folders.
    BoxWithConstraints(modifier.padding(1.dp)) {
        val glyph = maxWidth * 0.84f
        Column(
            Modifier.fillMaxWidth().selectedBg(selected).clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLong).padding(vertical = 6.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FileGlyph(e.file, e.isDir, glyph)
            Spacer(Modifier.height(2.dp))
            Text(e.name, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListRow(e: Entry, selected: Boolean, subtitleParent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 8.dp).selectedBg(selected).clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLong).padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileGlyph(e.file, e.isDir, 36.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(e.name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = if (subtitleParent) e.file.parentFile?.name.orEmpty() else if (e.isDir) itemsLabel(e.items) else humanSize(e.size)
            Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!e.isDir) {
            Spacer(Modifier.width(8.dp))
            Text(dateFmt.format(Date(e.modified)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StorageCard(v: Volume, onOpen: () -> Unit) {
    val stat = remember(v) { runCatching { StatFs(v.root.path) }.getOrNull() }
    val total = stat?.let { it.blockCountLong * it.blockSizeLong } ?: 0L
    val free = stat?.let { it.availableBlocksLong * it.blockSizeLong } ?: 0L
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp).clip(RoundedCornerShape(20.dp)).background(BarSurface)
            .clickable(onClick = onOpen).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storage, null, Modifier.size(20.dp), tint = FolderTint)
            Spacer(Modifier.width(10.dp))
            Text(v.label, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
        if (total > 0) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { ((total - free).toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(100.dp)),
                color = Accent, trackColor = Color(0x33FFFFFF),
            )
            Spacer(Modifier.height(6.dp))
            Text("${humanSize(total - free)} used of ${humanSize(total)} · ${humanSize(free)} free", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Private files kept inside the app and behind the device lock (PIN, pattern or biometrics). */
@Composable
private fun ColumnScope.LockedTab(
    vault: File, refresh: Int, unlocked: Boolean, onUnlock: () -> Unit, onOpen: (Entry) -> Unit, onLong: (Entry) -> Unit,
) {
    if (!unlocked) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, Modifier.size(48.dp), tint = FolderTint)
            Spacer(Modifier.height(12.dp))
            Text("Locked", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text("Private files, encrypted on your phone. Select files and choose \"Move to Locked\".",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = AccentText)) { Text("Unlock") }
        }
        return
    }
    val vaultItems by produceState(emptyList<Entry>(), vault, refresh) {
        value = withContext(Dispatchers.IO) { listDir(vault, true, SortField.Name, true) }
    }
    if (vaultItems.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Nothing locked yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(vaultItems, key = { it.file.path }) { e -> ListRow(e, false, subtitleParent = false, onClick = { onOpen(e) }, onLong = { onLong(e) }) }
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

@Composable
private fun SettingsScreen(
    prefs: Prefs, fadeMs: Int, onFade: (Int) -> Unit, showHidden: Boolean, onHidden: (Boolean) -> Unit,
    vols: List<Volume>, homePath: String, onHome: (String) -> Unit,
    vaultMode: String, onUsePassword: () -> Unit, onUseDevice: () -> Unit, onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    var news by remember { mutableStateOf(false) }
    var palette by remember { mutableStateOf(prefs.palette) }
    var newHex by remember { mutableStateOf("") }
    var homeMenu by remember { mutableStateOf(false) }
    val homeChoices = remember { vols.map { it.label to it.root } + listDir(vols[0].root, false, SortField.Name, true).filter { it.isDir }.map { it.name to it.file } }
    fun savePalette(p: List<String>) { palette = p; prefs.palette = p }
    Column(Modifier.fillMaxSize().background(GlassBg).systemBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClose) { Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        SettingsCard("Appearance") {
            Text("Fade between screens: ${"%.1f".format(Locale.US, fadeMs / 1000f)} s", fontSize = 15.sp)
            Slider(
                value = fadeMs / 1000f, onValueChange = { onFade((Math.round(it * 10) * 100).coerceIn(100, 900)) },
                valueRange = 0.1f..0.9f, steps = 7,
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent),
            )
        }
        SettingsCard("Folder colors") {
            Text("Colors offered when you color a folder. The first one is the default.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            palette.forEachIndexed { i, hex ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(Color(Hex.parse(hex) ?: 0)))
                    Text(hex, Modifier.weight(1f).padding(start = 12.dp), fontSize = 15.sp)
                    IconButton({ savePalette(palette.moved(i, -1)) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                    IconButton({ savePalette(palette.moved(i, 1)) }, enabled = i < palette.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                    IconButton({ savePalette(palette.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Filled.Delete, "Remove") }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newHex, { newHex = it }, Modifier.weight(1f), singleLine = true, label = { Text("Hex code, e.g. #FF8800") })
                TextButton(
                    onClick = { Hex.parse(newHex)?.let { savePalette(palette + Hex.format(it)); newHex = "" } },
                    enabled = Hex.parse(newHex) != null && Hex.format(Hex.parse(newHex)!!) !in palette,
                ) { Text("Add") }
            }
            TextButton({ savePalette(DefaultPalette) }) { Text("Reset to the 10 defaults") }
        }
        SettingsCard("Home screen") {
            Text("The Files tab opens in this folder.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box {
                TextButton({ homeMenu = true }) {
                    val label = homeChoices.firstOrNull { it.second.path == (homePath.ifEmpty { vols[0].root.path }) }?.first
                        ?: homePath.substringAfterLast('/')
                    Text(label)
                }
                DropdownMenu(homeMenu, { homeMenu = false }) {
                    for ((label, f) in homeChoices) {
                        DropdownMenuItem({ Text(label) }, { onHome(if (f.path == vols[0].root.path) "" else f.path); homeMenu = false })
                    }
                }
            }
        }
        SettingsCard("Locked folder") {
            Text("Files in Locked are encrypted with AES-256. Choose what unlocks them.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().clickable { if (vaultMode != "device") onUseDevice() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(vaultMode == "device", { if (vaultMode != "device") onUseDevice() })
                Text("Fingerprint or phone PIN / pattern", fontSize = 15.sp)
            }
            Row(Modifier.fillMaxWidth().clickable { if (vaultMode != "password") onUsePassword() }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(vaultMode == "password", { if (vaultMode != "password") onUsePassword() })
                Text("Custom password", fontSize = 15.sp)
            }
            if (vaultMode == "password") TextButton(onUsePassword) { Text("Change password") }
            Text("A forgotten custom password cannot be recovered, and neither can the files.", fontSize = 12.sp, color = Color(0xFFFFB4AB))
        }
        SettingsCard("Files") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show hidden files", Modifier.weight(1f), fontSize = 15.sp)
                Switch(showHidden, onHidden)
            }
        }
        SettingsCard("About") {
            Text("Txao Files ${appVersion(ctx)}", fontSize = 15.sp)
            Text("No analytics, no ads, no accounts, no network access.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton({ news = true }) { Text("What's new") }
        }
    }
    if (news) WhatsNewDialog(Changelog.releases) { news = false }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp).clip(RoundedCornerShape(20.dp)).background(BarSurface).padding(16.dp)) {
        Text(title, fontSize = 13.sp, color = Accent, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun WhatsNewDialog(releases: List<Release>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("What's new") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                for (r in releases) {
                    Text("Version ${r.version}", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, bottom = 2.dp))
                    for (n in r.notes) Text("• $n", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text("OK") } },
    )
}

@Composable
private fun ColumnScope.RecentsTab(vols: List<Volume>, showHidden: Boolean, refresh: Int, onVolume: (Volume) -> Unit, onOpen: (Entry) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val items = remember { mutableStateListOf<Entry>() }
    var offset by remember { mutableIntStateOf(0) }
    var end by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Loads 20 files at a time, so the list shows up at once and fills in as you scroll.
    fun more() {
        if (loading || end) return
        loading = true
        val from = offset
        scope.launch {
            val page = withContext(Dispatchers.IO) { recentPage(ctx, from, 20, showHidden) }
            val known = items.map { it.file.path }.toHashSet()
            items.addAll(page.entries.filter { it.file.path !in known })
            offset = page.nextOffset
            end = page.end
            loading = false
        }
    }
    LaunchedEffect(showHidden, refresh) { items.clear(); offset = 0; end = false; loading = false; more() }
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, items.size, loading) { if (nearEnd) more() }

    LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp)) {
        items(vols, key = { "vol:" + it.root.path }) { v -> StorageCard(v) { onVolume(v) } }
        item { Text("Recent files", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) }
        items(items, key = { it.file.path }) { e -> ListRow(e, false, subtitleParent = true, onClick = { onOpen(e) }, onLong = {}) }
        if (loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp) } }
    }
}

@Composable
private fun ColorDialog(palette: List<String>, onDismiss: () -> Unit, onPick: (Int?) -> Unit) {
    var hex by remember { mutableStateOf("") }
    val custom = Hex.parse(hex)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Folder color") },
        text = {
            Column {
                for (row in palette.chunked(5)) {
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (h in row) {
                            val argb = Hex.parse(h) ?: continue
                            Box(Modifier.size(40.dp).clip(CircleShape).background(Color(argb)).clickable { onPick(argb) })
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(hex, { hex = it }, Modifier.weight(1f), singleLine = true, label = { Text("Custom hex") }, placeholder = { Text("#FF8800") })
                    if (custom != null) Box(Modifier.padding(start = 8.dp).size(28.dp).clip(CircleShape).background(Color(custom)))
                }
            }
        },
        confirmButton = { TextButton({ custom?.let(onPick) }, enabled = custom != null) { Text("Apply") } },
        dismissButton = { TextButton({ onPick(null) }) { Text("Default color") } },
    )
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Locked") },
        text = { OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation()) },
        confirmButton = { TextButton({ onOk(pw) }, enabled = pw.isNotEmpty()) { Text("Unlock") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetPasswordDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid = pw.length >= 4 && pw == again
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Locked password") },
        text = {
            Column {
                Text("If you forget this password, your locked files cannot be recovered.", fontSize = 13.sp, color = Color(0xFFFFB4AB))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("New password (4+ characters)") }, visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(again, { again = it }, singleLine = true, label = { Text("Repeat password") }, visualTransformation = PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton({ onOk(pw) }, enabled = valid) { Text("Use password") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
