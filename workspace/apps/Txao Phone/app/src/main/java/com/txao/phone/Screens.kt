package com.txao.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Places the call through Telecom so our own in-call screen handles it; returns false when the call permission is missing. */
fun placeCall(ctx: Context, number: String): Boolean {
    val n = PhoneUtil.normalize(number)
    if (n.isEmpty()) return true
    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return false
    return try {
        ctx.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", n, null), null)
        true
    } catch (_: SecurityException) {
        false
    }
}

fun callVoicemail(ctx: Context) {
    try {
        ctx.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("voicemail", "", null), null)
    } catch (_: SecurityException) {
    }
}

private fun time(ctx: Context, t: Long) = DateFormat.getTimeFormat(ctx).format(t)

@Composable
fun FavoritesScreen(contacts: List<Contact>, recents: List<CallEntry>, onCall: (String) -> Unit, onOpen: (String, String) -> Unit) {
    val starred = contacts.filter { it.starred }.distinctBy { it.id }
    val frequent = Data.frequent(recents, starred.map { PhoneUtil.normalize(it.number) }.toSet())
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        if (starred.isEmpty() && frequent.isEmpty()) {
            item { Empty("Star contacts from their page, and the people you call most show up here.") }
        }
        if (starred.isNotEmpty()) {
            item { SectionHeader("Favorites") }
            item {
                // Fixed-height grid inside the list: three per row.
                val rows = (starred.size + 2) / 3
                LazyVerticalGrid(
                    GridCells.Fixed(3), Modifier.fillMaxWidth().height((rows * 128).dp).padding(horizontal = 12.dp),
                    userScrollEnabled = false,
                ) {
                    items(starred, key = { it.id }) { c ->
                        Column(
                            Modifier.padding(6.dp).glass(20.dp).clickable { onCall(c.number) }.padding(vertical = 14.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Avatar(c.name, 56.dp, c.number, c.photoUri)
                            Text(c.name, fontSize = 13.sp, maxLines = 1, modifier = Modifier.padding(top = 8.dp, start = 6.dp, end = 6.dp))
                        }
                    }
                }
            }
        }
        if (frequent.isNotEmpty()) {
            item { SectionHeader("Frequent") }
            items(frequent, key = { it.id }) { c ->
                PersonRow(c.label, PhoneUtil.pretty(c.number), { onOpen(c.number, c.label) }, number = c.number) {
                    IconButton({ onCall(c.number) }) { Icon(Icons.Filled.Call, "Call", tint = Mint) }
                }
            }
        }
    }
}

@Composable
fun RecentsScreen(recents: List<CallEntry>, onCall: (String) -> Unit, onOpen: (String, String) -> Unit) {
    val ctx = LocalContext.current
    var missedOnly by remember { mutableStateOf(false) }
    val groups = remember(recents, missedOnly) { Data.group(if (missedOnly) recents.filter { it.missed } else recents) }
    val now = remember(recents) { System.currentTimeMillis() }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(!missedOnly, { missedOnly = false }, { Text("All") })
            FilterChip(missedOnly, { missedOnly = true }, { Text("Missed") })
        }
        if (groups.isEmpty()) Empty(if (missedOnly) "No missed calls." else "Your recent calls will show up here.")
        LazyColumn(contentPadding = PaddingValues(bottom = 160.dp)) {
            var lastDay = ""
            groups.forEach { g ->
                val day = PhoneUtil.dayLabel(now, g.first.date)
                if (day != lastDay) { lastDay = day; item(key = "h$day") { SectionHeader(day) } }
                item(key = g.first.id) {
                    val c = g.first
                    val count = if (g.count > 1) " (${g.count})" else ""
                    val arrow = c.kind
                    PersonRow(c.label + count, "$arrow · " + time(ctx, c.date), { onOpen(c.number, c.label) }, if (c.missed) Danger else Color.Unspecified, c.number) {
                        IconButton({ onCall(c.number) }) { Icon(Icons.Filled.Call, "Call back", tint = Mint) }
                    }
                }
            }
        }
    }
}

@Composable
fun ContactsScreen(contacts: List<Contact>, onOpen: (String, String) -> Unit, onNew: () -> Unit) {
    val ctx = LocalContext.current
    val sections = remember(contacts) { contacts.groupBy { PhoneUtil.section(it.name) }.toSortedMap(compareBy { if (it == '#') '[' else it }) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        item {
            Row(
                Modifier.clickable(onClick = onNew)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Lavender), contentAlignment = Alignment.Center) { Icon(Icons.Filled.PersonAdd, null, tint = OnLavender) }
                Text("Create new contact")
            }
        }
        if (contacts.isEmpty()) item { Empty("No contacts with a phone number yet.") }
        sections.forEach { (letter, list) ->
            item(key = "s$letter") { SectionHeader(letter.toString()) }
            items(list, key = { "${it.id}/${it.number}" }) { c ->
                PersonRow(c.name, PhoneUtil.pretty(c.number), { onOpen(c.number, c.name) }, number = c.number, photoUri = c.photoUri)
            }
        }
    }
}

