package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

private val ReminderChoices = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)
private val AllDayReminderChoices = listOf(-540, 0, 1440, 2880, 10080)
private val DayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

/** Full-screen event editor. [initial] is a fresh draft for new events or the event being edited. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(initial: Event, isNew: Boolean, onSave: (Event) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(initial.title) }
    var notes by remember { mutableStateOf(initial.notes) }
    var location by remember { mutableStateOf(initial.location) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var color by remember { mutableIntStateOf(initial.color) }
    var start by remember { mutableStateOf(Instant.ofEpochMilli(initial.start).atZone(zone).toLocalDateTime()) }
    // For all-day events the stored end is exclusive; the editor shows the last day instead.
    var end by remember {
        mutableStateOf(
            Instant.ofEpochMilli(initial.end).atZone(zone).toLocalDateTime().let { if (initial.allDay) it.minusDays(1) else it },
        )
    }
    var repeat by remember { mutableStateOf(initial.repeat) }
    var reminders by remember { mutableStateOf(initial.reminders) }
    var picker by remember { mutableStateOf<String?>(null) }

    fun save() {
        val s = if (allDay) start.toLocalDate().atStartOfDay() else start
        var e = if (allDay) end.toLocalDate().plusDays(1).atStartOfDay() else end
        if (!e.isAfter(s)) e = if (allDay) s.plusDays(1) else s.plusHours(1)
        onSave(
            initial.copy(
                title = title.trim(), notes = notes.trim(), location = location.trim(), allDay = allDay, color = color,
                start = s.atZone(zone).toInstant().toEpochMilli(), end = e.atZone(zone).toInstant().toEpochMilli(),
                repeat = repeat, reminders = reminders.sorted(),
            ),
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        GlassBackground {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                    Text(if (isNew) "New event" else "Edit event", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    Button(onClick = { save() }) { Text("Save") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        title, { title = it }, label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    Section {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("All day", Modifier.weight(1f))
                            Switch(allDay, { allDay = it })
                        }
                        DateTimeRow("Starts", start, allDay, { picker = "startDate" }, { picker = "startTime" })
                        DateTimeRow("Ends", end, allDay, { picker = "endDate" }, { picker = "endTime" })
                    }
                    Section {
                        Text("Repeat", style = MaterialTheme.typography.labelLarge, color = Lavender)
                        RepeatEditor(repeat, start.toLocalDate(), zone) { repeat = it }
                    }
                    Section {
                        Text("Reminders", style = MaterialTheme.typography.labelLarge, color = Lavender)
                        val choices = if (allDay) AllDayReminderChoices else ReminderChoices
                        FlowRowSimple(choices) { m ->
                            FilterChip(
                                selected = m in reminders, onClick = { reminders = if (m in reminders) reminders - m else reminders + m },
                                label = { Text(Format.reminderLabel(m, allDay)) },
                            )
                        }
                    }
                    OutlinedTextField(location, { location = it }, label = { Text("Location") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    Section {
                        Text("Colour", style = MaterialTheme.typography.labelLarge, color = Lavender)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            EventColors.forEachIndexed { i, c ->
                                Box(
                                    Modifier.size(32.dp).clip(CircleShape).background(c).clickable { color = i },
                                    contentAlignment = Alignment.Center,
                                ) { if (i == color) Box(Modifier.size(12.dp).clip(CircleShape).background(Ink)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    when (picker) {
        "startDate", "endDate" -> {
            val isStart = picker == "startDate"
            val current = if (isStart) start else end
            val st = rememberDatePickerState(initialSelectedDateMillis = current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { picker = null },
                confirmButton = {
                    TextButton(onClick = {
                        st.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            if (isStart) {
                                val span = java.time.Duration.between(start, end)
                                start = LocalDateTime.of(d, start.toLocalTime()); end = start.plus(span)
                            } else end = LocalDateTime.of(d, end.toLocalTime())
                        }
                        picker = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
            ) { DatePicker(st) }
        }
        "startTime", "endTime" -> {
            val isStart = picker == "startTime"
            val current = if (isStart) start else end
            val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
            val st = rememberTimePickerState(current.hour, current.minute, is24)
            AlertDialog(
                onDismissRequest = { picker = null },
                text = { TimePicker(st) },
                confirmButton = {
                    TextButton(onClick = {
                        val t = LocalTime.of(st.hour, st.minute)
                        if (isStart) {
                            val span = java.time.Duration.between(start, end)
                            start = LocalDateTime.of(start.toLocalDate(), t); end = start.plus(span)
                        } else end = LocalDateTime.of(end.toLocalDate(), t)
                        picker = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picker = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

@Composable
private fun DateTimeRow(label: String, value: LocalDateTime, allDay: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.width(56.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
        OutlinedButton(onClick = onDate, modifier = Modifier.weight(1f)) { Text(Format.date(value.toLocalDate())) }
        if (!allDay) OutlinedButton(onClick = onTime) { Text(Format.time(value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())) }
    }
}

@Composable
private fun RepeatEditor(r: Repeat, startDate: LocalDate, zone: ZoneId, onChange: (Repeat) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    fun label(f: Freq) = when (f) {
        Freq.NONE -> "Does not repeat"; Freq.DAILY -> "Daily"; Freq.WEEKLY -> "Weekly"; Freq.MONTHLY -> "Monthly"; Freq.YEARLY -> "Yearly"
    }
    Box {
        OutlinedButton(onClick = { menu = true }) { Text(label(r.freq)) }
        DropdownMenu(menu, { menu = false }) {
            Freq.values().forEach { f -> DropdownMenuItem(text = { Text(label(f)) }, onClick = { onChange(r.copy(freq = f)); menu = false }) }
        }
    }
    if (r.freq == Freq.NONE) return
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Every")
        OutlinedTextField(
            r.interval.toString(), { s -> onChange(r.copy(interval = s.filter(Char::isDigit).take(3).toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
            modifier = Modifier.width(80.dp), singleLine = true,
        )
        Text(when (r.freq) { Freq.DAILY -> "day(s)"; Freq.WEEKLY -> "week(s)"; Freq.MONTHLY -> "month(s)"; else -> "year(s)" })
    }
    if (r.freq == Freq.WEEKLY) {
        val mask = if (r.weekdays == 0) 1 shl (startDate.dayOfWeek.value - 1) else r.weekdays
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayLabels.forEachIndexed { i, d ->
                FilterChip(
                    selected = mask and (1 shl i) != 0,
                    onClick = {
                        val next = mask xor (1 shl i)
                        if (next != 0) onChange(r.copy(weekdays = next))
                    },
                    label = { Text(d) }, modifier = Modifier.width(44.dp),
                )
            }
        }
    }
    var ends by remember(r.freq) { mutableStateOf(if (r.until != null) "until" else if (r.count != null) "count" else "never") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(ends == "never", { ends = "never"; onChange(r.copy(until = null, count = null)) }, label = { Text("Forever") })
        FilterChip(ends == "count", { ends = "count"; onChange(r.copy(until = null, count = r.count ?: 10)) }, label = { Text("Times") })
        FilterChip(
            ends == "until",
            { ends = "until"; onChange(r.copy(count = null, until = r.until ?: startDate.plusMonths(3).atTime(23, 59).atZone(zone).toInstant().toEpochMilli())) },
            label = { Text("Until") },
        )
    }
    if (ends == "count") OutlinedTextField(
        (r.count ?: 10).toString(), { s -> onChange(r.copy(count = s.filter(Char::isDigit).take(4).toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
        label = { Text("Occurrences") }, singleLine = true, modifier = Modifier.width(140.dp),
    )
    if (ends == "until") {
        var show by remember { mutableStateOf(false) }
        OutlinedButton(onClick = { show = true }) { Text(Format.date(r.until ?: 0, zone)) }
        if (show) {
            val st = rememberDatePickerState(initialSelectedDateMillis = Instant.ofEpochMilli(r.until ?: 0).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = { show = false },
                confirmButton = {
                    TextButton(onClick = {
                        st.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            onChange(r.copy(until = d.atTime(23, 59).atZone(zone).toInstant().toEpochMilli()))
                        }
                        show = false
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
            ) { DatePicker(st) }
        }
    }
}

/** Wraps chips onto multiple lines without needing the experimental FlowRow API. */
@Composable
private fun <T> FlowRowSimple(items: List<T>, perRow: Int = 3, chip: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(perRow).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { chip(it) } } }
    }
}
