package com.txao.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private enum class CallFilter(val label: String) { All("All"), Missed("Missed"), Contacts("Contacts"), NonSpam("Non-spam") }

/**
 * Home tab, laid out like Google Phone: filter chips, a collapsible Favorites row, then calls
 * grouped by day in rounded cards. The search bar lives in MainActivity above this.
 */
@Composable
fun HomeScreen(
    contacts: List<Contact>, recents: List<CallEntry>,
    onCall: (String) -> Unit, onOpen: (String, String) -> Unit, onViewContacts: () -> Unit,
) {
    val ctx = LocalContext.current
    var filter by remember { mutableStateOf(CallFilter.All) }
    var favoritesOpen by remember { mutableStateOf(true) }
    val starred = remember(contacts) { contacts.filter { it.starred }.distinctBy { it.id } }
    val sims = remember(recents) { Data.simLabels(ctx) }
    val shown = remember(recents, filter) {
        when (filter) {
            CallFilter.All -> recents
            CallFilter.Missed -> recents.filter { it.missed }
            CallFilter.Contacts -> recents.filter { !it.name.isNullOrBlank() }
            CallFilter.NonSpam -> recents.filter { !it.blocked }
        }
    }
    val groups = remember(shown) { Data.group(shown) }
    val now = remember(recents) { System.currentTimeMillis() }
    val days = remember(groups) { groups.groupBy { PhoneUtil.dayLabel(now, it.first.date) }.toList() }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(CallFilter.entries.toList()) { f ->
                    FilterChip(filter == f, { filter = f }, { Text(f.label) })
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clickable { favoritesOpen = !favoritesOpen }.padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Favorites", Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Icon(if (favoritesOpen) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (favoritesOpen) "Collapse" else "Expand", tint = Muted)
            }
        }
        if (favoritesOpen) {
            item {
                if (starred.isEmpty()) {
                    Text("Star people from their contact page and they show up here.", Modifier.padding(horizontal = 20.dp, vertical = 6.dp), color = Muted, fontSize = 13.sp)
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(starred, key = { it.id }) { c ->
                        Column(Modifier.width(76.dp).clickable { onCall(c.number) }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(c.name, 60.dp, c.number, c.photoUri)
                            Text(c.name.substringBefore(' '), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    item {
                        Column(Modifier.width(76.dp).clickable(onClick = onViewContacts).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(60.dp).glass(30.dp), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Contacts, null, tint = Lavender) }
                            Text("Contacts", fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
        }
        if (days.isEmpty()) item {
            Empty(when (filter) { CallFilter.Missed -> "No missed calls."; else -> "Your recent calls will show up here." })
        }
        days.forEach { (day, list) ->
            item(key = "h$day") { Text(day, Modifier.padding(start = 24.dp, top = 18.dp, bottom = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Lavender) }
            item(key = "d$day") {
                Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(24.dp).padding(vertical = 4.dp)) {
                    list.forEach { g -> CallRow(g, sims, onCall, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun CallRow(g: CallGroup, sims: Map<String, String>, onCall: (String) -> Unit, onOpen: (String, String) -> Unit) {
    val ctx = LocalContext.current
    val c = g.first
    val count = if (g.count > 1) " (${g.count})" else ""
    val bad = c.missed || c.declined || c.blocked
    val sim = c.accountId?.let(sims::get)?.let { " · $it" }.orEmpty()
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(c.number, c.label) }.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(c.label, 44.dp, c.number, null)
        Column(Modifier.weight(1f)) {
            Text(c.label + count, color = if (bad) Danger else Color.Unspecified, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val arrow = when {
                    c.outgoing -> Icons.AutoMirrored.Filled.CallMade
                    c.missed || c.declined || c.blocked -> Icons.AutoMirrored.Filled.CallMissed
                    else -> Icons.AutoMirrored.Filled.CallReceived
                }
                Icon(arrow, null, Modifier.size(14.dp), tint = if (bad) Danger else Muted)
                Text(" ${c.kind} · ${time(ctx, c.date)}$sim", fontSize = 13.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton({ onCall(c.number) }) { Icon(Icons.Filled.Call, "Call back", tint = Mint) }
    }
}
