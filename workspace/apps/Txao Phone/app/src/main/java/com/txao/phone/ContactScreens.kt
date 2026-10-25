package com.txao.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.os.Bundle
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun launch(ctx: Context, i: Intent) = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}

fun placeVideoCall(ctx: Context, number: String) {
    val n = PhoneUtil.normalize(number)
    if (n.isEmpty()) return
    try {
        ctx.getSystemService(TelecomManager::class.java).placeCall(
            Uri.fromParts("tel", n, null),
            Bundle().apply { putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, VideoProfile.STATE_BIDIRECTIONAL) },
        )
    } catch (_: SecurityException) {
    }
}

fun imIcon(p: ImPlatform): Int = when (p) {
    ImPlatform.WhatsApp -> R.drawable.ic_im_whatsapp; ImPlatform.Telegram -> R.drawable.ic_im_telegram
    ImPlatform.Signal -> R.drawable.ic_im_signal; ImPlatform.Messenger -> R.drawable.ic_im_messenger
    ImPlatform.Instagram -> R.drawable.ic_im_instagram; ImPlatform.Discord -> R.drawable.ic_im_discord
    ImPlatform.Snapchat -> R.drawable.ic_im_snapchat; ImPlatform.X -> R.drawable.ic_im_x
    ImPlatform.Skype -> R.drawable.ic_im_skype; ImPlatform.Other -> R.drawable.ic_im_other
}

/** Opens the person's chat in the platform's app (or browser); copies the handle when there is no link. */
private fun openIm(ctx: Context, im: ImEntry) {
    val link = im.known.link(im.handle)
    val opened = link != null && try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }
    if (!opened) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(im.displayPlatform, im.handle))
        Toast.makeText(ctx, "Copied ${im.handle}", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).glass(20.dp), content = content)
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector?, title: String, sub: String, onClick: (() -> Unit)? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = Muted) else Spacer(Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(title)
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = Muted)
        }
        trailing()
    }
}

