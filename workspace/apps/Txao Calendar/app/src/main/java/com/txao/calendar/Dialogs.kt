package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** The month grid that drops down from the title in the schedule / day / week views. */
@Composable
fun MiniMonth(selected: LocalDate, weekStart: DayOfWeek, onPick: (LocalDate) -> Unit) {
    var month by remember(selected) { mutableStateOf(YearMonth.from(selected)) }
    var drag by remember { mutableFloatStateOf(0f) }
    val first = month.atDay(1)
    val lead = (first.dayOfWeek.value - weekStart.value + 7) % 7
    val gridStart = first.minusDays(lead.toLong())
    val rows = (lead + month.lengthOfMonth() + 6) / 7
    val today = LocalDate.now()

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).pointerInput(month) {
            detectHorizontalDragGestures(
                onDragStart = { drag = 0f },
                onDragEnd = { if (drag > 80) month = month.minusMonths(1) else if (drag < -80) month = month.plusMonths(1) },
            ) { _, dx -> drag += dx }
        },
    ) {
        Row(Modifier.fillMaxWidth()) {
            for (i in 0 until 7) {
                Text(
                    weekStart.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1).uppercase(),
                    Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp, color = dimText(),
                )
            }
        }
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().height(36.dp)) {
                for (c in 0 until 7) {
                    val day = gridStart.plusDays((r * 7 + c).toLong())
                    Box(Modifier.weight(1f).fillMaxHeight().clickable { onPick(day) }, contentAlignment = Alignment.Center) {
                        if (day == selected && day != today) Box(Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = .25f)))
                        DayNumber(day, day == today, size = 32, fontSize = 13, dim = day.month != month.month)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Events of one day, shown from the month view when a date is tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySheet(vm: CalendarViewModel, day: LocalDate, onClose: () -> Unit, onOpen: (Occurrence) -> Unit, onAdd: (LocalDate) -> Unit, onOpenDay: (LocalDate) -> Unit) {
    val hidden = vm.settings.hiddenCalendars
    val occ = remember(day, vm.events.toList(), vm.deviceTick, hidden) { vm.index(day, day.plusDays(1))[day].orEmpty() }
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(Format.dayName(day), fontSize = 12.sp, color = dimText())
                    Text("${day.dayOfMonth} ${Format.monthName(YearMonth.from(day))}", fontSize = 22.sp)
                }
                TextButton(onClick = { onOpenDay(day) }) { Text("Day view") }
                IconButton(onClick = { onAdd(day) }) { Icon(Icons.Default.Add, "New event") }
            }
            Spacer(Modifier.height(8.dp))
            if (occ.isEmpty()) {
                Text("Nothing planned", Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAdd(day) }.padding(vertical = 12.dp), color = dimText())
            } else {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    occ.forEach { o -> EventCard(o, vm.colorOf(o.event), onClick = { onOpen(o) }) }
                }
            }
        }
    }
}

/** Full-screen search over titles, places and notes. */
@Composable
fun SearchScreen(vm: CalendarViewModel, onOpen: (Occurrence) -> Unit, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val results = remember(query, vm.events.toList(), vm.deviceTick) { vm.search(query) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GlassBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                    TextField(
                        query, { query = it }, Modifier.weight(1f).focusRequester(focus), singleLine = true,
                        placeholder = { Text("Search events") },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                if (query.isNotBlank() && results.isEmpty()) {
                    Text("No matching events", Modifier.padding(24.dp), color = dimText())
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(results) { o ->
                        val day = java.time.Instant.ofEpochMilli(o.start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        EventCard(o, vm.colorOf(o.event), subtitle = Format.shortDate(day) + " · " + Format.timeRange(o), onClick = { onOpen(o) })
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceSetting(label: String, value: T, options: List<Pair<T, String>>, onPick: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f))
            Text(options.firstOrNull { it.first == value }?.second.orEmpty(), color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (v, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onPick(v); open = false }) }
        }
    }
}

@Composable
private fun SwitchSetting(label: String, hint: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (hint != null) Text(hint, fontSize = 12.sp, color = dimText())
        }
        Switch(checked, onChange)
    }
}

@Composable
fun SettingsDialog(vm: CalendarViewModel, onDeviceToggle: (Boolean) -> Unit, onImport: () -> Unit, onExport: () -> Unit, onClose: () -> Unit) {
    val s = vm.settings
    var weekStart by remember { mutableIntStateOf(s.weekStart) }
    var reminder by remember { mutableIntStateOf(s.defaultReminder) }
    var duration by remember { mutableIntStateOf(s.defaultDuration) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceSetting("Start the week on", weekStart, listOf(1 to "Monday", 6 to "Saturday", 7 to "Sunday")) { weekStart = it; s.weekStart = it }
                SwitchSetting("Show week numbers", null, s.showWeekNumbers) { s.showWeekNumbers = it }
                ChoiceSetting("Default notification", reminder, listOf(-1 to "None", 0 to "At time of event", 10 to "10 minutes", 30 to "30 minutes", 60 to "1 hour")) { reminder = it; s.defaultReminder = it }
                ChoiceSetting("Default event length", duration, listOf(30 to "30 minutes", 60 to "1 hour", 90 to "90 minutes", 120 to "2 hours")) { duration = it; s.defaultDuration = it }
                SwitchSetting("Device calendars", "Show and edit the calendars synced to this phone (Google, Exchange, DAVx5 …)", s.deviceCalendars, onDeviceToggle)
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                TextButton(onClick = onImport) { Text("Import .ics file") }
                TextButton(onClick = onExport) { Text("Export my calendar (.ics)") }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}

/** Lists the calendars kept inside the app; tap one to rename, recolour or delete it. */
@Composable
fun ManageCalendarsDialog(vm: CalendarViewModel, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<Cal?>(null) }
    var creating by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Calendars") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                vm.localCalendars.forEach { c ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { editing = c }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorDot(c.color, 16)
                        Spacer(Modifier.width(14.dp))
                        Text(c.name)
                    }
                }
                TextButton(onClick = { creating = true }) { Text("New calendar") }
                if (vm.deviceCalendars.isNotEmpty()) Text("Device calendars are managed in the system calendar settings.", fontSize = 12.sp, color = dimText(), modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
    if (creating) CalendarEditDialog(null, onSave = { name, color -> vm.addCalendar(name, color); creating = false }, onDelete = null, onDismiss = { creating = false })
    editing?.let { c ->
        CalendarEditDialog(
            c, onSave = { name, color -> vm.updateCalendar(c.copy(name = name.trim().ifEmpty { c.name }, color = color)); editing = null },
            onDelete = if (c.id == LOCAL_CALENDAR) null else ({ vm.deleteCalendar(c.id); editing = null }),
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun CalendarEditDialog(initial: Cal?, onSave: (String, Int) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.color ?: DefaultCalendarColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New calendar" else "Edit calendar") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ColorChoices(DefaultCalendarColor, color, calendarLabel = null) { color = it }
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete calendar and its events", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
