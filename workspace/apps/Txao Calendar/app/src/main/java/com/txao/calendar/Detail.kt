package com.txao.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.ZoneId

/** Full-screen event page (like Google Calendar's): colour square + title, when, repeat, place, reminders, calendar, notes. */
@Composable
fun EventDetail(occ: Occurrence, vm: CalendarViewModel, onEdit: (Event) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val e = remember(occ) { vm.withReminders(occ.event) }
    val cal = vm.calendar(e.calendarId)
    val color = vm.colorOf(e)
    val canEdit = cal?.writable != false
    var confirm by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GlassBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                    Spacer(Modifier.weight(1f))
                    if (canEdit) IconButton(onClick = { onEdit(e) }) { Icon(Icons.Default.Edit, "Edit") }
                    if (canEdit) IconButton(onClick = { confirm = true }) { Icon(Icons.Default.Delete, "Delete") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 9.dp).size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(color)))
                        Spacer(Modifier.width(24.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(titleOf(e), fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            Text(Format.whenText(e.copy(start = occ.start, end = occ.end)), color = dimText(), fontSize = 14.sp)
                        }
                    }
                    if (e.repeats) {
                        val startDay = Instant.ofEpochMilli(occ.start).atZone(zone).toLocalDate()
                        val text = if (e.repeat.freq != Freq.NONE) Format.repeatText(e.repeat, startDay, zone)
                        else ICal.parseRule(e.rrule, zone)?.let { Format.repeatText(it, startDay, zone) } ?: "Repeats"
                        DetailRow(Icons.Default.Repeat) { Text(text) }
                    }
                    if (e.location.isNotEmpty()) {
                        DetailRow(Icons.Default.LocationOn) {
                            Text(
                                e.location,
                                Modifier.clip(RoundedCornerShape(6.dp)).clickable {
                                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(e.location)))) }
                                },
                            )
                        }
                    }
                    if (e.reminders.isNotEmpty()) {
                        DetailRow(Icons.Default.Notifications) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                e.reminders.sortedDescending().forEach { Text(Format.reminderLabel(it, e.allDay)) }
                            }
                        }
                    }
                    if (e.notes.isNotEmpty()) DetailRow(Icons.AutoMirrored.Filled.Notes) { Text(e.notes) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { ColorDot(cal?.color ?: color, 14) }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(cal?.name ?: "Calendar")
                            if (!cal?.account.isNullOrEmpty()) Text(cal?.account.orEmpty(), color = dimText(), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    if (confirm) {
        val repeating = e.repeat.freq != Freq.NONE && !e.isDevice
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete event?") },
            text = { Text(if (repeating) "This event repeats." else if (e.repeats) "This deletes the whole series." else "This can't be undone.") },
            confirmButton = {
                Row {
                    if (repeating) TextButton(onClick = { vm.deleteOccurrence(occ); confirm = false; onClose() }) { Text("Only this one") }
                    TextButton(onClick = { vm.delete(e.id); confirm = false; onClose() }) { Text(if (repeating) "All" else "Delete", color = MaterialTheme.colorScheme.error) }
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DetailRow(icon: ImageVector, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.size(24.dp), tint = dimText())
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f)) { content() }
    }
}
