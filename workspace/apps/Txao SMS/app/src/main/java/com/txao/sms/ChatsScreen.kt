package com.txao.sms

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** The inbox / archive list: search pill, swipe to archive, long-press to select, drawer, "Start chat" button. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(
    vm: SmsViewModel,
    archivedFolder: Boolean,
    modifier: Modifier,
    onOpen: (Conversation) -> Unit,
    onNew: () -> Unit,
    onSearch: () -> Unit,
    onNavigate: (Screen) -> Unit,
) {
    val all by vm.conversations.collectAsState()
    val context = LocalContext.current
    val list = remember(all, archivedFolder) { all.filter { it.archived == archivedFolder } }
    val inboxUnread = remember(all) { all.filter { !it.archived }.sumOf { it.unread } }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = selected.isEmpty(),
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color(0xF20B0E14)) {
                Text("Txao SMS", Modifier.padding(horizontal = 28.dp, vertical = 24.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                fun go(s: Screen) = scope.launch { drawer.close(); onNavigate(s) }
                val colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = Lavender.copy(alpha = 0.22f), unselectedContainerColor = Color.Transparent,
                    selectedTextColor = Lavender, selectedIconColor = Lavender,
                )
                NavigationDrawerItem(
                    label = { Text("Inbox") }, selected = !archivedFolder, onClick = { go(Screen.Chats(false)) }, colors = colors,
                    icon = { Icon(Icons.Default.Inbox, null) },
                    badge = { if (inboxUnread > 0) Text(inboxUnread.toString(), fontWeight = FontWeight.Bold) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                NavigationDrawerItem(label = { Text("Archived") }, selected = archivedFolder, onClick = { go(Screen.Chats(true)) }, colors = colors,
                    icon = { Icon(Icons.Default.Archive, null) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Starred") }, selected = false, onClick = { go(Screen.Starred) }, colors = colors,
                    icon = { Icon(Icons.Default.Star, null) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Spam & blocked") }, selected = false, onClick = { go(Screen.Blocked) }, colors = colors,
                    icon = { Icon(Icons.Default.Block, null) }, modifier = Modifier.padding(horizontal = 12.dp))
                NavigationDrawerItem(label = { Text("Settings") }, selected = false, onClick = { go(Screen.Settings) }, colors = colors,
                    icon = { Icon(Icons.Default.Settings, null) }, modifier = Modifier.padding(horizontal = 12.dp))
            }
        },
    ) {
        Box(modifier) {
            Column {
                if (selected.isEmpty()) {
                    // Search pill: menu on the left, search prompt, settings on the right.
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(48.dp).glass(24.dp, Color(0x1FFFFFFF)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Default.Menu, "Menu") }
                        Text(
                            if (archivedFolder) "Search archived" else "Search conversations",
                            Modifier.weight(1f).clickable(onClick = onSearch).padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { onNavigate(Screen.Settings) }) { Icon(Icons.Default.Settings, "Settings") }
                    }
                    if (archivedFolder) Text("Archived", Modifier.padding(start = 20.dp, bottom = 4.dp), fontWeight = FontWeight.Medium, color = Lavender)
                } else {
                    val chosen = list.filter { it.threadId in selected }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                        Text(selected.size.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                        IconButton(onClick = { chosen.forEach { vm.togglePin(it.threadId) }; selected = emptySet() }) { Icon(Icons.Default.PushPin, "Pin") }
                        IconButton(onClick = { vm.archive(selected, !archivedFolder); selected = emptySet() }) {
                            Icon(if (archivedFolder) Icons.Default.Unarchive else Icons.Default.Archive, if (archivedFolder) "Unarchive" else "Archive")
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text("Block") }, onClick = { chosen.forEach { vm.block(it.address) }; selected = emptySet(); menu = false })
                                val allMuted = chosen.all { it.muted }
                                DropdownMenuItem(text = { Text(if (allMuted) "Unmute notifications" else "Mute notifications") },
                                    onClick = { chosen.forEach { vm.setMuted(it.threadId, !allMuted) }; selected = emptySet(); menu = false })
                            }
                        }
                    }
                }
                if (list.isEmpty()) {
                    EmptyState(if (archivedFolder) "No archived conversations" else "No conversations yet")
                } else {
                    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
                        items(list, key = { it.threadId }) { c ->
                            val isSel = c.threadId in selected
                            val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                                if (v != SwipeToDismissBoxValue.Settled) { vm.archive(setOf(c.threadId), !archivedFolder); true } else false
                            })
                            SwipeToDismissBox(
                                state = swipe,
                                enableDismissFromStartToEnd = selected.isEmpty(),
                                enableDismissFromEndToStart = selected.isEmpty(),
                                backgroundContent = {
                                    if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) Box(
                                        Modifier.fillMaxSize().background(Lavender.copy(alpha = 0.16f)).padding(horizontal = 28.dp),
                                        contentAlignment = if (swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
                                    ) {
                                        Icon(if (archivedFolder) Icons.Default.Unarchive else Icons.Default.Archive, if (archivedFolder) "Unarchive" else "Archive", tint = Lavender)
                                    }
                                },
                            ) {
                                ChatRow(
                                    c, isSel, listTime(context, c.date),
                                    Modifier.combinedClickable(
                                        onClick = { if (selected.isNotEmpty()) selected = if (isSel) selected - c.threadId else selected + c.threadId else onOpen(c) },
                                        onLongClick = { selected = if (isSel) selected - c.threadId else selected + c.threadId },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
            if (selected.isEmpty() && !archivedFolder) {
                FloatingActionButton(
                    onClick = onNew,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
                    containerColor = Lavender, contentColor = OnLavender,
                ) { Icon(Icons.Default.AddComment, "Start chat") }
            }
        }
    }
    if (confirmDelete) {
        Confirm(
            "Delete ${selected.size} conversation${if (selected.size == 1) "" else "s"}?", "The messages will be removed from this phone.", "Delete",
            onDismiss = { confirmDelete = false },
        ) { selected.forEach(vm::deleteThread); selected = emptySet(); confirmDelete = false }
    }
}

@Composable
private fun ChatRow(c: Conversation, selected: Boolean, time: String, modifier: Modifier) {
    val unread = c.unread > 0
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) Lavender.copy(alpha = 0.18f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(c.title, 44, selected, c.address)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 16.sp,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f, fill = false),
                )
                if (c.pinned) Icon(Icons.Default.PushPin, "Pinned", Modifier.padding(start = 6.dp).size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                c.snippet, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp,
                color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(time, fontSize = 12.sp, fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                color = if (unread) Lavender else MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.muted) Icon(Icons.Default.NotificationsOff, "Muted", Modifier.padding(top = 4.dp).size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            else if (unread) Box(Modifier.padding(top = 6.dp).size(10.dp).background(Lavender, androidx.compose.foundation.shape.CircleShape))
        }
    }
}