/** Contact page (or number details for unknown callers): photo header, actions, all saved info, call history. */
@Composable
fun ContactPage(
    contactId: Long?, number: String, title: String,
    onBack: () -> Unit, onCall: (String) -> Unit, onEdit: (ContactDetails?, String) -> Unit, onChanged: () -> Unit,
) {
    val ctx = LocalContext.current
    var reload by remember { mutableIntStateOf(0) }
    val details by produceState<ContactDetails?>(null, contactId, number, reload) {
        value = withContext(Dispatchers.IO) { (contactId ?: ContactsRepo.idForNumber(ctx, number))?.let { ContactsRepo.load(ctx, it) } }
    }
    val numbers = details?.phones?.map { it.value }?.ifEmpty { null } ?: listOf(number).filter { it.isNotEmpty() }
    var history by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    LaunchedEffect(details, number, reload) {
        history = withContext(Dispatchers.IO) { (if (numbers.isEmpty()) emptyList() else Data.recents(ctx, 2000).filter { c -> numbers.any { PhoneUtil.sameNumber(it, c.number) } }) }
    }
    var blocked by remember(number) { mutableStateOf(Data.isBlocked(ctx, number)) }
    var note by remember { mutableStateOf<String?>(null) }
    val name = details?.name?.ifEmpty { null } ?: title
    val primary = details?.phones?.firstOrNull { it.primary }?.value ?: numbers.firstOrNull() ?: number
    val photo = rememberPhoto(details?.photoUri, 1024)
    val place = PhoneUtil.countryOf(primary)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Box(Modifier.fillMaxWidth().height(340.dp).clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))) {
                if (photo != null) {
                    Image(photo, name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    val tint = avatarTint(name)
                    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(tint.copy(alpha = 0.5f), tint.copy(alpha = 0.12f)))), contentAlignment = Alignment.Center) {
                        Text(name.trim().firstOrNull()?.uppercase() ?: "#", fontSize = 120.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.85f))
                    }
                }
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                    Spacer(Modifier.weight(1f))
                    details?.let { d ->
                        CircleButton(if (d.starred) Icons.Filled.Star else Icons.Filled.StarBorder, "Favorite", { ContactsRepo.setStarred(ctx, d.contactId, !d.starred); reload++; onChanged() }, if (d.starred) Lavender else Color.White)
                    }
                    CircleButton(if (details == null) Icons.Filled.PersonAdd else Icons.Filled.Edit, if (details == null) "Add contact" else "Edit", { onEdit(details, number) })
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(name, fontSize = 30.sp, fontWeight = FontWeight.Black)
                if (name != PhoneUtil.pretty(number)) {
                    Text(listOfNotNull(PhoneUtil.pretty(primary).ifEmpty { null }, place).joinToString(" · "), color = Muted)
                } else if (place != null) Text(place, color = Muted)
                if (blocked) Text("Blocked", color = Danger, fontSize = 13.sp)
                details?.company?.takeIf { it.isNotEmpty() }?.let { Text(it, color = Muted, fontSize = 14.sp) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ActionChip(Icons.Filled.Call, "Call", Mint) { onCall(primary) }
                ActionChip(Icons.AutoMirrored.Filled.Message, "Message") { launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", PhoneUtil.normalize(primary), null))) }
                ActionChip(Icons.Filled.Videocam, "Video") { placeVideoCall(ctx, primary) }
                details?.emails?.firstOrNull()?.let { e -> ActionChip(Icons.Filled.Email, "Email") { launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e.value, null))) } }
                ActionChip(Icons.Filled.Share, "Share") {
                    launch(ctx, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "$name\n${numbers.joinToString("\n")}"), null))
                }
                ActionChip(Icons.Filled.Block, if (blocked) "Unblock" else "Block", Danger) {
                    val ok = if (blocked) Data.unblock(ctx, primary) else Data.block(ctx, primary)
                    if (ok) blocked = !blocked else note = "Make Txao Phone your default phone app to block numbers."
                }
            }
            note?.let { Text(it, color = Danger, fontSize = 13.sp, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center) }
        }
        details?.let { d ->
            item { Spacer(Modifier.height(12.dp)) }
            items(d.phones, key = { "p" + it.value }) { p ->
                InfoCard {
                    InfoRow(Icons.Filled.Call, PhoneUtil.pretty(p.value), listOfNotNull(p.display(ctx, true), if (p.primary) "Default" else null).joinToString(" · "), { onCall(p.value) }) {
                        IconButton({ placeVideoCall(ctx, p.value) }) { Icon(Icons.Filled.Videocam, "Video call", tint = Muted) }
                        IconButton({ launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", PhoneUtil.normalize(p.value), null))) }) { Icon(Icons.AutoMirrored.Filled.Message, "Message", tint = Muted) }
                    }
                }
            }
            items(d.emails, key = { "e" + it.value }) { e ->
                InfoCard { InfoRow(Icons.Filled.Email, e.value, e.display(ctx, false), { launch(ctx, Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", e.value, null))) }) }
            }
            items(d.ims, key = { "i" + it.platform + it.handle }) { im ->
                InfoCard {
                    Row(
                        Modifier.fillMaxWidth().clickable { openIm(ctx, im) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Image(painterResource(imIcon(im.known)), im.displayPlatform, Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(im.handle)
                            Text(im.displayPlatform, fontSize = 13.sp, color = Muted)
                        }
                    }
                }
            }
            if (d.address.isNotEmpty()) item {
                InfoCard { InfoRow(Icons.Filled.Place, d.address, "Address", { launch(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(d.address)))) }) }
            }
            if (d.company.isNotEmpty()) item { InfoCard { InfoRow(Icons.Filled.Business, d.company, "Organization") } }
            if (d.birthday.isNotEmpty()) item { InfoCard { InfoRow(Icons.Filled.Cake, PhoneUtil.birthdayLabel(d.birthday), "Birthday") } }
            if (d.notes.isNotEmpty()) item { InfoCard { InfoRow(Icons.Filled.Notes, d.notes, "Notes") } }
        }
        if (history.isNotEmpty()) item { Spacer(Modifier.height(8.dp)); SectionHeader("History") }
        items(history, key = { it.id }) { c ->
            val icon = when { c.missed -> Icons.AutoMirrored.Filled.CallMissed; c.outgoing -> Icons.AutoMirrored.Filled.CallMade; else -> Icons.AutoMirrored.Filled.CallReceived }
            val kind = c.kind
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(icon, null, tint = if (c.missed) Danger else Muted)
                Column(Modifier.weight(1f)) {
                    Text(kind, color = if (c.missed) Danger else Color.Unspecified)
                    Text(DateFormat.getMediumDateFormat(ctx).format(c.date) + " " + DateFormat.getTimeFormat(ctx).format(c.date), fontSize = 12.sp, color = Muted)
                }
                if (c.duration > 0) Text(PhoneUtil.duration(c.duration), color = Muted)
            }
        }
        if (history.isNotEmpty()) item {
            TextButton({ Data.deleteHistory(ctx, history.map { it.id }); history = emptyList(); onChanged() }, Modifier.padding(8.dp)) {
                Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete call history")
            }
        }
    }
}

@Composable
private fun CircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, tint: Color = Color.White) {
    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0x990B0E14)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = tint)
    }
}