@Composable
fun VoicemailScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).glass(44.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Voicemail, null, Modifier.size(40.dp), tint = Lavender) }
        Spacer(Modifier.height(20.dp))
        Text("Voicemail", fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Calls your carrier's voicemail box.", color = Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 20.dp))
        Button({ callVoicemail(ctx) }) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(8.dp)); Text("Call voicemail") }
    }
}

@Composable
fun SearchResults(query: String, contacts: List<Contact>, recents: List<CallEntry>, onCall: (String) -> Unit, onOpen: (String, String) -> Unit) {
    val digits = PhoneUtil.normalize(query)
    val people = remember(query, contacts) { contacts.filter { it.name.contains(query, true) || (digits.isNotEmpty() && PhoneUtil.normalize(it.number).contains(digits)) }.take(40) }
    val calls = remember(query, recents) { Data.group(recents.filter { it.name?.contains(query, true) == true || (digits.isNotEmpty() && PhoneUtil.normalize(it.number).contains(digits)) }).take(20) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 160.dp)) {
        if (digits.length >= 3) {
            item {
                PersonRow("Call $digits", "", { onCall(digits) }) { Icon(Icons.Filled.Call, null, tint = Mint) }
            }
        }
        if (people.isNotEmpty()) item { SectionHeader("Contacts") }
        items(people, key = { "c${it.id}/${it.number}" }) { c -> PersonRow(c.name, PhoneUtil.pretty(c.number), { onOpen(c.number, c.name) }, number = c.number, photoUri = c.photoUri) }
        if (calls.isNotEmpty()) item { SectionHeader("Recent calls") }
        items(calls, key = { "r${it.first.id}" }) { g -> PersonRow(g.first.label, PhoneUtil.pretty(g.first.number), { onOpen(g.first.number, g.first.label) }, number = g.first.number) }
        if (people.isEmpty() && calls.isEmpty() && digits.length < 3) item { Empty("No results.") }
    }
}

@Composable
fun KeypadScreen(number: String, contacts: List<Contact>, onChange: (String) -> Unit, onCall: () -> Unit, onClose: () -> Unit, onAddContact: (String) -> Unit) {
    val ctx = LocalContext.current
    val matches = remember(number, contacts) { if (number.isEmpty()) emptyList() else contacts.filter { PhoneUtil.matches(number, it.name, it.number) }.take(3) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth()) { IconButton(onClose) { Icon(Icons.Filled.KeyboardArrowDown, "Close keypad") } }
        Column(Modifier.weight(1f).fillMaxWidth()) {
            matches.forEach { c ->
                Row(Modifier.fillMaxWidth().clickable { onChange(PhoneUtil.normalize(c.number)) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Avatar(c.name, 36.dp, c.number, c.photoUri)
                    Text("${c.name} · ${PhoneUtil.pretty(c.number)}", fontSize = 14.sp, color = Muted, maxLines = 1)
                }
            }
        }
        Text(number.ifEmpty { " " }, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Box(Modifier.height(36.dp)) {
            if (number.isNotEmpty() && Data.lookupName(ctx, number) == null) {
                TextButton({ onAddContact(number) }) { Text("Add to contacts") }
            }
        }
        Spacer(Modifier.height(8.dp))
        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (k, sub) ->
                    Column(
                        Modifier.padding(vertical = 6.dp).size(76.dp).glass(38.dp).clickable { onChange(number + k) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        if (sub.isNotEmpty()) Text(sub, fontSize = 9.sp, color = Muted)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.size(48.dp))
            Box(Modifier.size(76.dp).clip(CircleShape).background(Mint).clickable(onClick = onCall), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, "Call", tint = OnMint, modifier = Modifier.size(32.dp))
            }
            IconButton({ onChange(number.dropLast(1)) }, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete") }
        }
    }
}

@Composable
fun BlockedScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var numbers by remember { mutableStateOf(Data.blockedNumbers(ctx)) }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Blocked numbers", fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        if (!Data.canBlock(ctx)) Text("Make Txao Phone your default phone app to manage blocked numbers.", Modifier.padding(20.dp), color = Muted)
        else if (numbers.isEmpty()) Empty("No blocked numbers. Blocked callers never ring.")
        LazyColumn {
            items(numbers) { n ->
                PersonRow(PhoneUtil.pretty(n), "", {}) {
                    TextButton({ Data.unblock(ctx, n); numbers = Data.blockedNumbers(ctx) }) { Text("Unblock") }
                }
            }
        }
    }
}

@Composable
fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(32.dp), color = Muted, textAlign = TextAlign.Center)
}
