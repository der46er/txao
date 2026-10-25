package com.txao.sms

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------- first start ----------

@Composable
fun OnboardingScreen(onSetDefault: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(28.dp).navigationBarsPadding(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.size(120.dp))
        Column(Modifier.glass(28.dp).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Txao SMS", fontWeight = FontWeight.Black, fontSize = 28.sp)
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

// ---------- start chat ----------

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NewChatScreen(vm: SmsViewModel, draft: String, modifier: Modifier, onBack: () -> Unit, onPick: (String) -> Unit) {
    val contacts by vm.contacts.collectAsState()
    var to by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.loadContacts() }
    val q = to.trim()
    val shown = remember(contacts, q) {
        val digits = q.filter(Char::isDigit)
        if (q.isEmpty()) contacts else contacts.filter { it.name.contains(q, ignoreCase = true) || (digits.isNotEmpty() && it.number.filter(Char::isDigit).contains(digits)) }
    }
    val grouped = remember(shown) { shown.groupBy { it.name.firstOrNull { c -> c.isLetter() }?.uppercaseChar() ?: '#' }.toSortedMap() }
    Column(modifier.imePadding()) {
        Header(if (draft.isEmpty()) "New conversation" else "Forward to", onBack)
        TextField(
            to, { to = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass(28.dp, androidx.compose.ui.graphics.Color(0x1FFFFFFF)),
            placeholder = { Text("Type a name, phone number") }, singleLine = true, colors = fieldColors(),
            leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            val typed = Numbers.clean(q)
            if (typed.any { it.isDigit() }) {
                item("typed") {
                    Row(Modifier.fillMaxWidth().clickable { onPick(typed) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar("", 44)
                        Text("Send to $typed", Modifier.padding(start = 16.dp), fontWeight = FontWeight.Medium)
                    }
                }
            }
            grouped.forEach { (letter, group) ->
                stickyHeader(key = "h$letter") {
                    Text(letter.toString(), Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Color(0xF20B0E14)).padding(horizontal = 24.dp, vertical = 6.dp), color = Lavender, fontWeight = FontWeight.Bold)
                }
                items(group, key = { it.name + it.number }) { c ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(c.number) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(c.name, 44, address = c.number)
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(c.name, fontSize = 16.sp)
                            Text(c.number, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

// ---------- search ----------

@Composable
fun SearchScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit, onOpen: (Msg) -> Unit, onOpenChat: (Conversation) -> Unit) {
    val results by vm.results.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val context = LocalContext.current
    var q by remember { mutableStateOf("") }
    LaunchedEffect(q) { vm.search(q) }
    Column(modifier.imePadding()) {
        Header("Search", onBack)
        TextField(
            q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass(28.dp, androidx.compose.ui.graphics.Color(0x1FFFFFFF)),
            placeholder = { Text("Search conversations") }, singleLine = true, colors = fieldColors(), leadingIcon = { Icon(Icons.Default.Search, null) },
        )
        // Chats whose contact name or number matches, then single messages whose text matches.
        val chats = remember(q, conversations) {
            val t = q.trim()
            val digits = t.filter { it.isDigit() }
            if (t.length < 2) emptyList()
            else conversations.filter { it.title.contains(t, ignoreCase = true) || (digits.length >= 3 && it.address.filter { c -> c.isDigit() }.contains(digits)) }.take(10)
        }
        if (q.trim().length >= 2 && results.isEmpty() && chats.isEmpty()) EmptyState("No results")
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            if (chats.isNotEmpty()) item("hc") { Text("Conversations", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Lavender) }
            items(chats, key = { "c${it.threadId}" }) { c ->
                Row(Modifier.fillMaxWidth().clickable { onOpenChat(c) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.title, 44, address = c.address)
                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                        Text(c.title, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (c.title != c.address) Text(c.address, fontSize = 13.sp, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (results.isNotEmpty()) item("hm") { Text("Messages", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Lavender) }
            items(results, key = { it.id }) { m -> MessageRow(m, listTime(context, m.date)) { onOpen(m) } }
        }
    }
}

@Composable
private fun MessageRow(m: Msg, time: String, onClick: () -> Unit) {
    val name = rememberContactInfo(m.address)?.name ?: m.address
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(name, 44, address = m.address)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.preview, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(time, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- starred ----------

@Composable
fun StarredScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit, onOpen: (Msg) -> Unit) {
    val starredIds by vm.starredIds.collectAsState()
    val items by vm.starredMessages.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(starredIds) { vm.loadStarred() }
    Column(modifier) {
        Header("Starred", onBack)
        if (items.isEmpty()) EmptyState("Long-press a message and tap the star to keep it here")
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(items, key = { it.id }) { m -> MessageRow(m, listTime(context, m.date)) { onOpen(m) } }
        }
    }
}

// ---------- spam & blocked ----------

@Composable
fun BlockedScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit) {
    val blocked by vm.blocked.collectAsState()
    var adding by remember { mutableStateOf(false) }
    Column(modifier) {
        Header("Spam & blocked", onBack) { TextButton(onClick = { adding = true }) { Text("Add number") } }
        Text(
            "Texts from blocked numbers are dropped silently and never show up here.",
            Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (blocked.isEmpty()) EmptyState("No blocked numbers")
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(blocked, key = { it }) { n ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(rememberContactInfo(n)?.name ?: n, 44, address = n)
                    Text(rememberContactInfo(n)?.name ?: n, Modifier.weight(1f).padding(horizontal = 14.dp))
                    TextButton(onClick = { vm.unblock(n) }) { Text("Unblock") }
                }
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

// ---------- settings ----------

@Composable
fun SettingsScreen(vm: SmsViewModel, modifier: Modifier, onBack: () -> Unit) {
    val scheduled by vm.scheduled.collectAsState()
    val context = LocalContext.current
    var reports by remember { mutableStateOf(vm.store.deliveryReports) }
    var preview by remember { mutableStateOf(vm.store.notificationPreview) }
    Column(modifier) {
        Header("Settings", onBack)
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Notifications", color = Lavender, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                    SwitchRow("Show message text", "Turn off to hide the text in notifications.", preview) { preview = it; vm.store.notificationPreview = it }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Advanced", color = Lavender, fontWeight = FontWeight.Medium, modifier = Modifier.padding(bottom = 6.dp))
                    SwitchRow("Delivery reports", "Show \"Delivered\" under sent messages (your carrier may charge).", reports) { reports = it; vm.store.deliveryReports = it }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().glass().padding(16.dp)) {
                    Text("Scheduled messages", color = Lavender, fontWeight = FontWeight.Medium)
                    if (scheduled.isEmpty()) Text("Nothing scheduled", Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text(
                    "Txao SMS ${runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()}. No analytics, no ads, no accounts. Picture messages (MMS) need mobile data. Contact names, photos, backgrounds and usernames come from Txao Phone and your address book.",
                    Modifier.padding(8.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