/** Create / edit a contact inside Txao Phone. [initial] has rawId 0 for a new contact. */
@Composable
fun EditorScreen(initial: ContactDetails, onCancel: () -> Unit, onSaved: (Long?) -> Unit, onDeleted: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember { mutableStateOf(initial.name) }
    var company by remember { mutableStateOf(initial.company) }
    var address by remember { mutableStateOf(initial.address) }
    var birthday by remember { mutableStateOf(initial.birthday) }
    var notes by remember { mutableStateOf(initial.notes) }
    val phones = remember { mutableStateListOf<Labeled>().apply { addAll(initial.phones.ifEmpty { listOf(Labeled(ContactsRepo.PHONE_TYPES[0], null, "")) }) } }
    val emails = remember { mutableStateListOf<Labeled>().apply { addAll(initial.emails) } }
    val ims = remember { mutableStateListOf<ImEntry>().apply { addAll(initial.ims) } }
    var newPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var removePhoto by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editorScroll = rememberScrollState()
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) { newPhoto = Photos.encodeForContact(ctx, uri); removePhoto = false }
    }
    val preview = remember(newPhoto) { newPhoto?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val stored = rememberPhoto(if (removePhoto) null else initial.photoUri, 512)
    val shown = preview ?: stored

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onCancel) { Icon(Icons.Filled.Close, "Cancel") }
            Text(if (initial.rawId == 0L) "New contact" else "Edit contact", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Black)
            Button({
                if (name.isBlank() && phones.none { it.value.isNotBlank() }) { error = "Add a name or a phone number."; return@Button }
                val d = initial.copy(name = name, company = company, address = address, birthday = birthday, notes = notes, phones = phones.toList(), emails = emails.toList(), ims = ims.filter { it.handle.isNotBlank() })
                val id = ContactsRepo.save(ctx, ContactEdit(d, newPhoto, removePhoto))
                if (id == null) error = "Could not save. Is contacts permission granted?" else onSaved(id)
            }, Modifier.padding(end = 12.dp)) { Text("Save") }
        }
        Column(Modifier.weight(1f).verticalScroll(editorScroll).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(110.dp).clip(CircleShape).glass(55.dp).clickable { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, contentAlignment = Alignment.Center) {
                        if (shown != null) Image(shown, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.AddAPhoto, "Add photo", Modifier.size(36.dp), tint = Lavender)
                    }
                    if (shown != null) TextButton({ newPhoto = null; removePhoto = true }) { Text("Remove photo") }
                    else Text("Add photo", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Field("Name", name, { name = it }, Icons.Filled.Person)
            phones.forEachIndexed { i, p ->
                LabeledField(p, true, ContactsRepo.PHONE_TYPES, { phones[i] = it }, { if (phones.size > 1) phones.removeAt(i) else phones[i] = p.copy(value = "") })
            }
            TextButton({ phones += Labeled(ContactsRepo.PHONE_TYPES[0], null, "") }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add phone") }
            emails.forEachIndexed { i, e -> LabeledField(e, false, ContactsRepo.EMAIL_TYPES, { emails[i] = it }, { emails.removeAt(i) }) }
            TextButton({ emails += Labeled(ContactsRepo.EMAIL_TYPES[0], null, "") }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add email") }
            ims.forEachIndexed { i, im -> ImField(im, { ims[i] = it }, { ims.removeAt(i) }) }
            TextButton({ ims += ImEntry(ImPlatform.WhatsApp.label, "") }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text("Add username (WhatsApp, Telegram, Signal…)") }
            Field("Company", company, { company = it }, Icons.Filled.Business)
            Field("Address", address, { address = it }, Icons.Filled.Place)
            Field("Birthday (YYYY-MM-DD)", birthday, { birthday = it }, Icons.Filled.Cake)
            Field("Notes", notes, { notes = it }, Icons.Filled.Notes, singleLine = false)
            error?.let { Text(it, color = Danger) }
            if (initial.rawId != 0L) {
                TextButton({ confirmDelete = true }, colors = ButtonDefaults.textButtonColors(contentColor = Danger)) { Icon(Icons.Filled.Delete, null); Spacer(Modifier.width(8.dp)); Text("Delete contact") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${initial.name.ifEmpty { "contact" }}?") }, text = { Text("This removes the contact from this phone.") },
            confirmButton = { TextButton({ confirmDelete = false; if (ContactsRepo.delete(ctx, initial.contactId)) onDeleted() }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
        )
    }
}


@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit, icon: androidx.compose.ui.graphics.vector.ImageVector, singleLine: Boolean = true) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth(), label = { Text(label) }, leadingIcon = { Icon(icon, null, tint = Muted) }, singleLine = singleLine, shape = RoundedCornerShape(18.dp))
}

@Composable
private fun LabeledField(item: Labeled, phone: Boolean, types: List<Int>, onChange: (Labeled) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OutlinedTextField(
        item.value, { onChange(item.copy(value = it)) }, Modifier.fillMaxWidth(),
        label = { Text(if (phone) "Phone" else "Email") },
        leadingIcon = { Icon(if (phone) Icons.Filled.Call else Icons.Filled.Email, null, tint = Muted) },
        trailingIcon = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton({ menu = true }) { Text(ContactsRepo.typeName(item.type, phone)) }
                    DropdownMenu(menu, { menu = false }) {
                        types.forEach { t -> DropdownMenuItem({ Text(ContactsRepo.typeName(t, phone)) }, { onChange(item.copy(type = t, customLabel = null)); menu = false }) }
                    }
                }
                IconButton(onRemove) { Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = Muted) }
            }
        },
        singleLine = true, shape = RoundedCornerShape(18.dp),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = if (phone) androidx.compose.ui.text.input.KeyboardType.Phone else androidx.compose.ui.text.input.KeyboardType.Email),
    )
}

@Composable
private fun ImField(item: ImEntry, onChange: (ImEntry) -> Unit, onRemove: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    OutlinedTextField(
        item.handle, { onChange(item.copy(handle = it)) }, Modifier.fillMaxWidth(),
        label = { Text("Username or number · ${item.displayPlatform}") },
        leadingIcon = {
            Box {
                Image(painterResource(imIcon(item.known)), item.displayPlatform, Modifier.size(28.dp).clickable { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    ImPlatform.entries.forEach { p ->
                        DropdownMenuItem(
                            { Text(p.label) }, { onChange(item.copy(platform = p.label)); menu = false },
                            leadingIcon = { Image(painterResource(imIcon(p)), null, Modifier.size(24.dp)) },
                        )
                    }
                }
            }
        },
        trailingIcon = { IconButton(onRemove) { Icon(Icons.Filled.RemoveCircleOutline, "Remove", tint = Muted) } },
        singleLine = true, shape = RoundedCornerShape(18.dp),
    )
}
