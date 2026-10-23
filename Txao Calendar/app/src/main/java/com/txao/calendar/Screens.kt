package com.txao.calendar

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private enum class Mode { MONTH, AGENDA }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(vm: CalendarViewModel, draftText: String?, onDraftConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    var mode by remember { mutableStateOf(Mode.MONTH) }
    var selected by remember { mutableStateOf(LocalDate.now()) }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var editing by remember { mutableStateOf<Pair<Event, Boolean>?>(null) }
    var viewing by remember { mutableStateOf<Occurrence?>(null) }
    var menu by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    fun draft(day: LocalDate, title: String = ""): Event {
        val now = java.time.LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)
        val s = day.atTime(if (day == LocalDate.now()) now else java.time.LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        val rem = vm.settings.defaultReminder
        return Event(id = vm.newId(), title = title, start = s, end = s + 3600_000, reminders = if (rem >= 0) listOf(rem) else emptyList())
    }

    // An incoming "share text" / app shortcut opens the editor once.
    LaunchedEffect(draftText) {
        if (draftText != null) { editing = draft(selected, draftText.lineSequence().firstOrNull().orEmpty().take(80)).let { e ->
            e.copy(notes = draftText.lines().drop(1).joinToString("\n").trim()) to true }
            onDraftConsumed() }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val n = text?.let { vm.importIcs(it) } ?: 0
            Toast.makeText(ctx, if (n == 0) "No events found in that file" else "Imported $n event(s)", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                title = {
                    Text(
                        if (mode == Mode.MONTH) month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year else "Upcoming",
                        fontWeight = FontWeight.Black,
                    )
                },
                actions = {
                    if (mode == Mode.MONTH) {
                        IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                        IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                    }
                    TextButton(onClick = { selected = LocalDate.now(); month = YearMonth.now() }) { Text("Today") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text(if (mode == Mode.MONTH) "Upcoming list" else "Month view") }, onClick = {
                                mode = if (mode == Mode.MONTH) Mode.AGENDA else Mode.MONTH; menu = false
                            })
                            DropdownMenuItem(text = { Text("Import .ics") }, onClick = { menu = false; importer.launch("*/*") })
                            DropdownMenuItem(text = { Text("Export .ics") }, onClick = { menu = false; shareIcs(ctx, vm.exportIcs()) })
                            DropdownMenuItem(text = { Text("Settings") }, onClick = { menu = false; showSettings = true })
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = draft(selected) to true }, containerColor = Lavender, contentColor = androidx.compose.ui.graphics.Color(0xFF2E1B5C)) {
                Icon(Icons.Default.Add, "New event")
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when (mode) {
                Mode.MONTH -> MonthScreen(
                    vm, month, selected,
                    onPick = { selected = it; if (YearMonth.from(it) != month) month = YearMonth.from(it) },
                    onMonth = { month = it },
                    onOpen = { viewing = it },
                    onAdd = { d -> editing = draft(d) to true },
                )
                Mode.AGENDA -> AgendaScreen(vm, onOpen = { viewing = it })
            }
        }
    }

    editing?.let { (e, isNew) ->
        EventEditor(e, isNew, onSave = { vm.upsert(it); editing = null; selected = java.time.Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate(); month = YearMonth.from(selected) }, onDismiss = { editing = null })
    }
    viewing?.let { occ ->
        EventDetail(
            occ, vm,
            onEdit = { editing = occ.event to false; viewing = null },
            onClose = { viewing = null },
        )
    }
    if (showSettings) SettingsDialog(vm.settings, onClose = { showSettings = false })
}

