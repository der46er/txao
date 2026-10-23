package com.txao.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Date

// ---------- shared pieces ----------

@Composable
private fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        else Spacer(Modifier.size(12.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
private fun Avatar(label: String, size: Int = 46) {
    val initial = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#"
    Box(Modifier.size(size.dp).clip(CircleShape).background(Lavender.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
        Text(initial, color = Lavender, fontWeight = FontWeight.Bold, fontSize = (size / 2.5).sp)
    }
}

@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color(0x14FFFFFF), unfocusedContainerColor = Color(0x14FFFFFF),
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
)

private fun shortTime(context: Context, ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    return when {
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR) ->
            DateFormat.getTimeFormat(context).format(Date(ms))
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> DateFormat.format("d MMM", ms).toString()
        else -> DateFormat.getDateFormat(context).format(Date(ms))
    }
}

private fun dayLabel(ms: Long): String = DateFormat.format("EEEE, d MMMM yyyy", ms).toString()

private fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

// ---------- onboarding ----------

@Composable
fun OnboardingScreen(onSetDefault: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(28.dp).navigationBarsPadding(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.size(120.dp))
        Column(Modifier.glass(28.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("TXAO SMS", fontWeight = FontWeight.Black, fontSize = 28.sp)
            Spacer(Modifier.size(12.dp))
            Text(
                "To send and receive texts, Txao SMS has to be your default SMS app. Your messages stay on this phone: no accounts, no ads, no analytics.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(20.dp))
            Button(onClick = onSetDefault) { Text("Set as default SMS app") }
        }
    }
}

// ---------- conversation list ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatsScreen(vm: SmsViewModel, modifier: Modifier, onOpen: (Conversation) -> Unit, onNew: () -> Unit, onSearch: () -> Unit, onSettings: () -> Unit) {
    val chats by vm.conversations.collectAsState()
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf<Conversation?>(null) }
    Box(modifier) {
        Column {
            Header("Messages") {
                IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
                IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
            }
            if (chats.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No conversations yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chats, key = { it.threadId }) { c ->
                        Box {
                            Row(
                                Modifier.fillMaxWidth().glass()
                                    .combinedClickable(onClick = { onOpen(c) }, onLongClick = { menuFor = c.threadId })
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(c.title)
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(c.title, fontWeight = if (c.unread > 0) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                        if (c.pinned) Icon(Icons.Default.PushPin, "Pinned", Modifier.padding(start = 6.dp).size(14.dp), tint = Lavender)
                                    }
                                    Text(c.snippet, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (c.unread > 0) FontWeight.SemiBold else FontWeight.Normal)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(shortTime(context, c.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (c.unread > 0) {
                                        Box(Modifier.padding(top = 4.dp).clip(CircleShape).background(Lavender).widthIn(min = 20.dp).padding(horizontal = 6.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
                                            Text(c.unread.toString(), color = OnLavender, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                            DropdownMenu(expanded = menuFor == c.threadId, onDismissRequest = { menuFor = null }) {
                                DropdownMenuItem(text = { Text(if (c.pinned) "Unpin" else "Pin") }, onClick = { vm.togglePin(c.threadId); menuFor = null })
                                DropdownMenuItem(text = { Text("Block number") }, onClick = { vm.block(c.address); menuFor = null })
                                DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { confirmDelete = c; menuFor = null })
                            }
                        }
                    }
                }
            }
        }
        FloatingActionButton(onClick = onNew, modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp), containerColor = Lavender, contentColor = OnLavender) {
            Icon(Icons.Default.Add, "New message")
        }
    }
    confirmDelete?.let { c ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete conversation?") },
            text = { Text("All messages with ${c.title} will be removed from this phone.") },
            confirmButton = { TextButton(onClick = { vm.deleteThread(c.threadId); confirmDelete = null }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

// ---------- new chat ----------

@Composable
fun NewChatScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit, onPick: (String) -> Unit) {
    val contacts by vm.contacts.collectAsState()
    var to by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadContacts() }
    val q = to.trim()
    val shown = remember(contacts, q) {
        if (q.isEmpty()) contacts
        else contacts.filter { it.name.contains(q, ignoreCase = true) || it.number.filter(Char::isDigit).contains(q.filter(Char::isDigit).ifEmpty { "\u0000" }) }
    }
    Column(modifier.imePadding()) {
        Header("New message", onBack)
        TextField(
            to, { to = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp)),
            placeholder = { Text("Name or number") }, singleLine = true, colors = fieldColors(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        )
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val typed = Numbers.clean(q)
            if (typed.isNotEmpty() && typed.any { it.isDigit() }) {
                item("typed") {
                    Row(Modifier.fillMaxWidth().glass().clickable { onPick(typed) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("#", 40)
                        Text("Send to $typed", Modifier.padding(start = 14.dp), fontWeight = FontWeight.Medium)
                    }
                }
            }
            items(shown, key = { it.name + it.number }) { c ->
                Row(Modifier.fillMaxWidth().glass().clickable { onPick(c.number) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.name, 40)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(c.name, fontWeight = FontWeight.Medium)
                        Text(c.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------- search ----------

@Composable
fun SearchScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit, onOpen: (Msg) -> Unit) {
    val results by vm.results.collectAsState()
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    LaunchedEffect(q) { vm.search(q) }
    Column(modifier.imePadding()) {
        Header("Search", onBack)
        TextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp)), placeholder = { Text("Search messages") }, singleLine = true, colors = fieldColors())
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results, key = { it.id }) { m ->
                Column(Modifier.fillMaxWidth().glass().clickable { onOpen(m) }.padding(14.dp)) {
                    val name = vm.repo.displayName(m.address) ?: m.address
                    Text("$name · ${shortTime(context, m.date)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(m.body, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------- one conversation ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: SmsViewModel, screen: Screen.Chat, modifier: Modifier, onBack: () -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(screen.address) }
    var threadId by remember { mutableStateOf(screen.threadId) }
    var text by remember { mutableStateOf(screen.draft) }
    val messages by vm.messages.collectAsState()
    var menuFor by remember { mutableStateOf<Long?>(null) }
    var headerMenu by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val title = vm.repo.displayName(address) ?: address.ifEmpty { "New message" }
    val listState = rememberLazyListState()

    LaunchedEffect(threadId) {
        vm.open(threadId)
    }
    DisposableCloseThread(vm)
    // Newest at the bottom; reverseLayout keeps the list pinned there.
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }

    Column(modifier.imePadding()) {
        Header(title, onBack) {
            if (address.isNotEmpty() && !Numbers.isAlpha(address)) {
                IconButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(address)))) }) { Icon(Icons.Default.Call, "Call") }
            }
            Box {
                IconButton(onClick = { headerMenu = true }) { Icon(Icons.Default.MoreVert, "More") }
                DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                    DropdownMenuItem(text = { Text("Block number") }, onClick = { vm.block(address); headerMenu = false })
                    if (threadId != null) DropdownMenuItem(text = { Text("Delete conversation", color = Danger) }, onClick = { confirmDelete = true; headerMenu = false })
                }
            }
        }
        if (address.isEmpty()) {
            // Shared text with no recipient yet.
            var to by remember { mutableStateOf("") }
            TextField(to, { to = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(18.dp)), placeholder = { Text("To: number") }, singleLine = true, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = { TextButton(onClick = { address = Numbers.clean(to); threadId = vm.repo.existingThreadId(address) }, enabled = to.any { it.isDigit() }) { Text("OK") } })
        }
        LazyColumn(Modifier.weight(1f), state = listState, reverseLayout = true, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val reversed = messages.asReversed()
            items(reversed.size, key = { reversed[it].id }) { i ->
                val m = reversed[i]
                val older = reversed.getOrNull(i + 1)
                Column {
                    if (older == null || !sameDay(older.date, m.date)) {
                        Text(dayLabel(m.date), Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
                        Column(
                            Modifier.widthIn(max = 300.dp)
                                .glass(20.dp, if (m.outgoing) Lavender.copy(alpha = 0.28f) else Color(0x14FFFFFF))
                                .combinedClickable(onClick = {}, onLongClick = { menuFor = m.id })
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                        ) {
                            Text(m.body)
                            val status = when {
                                m.failed -> "Not sent"
                                m.pending -> "Sending…"
                                m.outgoing && m.delivered -> "Delivered"
                                else -> ""
                            }
                            Text(
                                shortTime(context, m.date) + if (status.isNotEmpty()) " · $status" else "",
                                Modifier.align(Alignment.End), fontSize = 11.sp, color = if (m.failed) Danger else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        DropdownMenu(expanded = menuFor == m.id, onDismissRequest = { menuFor = null }) {
                            DropdownMenuItem(text = { Text("Copy") }, onClick = {
                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("message", m.body)); menuFor = null
                            })
                            if (m.failed) DropdownMenuItem(text = { Text("Try again") }, onClick = { vm.resend(m); menuFor = null })
                            DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { vm.deleteMessage(m.id); menuFor = null })
                        }
                    }
                }
            }
        }
        val info = SmsText.info(text)
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (info.parts > 1 || info.remaining < 20 && text.isNotEmpty()) {
                Text("${info.remaining} left · ${info.parts} SMS", Modifier.padding(start = 8.dp, bottom = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                TextField(
                    text, { text = it }, Modifier.weight(1f).clip(RoundedCornerShape(24.dp)), placeholder = { Text("Text message") }, maxLines = 5, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                val canSend = text.isNotBlank() && address.isNotEmpty()
                IconButton(onClick = { scheduling = true }, enabled = canSend) { Icon(Icons.Default.Schedule, "Send later") }
                IconButton(
                    onClick = {
                        vm.send(address, text)
                        text = ""
                        // The thread now exists; start following it.
                        if (threadId == null) threadId = vm.repo.existingThreadId(address) ?: runCatching { vm.repo.threadIdFor(address) }.getOrNull()
                    },
                    enabled = canSend,
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (canSend) Lavender else Color.Gray) }
            }
        }
    }

    if (scheduling) ScheduleDialog(onDismiss = { scheduling = false }) { at ->
        vm.schedule(address, text, at); text = ""; scheduling = false
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete conversation?") },
        text = { Text("All messages with $title will be removed from this phone.") },
        confirmButton = { TextButton(onClick = { threadId?.let(vm::deleteThread); confirmDelete = false; onBack() }) { Text("Delete", color = Danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/** Stops following the open thread when the chat screen leaves composition. */
@Composable
private fun DisposableCloseThread(vm: SmsViewModel) {
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.open(null) } }
}

@Composable
private fun ScheduleDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    fun at(addMinutes: Int) = System.currentTimeMillis() + addMinutes * 60_000L
    fun tomorrow(hour: Int) = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
    }.timeInMillis
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send later") },
        text = {
            Column {
                listOf("In 15 minutes" to at(15), "In 1 hour" to at(60), "In 3 hours" to at(180), "Tomorrow 08:00" to tomorrow(8), "Tomorrow 18:00" to tomorrow(18)).forEach { (label, time) ->
                    TextButton(onClick = { onPick(time) }, Modifier.fillMaxWidth()) { Text(label) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---------- settings ----------

@Composable
fun SettingsScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit) {
    val blocked by vm.blocked.collectAsState()
    val scheduled by vm.scheduled.collectAsState()
    val context = LocalContext.current
    var reports by remember { mutableStateOf(vm.store.deliveryReports) }
    var preview by remember { mutableStateOf(vm.store.notificationPreview) }
    var adding by remember { mutableStateOf(false) }
    Column(modifier) {
        Header("Settings", onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    SwitchRow("Delivery reports", "Show \"Delivered\" under sent messages (carrier may charge).", reports) { reports = it; vm.store.deliveryReports = it }
                    SwitchRow("Show message text in notifications", null, preview) { preview = it; vm.store.notificationPreview = it }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Scheduled", fontWeight = FontWeight.Bold)
                    if (scheduled.isEmpty()) Text("Nothing scheduled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    scheduled.forEach { s ->
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${vm.repo.displayName(s.address) ?: s.address} · ${DateFormat.format("d MMM HH:mm", s.at)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(s.body, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            TextButton(onClick = { vm.cancelScheduled(s) }) { Text("Cancel", color = Danger) }
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Blocked numbers", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                        TextButton(onClick = { adding = true }) { Text("Add") }
                    }
                    if (blocked.isEmpty()) Text("No blocked numbers", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    blocked.forEach { n ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(n, Modifier.weight(1f))
                            TextButton(onClick = { vm.unblock(n) }) { Text("Unblock") }
                        }
                    }
                }
            }
            item {
                Text(
                    "Txao SMS ${BuildConfigVersion.name(context)}. No analytics, no ads, no accounts. MMS (picture messages) is not supported yet.",
                    Modifier.padding(8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (adding) {
        var n by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Block a number") },
            text = { TextField(n, { n = it }, singleLine = true, placeholder = { Text("Phone number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) },
            confirmButton = { TextButton(onClick = { if (n.isNotBlank()) vm.block(n); adding = false }) { Text("Block") } },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

private object BuildConfigVersion {
    fun name(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
}
