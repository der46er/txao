package com.txao.sms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private const val BURST_GAP_MS = 15 * 60_000L
private const val GROUP_GAP_MS = 3 * 60_000L

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: SmsViewModel, screen: Screen.Chat, modifier: Modifier, onBack: () -> Unit, onForward: (String) -> Unit) {
    val context = LocalContext.current
    var address by remember { mutableStateOf(screen.address) }
    var threadId by remember { mutableStateOf(screen.threadId) }
    var text by remember { mutableStateOf(screen.draft) }
    val messages by vm.messages.collectAsState()
    val starred by vm.starredIds.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val muted = conversations.firstOrNull { it.threadId == threadId }?.muted == true

    var selected by remember { mutableStateOf(setOf<Long>()) }
    var menu by remember { mutableStateOf(false) }
    var plusMenu by remember { mutableStateOf(false) }
    var scheduling by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmDeleteMsgs by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<Msg?>(null) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val contact = rememberContactInfo(address.ifEmpty { null })
    val title = contact?.name ?: address.ifEmpty { "New message" }
    val background = rememberContactBackground(address)
    var picked by remember { mutableStateOf(listOf<Uri>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris -> picked = (picked + uris).distinct().take(5) }
    val listState = rememberLazyListState()

    LaunchedEffect(threadId) { vm.open(threadId) }
    DisposableEffect(Unit) { onDispose { vm.open(null) } }
    BackHandler(enabled = selected.isNotEmpty() || searching) { if (selected.isNotEmpty()) selected = emptySet() else { searching = false; query = "" } }

    val shown = remember(messages, searching, query) {
        if (searching && query.isNotBlank()) messages.filter { it.body.contains(query.trim(), ignoreCase = true) } else messages
    }
    // Newest at the bottom; reverseLayout keeps the list pinned there.
    LaunchedEffect(shown.size) { if (shown.isNotEmpty()) listState.animateScrollToItem(0) }

    Box(modifier) {
    if (background != null) {
        // The contact's background from Txao Phone, dimmed so the bubbles stay readable.
        Image(background, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.6f)
        Box(Modifier.fillMaxSize().background(Color(0x99090B10)))
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        when {
            selected.isNotEmpty() -> {
                val chosen = messages.filter { it.id in selected }
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Default.Close, "Cancel") }
                    Text(selected.size.toString(), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    val allStarred = selected.all { it in starred }
                    IconButton(onClick = { vm.toggleStar(selected); selected = emptySet() }) {
                        Icon(if (allStarred) Icons.Default.Star else Icons.Default.StarBorder, "Star", tint = if (allStarred) Lavender else Color.White)
                    }
                    IconButton(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("messages", chosen.sortedBy { it.date }.joinToString("\n") { it.body }))
                        selected = emptySet()
                    }) { Icon(Icons.Default.ContentCopy, "Copy") }
                    if (chosen.size == 1) IconButton(onClick = { onForward(chosen[0].body); selected = emptySet() }) { Icon(Icons.Default.Share, "Forward") }
                    IconButton(onClick = { confirmDeleteMsgs = true }) { Icon(Icons.Default.Delete, "Delete") }
                    if (chosen.size == 1) Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Details") }, onClick = { details = chosen[0]; selected = emptySet(); menu = false })
                            if (chosen[0].failed) DropdownMenuItem(text = { Text("Try again") }, onClick = { vm.resend(chosen[0]); selected = emptySet(); menu = false })
                        }
                    }
                }
            }
            searching -> Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { searching = false; query = "" }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                TextField(query, { query = it }, Modifier.weight(1f), placeholder = { Text("Search in conversation") }, singleLine = true, colors = fieldColors())
            }
            else -> Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                val callable = address.isNotEmpty() && !Numbers.isAlpha(address) && !address.contains(',')
                // Name and picture open the contact page in Txao Phone.
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable(enabled = callable) { PhoneLink.openContact(context, address, contact?.contactId) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(title, 36, address = address.ifEmpty { null })
                    Column(Modifier.padding(start = 10.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (address.isNotEmpty() && title != address) {
                            Text(address, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
                if (callable) {
                    IconButton(onClick = { PhoneLink.call(context, address) }) { Icon(Icons.Default.Call, "Call") }
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Search") }, onClick = { searching = true; menu = false })
                        if (threadId != null) {
                            DropdownMenuItem(text = { Text("Archive") }, onClick = { vm.archive(setOf(threadId!!), true); menu = false; onBack() })
                            DropdownMenuItem(text = { Text(if (muted) "Unmute notifications" else "Mute notifications") }, onClick = { vm.setMuted(threadId!!, !muted); menu = false })
                        }
                        DropdownMenuItem(text = { Text("Block number") }, onClick = { vm.block(address); menu = false })
                        if (threadId != null) DropdownMenuItem(text = { Text("Delete", color = Danger) }, onClick = { confirmDelete = true; menu = false })
                    }
                }
            }
        }
        val ims = contact?.ims.orEmpty()
        if (ims.isNotEmpty() && selected.isEmpty() && !searching) {
            // Usernames saved on the contact in Txao Phone: tap to open the chat there.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ims.forEach { im ->
                    Text(
                        "${im.displayPlatform} · ${im.handle}",
                        Modifier.glassIn(RoundedCornerShape(14.dp)).clickable {
                            if (!PhoneLink.openIm(context, im)) {
                                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("username", im.handle))
                                Toast.makeText(context, "Username copied", Toast.LENGTH_SHORT).show()
                            }
                        }.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontSize = 12.sp, maxLines = 1,
                    )
                }
            }
        }
        if (address.isEmpty()) {
            // Text shared from another app, no recipient yet.
            var to by remember { mutableStateOf("") }
            TextField(
                to, { to = it }, Modifier.fillMaxWidth().padding(horizontal = 12.dp).glass(26.dp), placeholder = { Text("To: number") }, singleLine = true, colors = fieldColors(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                trailingIcon = { TextButton(onClick = { address = Numbers.clean(to); threadId = vm.repo.existingThreadId(address) }, enabled = to.any { it.isDigit() }) { Text("OK") } },
            )
        }

        val reversed = shown.asReversed()
        // The status line sits under the newest outgoing message only.
        val lastId = messages.lastOrNull()?.takeIf { it.outgoing }?.id
        LazyColumn(Modifier.weight(1f), state = listState, reverseLayout = true, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            items(reversed.size, key = { reversed[it].id }) { i ->
                val m = reversed[i]
                val older = reversed.getOrNull(i + 1)
                val newer = reversed.getOrNull(i - 1)
                val groupedWithOlder = older != null && older.outgoing == m.outgoing && m.date - older.date < GROUP_GAP_MS
                val groupedWithNewer = newer != null && newer.outgoing == m.outgoing && newer.date - m.date < GROUP_GAP_MS
                val isSel = m.id in selected
                Column(Modifier.fillMaxWidth().padding(top = if (groupedWithOlder) 2.dp else 10.dp)) {
                    if (older == null || m.date - older.date > BURST_GAP_MS || !sameDay(older.date, m.date)) {
                        Text(burstLabel(context, m.date), Modifier.fillMaxWidth().padding(vertical = 10.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.outgoing) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                        if (!m.outgoing) {
                            // Contact picture next to the last bubble of a run.
                            Box(Modifier.size(26.dp)) { if (!groupedWithNewer) Avatar(title, 26, address = address.ifEmpty { null }) }
                            Box(Modifier.size(6.dp))
                        }
                        val big = 22.dp
                        val small = 6.dp
                        val shape = if (m.outgoing) RoundedCornerShape(big, if (groupedWithOlder) small else big, if (groupedWithNewer) small else big, big)
                        else RoundedCornerShape(if (groupedWithOlder) small else big, big, big, if (groupedWithNewer) small else big)
                        Column(
                            Modifier.widthIn(max = 290.dp)
                                .glassIn(shape, when {
                                    isSel -> Lavender.copy(alpha = 0.5f)
                                    m.outgoing -> Lavender.copy(alpha = 0.30f)
                                    else -> Color(0x1AFFFFFF)
                                })
                                .combinedClickable(
                                    onClick = {
                                        if (selected.isNotEmpty()) selected = if (isSel) selected - m.id else selected + m.id
                                        else if (m.failed) vm.resend(m)
                                        else if (m.attachments.isNotEmpty()) openAttachment(context, m.attachments.first())
                                    },
                                    onLongClick = { selected = if (isSel) selected - m.id else selected + m.id },
                                ),
                        ) {
                            m.attachments.forEach { AttachmentView(it) }
                            val subject = m.subject
                            if (subject != null) Text(subject, Modifier.padding(start = 12.dp, end = 12.dp, top = 7.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            if (m.body.isNotEmpty() || m.id in starred) {
                                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.Bottom) {
                                    Text(m.body, fontSize = 15.sp, modifier = Modifier.weight(1f, fill = false))
                                    if (m.id in starred) Icon(Icons.Default.Star, "Starred", Modifier.padding(start = 6.dp).size(14.dp), tint = Lavender)
                                }
                            }
                        }
                    }
                    val status = when {
                        m.failed -> "Not sent · Tap to retry"
                        m.pending -> "Sending…"
                        m.outgoing && m.id == lastId -> if (m.delivered) "Delivered" else "Sent"
                        else -> null
                    }
                    if (status != null) {
                        Text(status, Modifier.fillMaxWidth().padding(top = 3.dp, end = 6.dp), textAlign = TextAlign.End, fontSize = 11.sp, color = if (m.failed) Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        val info = SmsText.info(text)
        Column(Modifier.navigationBarsPadding().padding(horizontal = 10.dp, vertical = 6.dp)) {
            if (picked.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    picked.forEach { u ->
                        val thumb = rememberImage(u, 160)
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x1FFFFFFF))) {
                            if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                            else Icon(Icons.Default.PlayArrow, null, Modifier.align(Alignment.Center))
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(3.dp).size(20.dp).background(Color(0xCC000000), CircleShape).clickable { picked = picked - u },
                                contentAlignment = Alignment.Center,
                            ) { Icon(Icons.Default.Close, "Remove", Modifier.size(13.dp)) }
                        }
                    }
                }
            }
            if (picked.isEmpty() && (info.parts > 1 || (info.remaining < 20 && text.isNotEmpty()))) {
                Text("${info.remaining} left · ${info.parts} SMS", Modifier.align(Alignment.End).padding(end = 64.dp, bottom = 4.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                val canSend = (text.isNotBlank() || picked.isNotEmpty()) && address.isNotEmpty()
                Box {
                    IconButton(onClick = { plusMenu = true }, Modifier.size(44.dp).glassIn(CircleShape)) { Icon(Icons.Default.Add, "More") }
                    DropdownMenu(expanded = plusMenu, onDismissRequest = { plusMenu = false }) {
                        DropdownMenuItem(text = { Text("Picture or video") }, enabled = picked.size < 5, onClick = {
                            plusMenu = false
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        })
                        DropdownMenuItem(text = { Text("Send later") }, enabled = canSend && picked.isEmpty(), onClick = { plusMenu = false; scheduling = true })
                    }
                }
                TextField(
                    text, { text = it }, Modifier.weight(1f).padding(horizontal = 8.dp).heightIn(min = 44.dp).glass(24.dp, Color(0x1AFFFFFF)),
                    placeholder = { Text(if (picked.isEmpty()) "Text message" else "Add a message") }, maxLines = 6, colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                IconButton(
                    onClick = {
                        if (picked.isNotEmpty()) { vm.sendMms(address, text, picked); picked = emptyList() } else vm.send(address, text)
                        text = ""
                        // The thread now exists; start following it.
                        if (threadId == null) threadId = vm.repo.existingThreadId(address) ?: runCatching { vm.repo.threadIdFor(address) }.getOrNull()
                    },
                    enabled = canSend,
                    modifier = Modifier.size(44.dp).background(if (canSend) Lavender else Color(0x1FFFFFFF), CircleShape),
                ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = if (canSend) OnLavender else Color(0x66FFFFFF)) }
            }
        }
    }
    }

    if (scheduling) ScheduleDialog(onDismiss = { scheduling = false }) { at ->
        vm.schedule(address, text, at); text = ""; scheduling = false
    }
    if (confirmDelete) Confirm("Delete conversation?", "All messages with $title will be removed from this phone.", "Delete", { confirmDelete = false }) {
        threadId?.let(vm::deleteThread); confirmDelete = false; onBack()
    }
    if (confirmDeleteMsgs) Confirm("Delete ${selected.size} message${if (selected.size == 1) "" else "s"}?", "They will be removed from this phone.", "Delete", { confirmDeleteMsgs = false }) {
        vm.deleteMessages(selected); selected = emptySet(); confirmDeleteMsgs = false
    }
    details?.let { m ->
        val info = SmsText.info(m.body)
        AlertDialog(
            onDismissRequest = { details = null },
            title = { Text("Message details") },
            text = {
                Column {
                    Text(if (m.outgoing) "Sent to: ${m.address}" else "From: ${m.address}")
                    Text("Time: ${burstLabel(context, m.date)}")
                    if (m.mms) {
                        Text("Type: Picture message (MMS)")
                        if (m.attachments.isNotEmpty()) Text("Attachments: ${m.attachments.size}")
                    } else {
                        Text("Size: ${info.length} characters, ${info.parts} SMS")
                        Text("Encoding: ${if (info.encoding == SmsText.Encoding.GSM) "GSM-7" else "Unicode"}")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { details = null }) { Text("OK") } },
        )
    }
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

/** Picture, video or audio inside a bubble. Tapping the bubble opens it in the viewer app. */
@Composable
private fun AttachmentView(a: Attachment) {
    val image = if (a.isImage) rememberImage(a.uri, 640) else null
    when {
        image != null -> Image(image, a.label, Modifier.fillMaxWidth().heightIn(max = 280.dp), contentScale = ContentScale.FillWidth)
        a.isImage -> Box(Modifier.size(width = 220.dp, height = 140.dp).background(Color(0x1FFFFFFF)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ImageIcon, a.label)
        }
        else -> Row(Modifier.fillMaxWidth().background(Color(0x1FFFFFFF)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null)
            Text("${a.label}: ${a.name ?: a.type}", Modifier.padding(start = 8.dp), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun openAttachment(context: android.content.Context, a: Attachment) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(a.uri, a.type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(context, "No app can open this", Toast.LENGTH_SHORT).show()
    }
}