@Composable
private fun MonthScreen(
    vm: CalendarViewModel, month: YearMonth, selected: LocalDate,
    onPick: (LocalDate) -> Unit, onMonth: (YearMonth) -> Unit, onOpen: (Occurrence) -> Unit, onAdd: (LocalDate) -> Unit,
) {
    val weekStart = DayOfWeek.of(vm.settings.weekStart)
    val first = month.atDay(1)
    val gridStart = first.minusDays(((first.dayOfWeek.value - weekStart.value + 7) % 7).toLong())
    val index = remember(vm.events.toList(), gridStart) { Days.index(vm.events, gridStart, gridStart.plusDays(42)) }
    val today = LocalDate.now()
    var drag by remember { mutableFloatStateOf(0f) }

    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    weekStart.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(3),
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f),
                )
            }
        }
        Column(
            Modifier.fillMaxWidth().weight(.55f).pointerInput(month) {
                detectHorizontalDragGestures(
                    onDragStart = { drag = 0f },
                    onDragEnd = { if (drag > 120) onMonth(month.minusMonths(1)) else if (drag < -120) onMonth(month.plusMonths(1)) },
                ) { _, dx -> drag += dx }
            },
        ) {
            for (row in 0 until 6) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (col in 0 until 7) {
                        val day = gridStart.plusDays((row * 7 + col).toLong())
                        DayCell(day, day.month == month.month, day == selected, day == today, index[day].orEmpty(), Modifier.weight(1f)) { onPick(day) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        DayList(vm, selected, index[selected].orEmpty(), onOpen, onAdd, Modifier.weight(.45f))
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, selected: Boolean, today: Boolean, occ: List<Occurrence>, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier.padding(2.dp).clip(shape)
            .background(if (selected) Lavender.copy(alpha = .22f) else if (inMonth) GlassFill else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(24.dp).clip(CircleShape).background(if (today) Lavender else androidx.compose.ui.graphics.Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(), fontSize = 12.sp, fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
                color = if (today) androidx.compose.ui.graphics.Color(0xFF2E1B5C) else MaterialTheme.colorScheme.onSurface.copy(alpha = if (inMonth) 1f else .35f),
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            occ.take(3).forEach { Box(Modifier.size(5.dp).clip(CircleShape).background(eventColor(it.event.color))) }
        }
        if (occ.size > 3) Text("+${occ.size - 3}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
    }
}

@Composable
private fun DayList(vm: CalendarViewModel, day: LocalDate, occ: List<Occurrence>, onOpen: (Occurrence) -> Unit, onAdd: (LocalDate) -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(12.dp)) {
        Text(Format.date(day), fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 6.dp))
        if (occ.isEmpty()) {
            Text("Nothing planned", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f), modifier = Modifier.clickable { onAdd(day) }.padding(vertical = 8.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) { items(occ) { OccurrenceRow(it, onOpen) } }
        }
    }
}

@Composable
private fun OccurrenceRow(o: Occurrence, onOpen: (Occurrence) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(o) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(34.dp).clip(CircleShape).background(eventColor(o.event.color)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(o.event.title.ifEmpty { "(No title)" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(
                if (o.event.allDay) "All day" else Format.time(o.start) + " – " + Format.time(o.end),
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            )
        }
        if (o.event.repeat.freq != Freq.NONE) Text("↻", color = Lavender)
    }
}

@Composable
private fun AgendaScreen(vm: CalendarViewModel, onOpen: (Occurrence) -> Unit) {
    val today = LocalDate.now()
    val index = remember(vm.events.toList(), today) { Days.index(vm.events, today, today.plusDays(120)).toSortedMap() }
    if (index.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No events in the next 120 days", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f)) }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 96.dp)) {
        index.forEach { (day, occ) ->
            item(key = day.toString()) {
                Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(12.dp)) {
                    Text(if (day == today) "Today" else Format.date(day), fontWeight = FontWeight.Black, modifier = Modifier.padding(bottom = 4.dp))
                    occ.forEach { OccurrenceRow(it, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun EventDetail(occ: Occurrence, vm: CalendarViewModel, onEdit: () -> Unit, onClose: () -> Unit) {
    val e = occ.event
    var confirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(e.title.ifEmpty { "(No title)" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(Format.whenText(e.copy(start = occ.start, end = occ.end)))
                if (e.repeat.freq != Freq.NONE) Text("Repeats " + e.repeat.freq.name.lowercase() + if (e.repeat.interval > 1) " (every ${e.repeat.interval})" else "", color = Lavender)
                if (e.location.isNotEmpty()) Text("📍 " + e.location)
                if (e.reminders.isNotEmpty()) Text("🔔 " + e.reminders.sortedDescending().joinToString(", ") { Format.reminderLabel(it, e.allDay) })
                if (e.notes.isNotEmpty()) Text(e.notes, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .8f))
            }
        },
        confirmButton = { TextButton(onClick = onEdit) { Text("Edit") } },
        dismissButton = {
            Row {
                TextButton(onClick = { confirm = true }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
    )
    if (confirm) {
        val repeating = e.repeat.freq != Freq.NONE
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete event?") },
            text = { Text(if (repeating) "This event repeats." else "This can't be undone.") },
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
private fun SettingsDialog(s: CalendarSettings, onClose: () -> Unit) {
    var weekStart by remember { mutableIntStateOf(s.weekStart) }
    var reminder by remember { mutableIntStateOf(s.defaultReminder) }
    AlertDialog(
        onDismissRequest = { onClose() },
        title = { Text("Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Week starts on", color = Lavender)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1 to "Monday", 7 to "Sunday", 6 to "Saturday").forEach { (v, l) ->
                        FilterChip(weekStart == v, { weekStart = v; s.weekStart = v }, label = { Text(l) })
                    }
                }
                Text("Default reminder", color = Lavender)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(-1 to "None", 10 to "10 min", 60 to "1 h").forEach { (v, l) ->
                        FilterChip(reminder == v, { reminder = v; s.defaultReminder = v }, label = { Text(l) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

private fun shareIcs(ctx: android.content.Context, text: String) {
    val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
    val f = File(dir, "txao-calendar.ics").apply { writeText(text) }
    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    ctx.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).setType("text/calendar").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
            "Export calendar",
        ),
    )
}
