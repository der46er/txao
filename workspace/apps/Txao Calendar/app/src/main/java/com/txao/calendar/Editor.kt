package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale

private val ReminderChoices = listOf(0, 5, 10, 15, 30, 60, 120, 1440, 2880, 10080)
private val AllDayReminderChoices = listOf(-540, 0, 1440, 2880, 10080)
private val DayLabels = listOf("M", "T", "W", "T", "F", "S", "S")

/**
 * Full-screen event editor laid out like Google Calendar's: title, all-day, start / end, repeat, place,
 * notifications, calendar, colour and description. [initial] is a fresh draft for new events or the event being edited.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(initial: Event, isNew: Boolean, calendars: List<Cal>, onSave: (Event) -> Unit, onDismiss: () -> Unit) {
    val zone = ZoneId.systemDefault()
    var title by remember { mutableStateOf(initial.title) }
    var notes by remember { mutableStateOf(initial.notes) }
    var location by remember { mutableStateOf(initial.location) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var color by remember { mutableIntStateOf(initial.color) }
    var calendarId by remember { mutableStateOf(initial.calendarId) }
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
    var dialog by remember { mutableStateOf<String?>(null) }
    var calMenu by remember { mutableStateOf(false) }

    val lockedRepeat = initial.rrule.isNotEmpty()
    val writable = calendars.filter { it.writable }
    val cal = calendars.firstOrNull { it.id == calendarId } ?: writable.firstOrNull()
    val shownColor = if (color != 0) color else cal?.color ?: DefaultCalendarColor

    fun save() {
        val s = if (allDay) start.toLocalDate().atStartOfDay() else start
        var e = if (allDay) end.toLocalDate().plusDays(1).atStartOfDay() else end
        if (!e.isAfter(s)) e = if (allDay) s.plusDays(1) else s.plusHours(1)
        onSave(
            initial.copy(
                title = title.trim(), notes = notes.trim(), location = location.trim(), allDay = allDay, color = color,
                calendarId = cal?.id ?: calendarId,
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
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { save() }) { Text("Save") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    TextField(
                        title, { title = it }, Modifier.fillMaxWidth().padding(start = 44.dp, end = 8.dp),
                        placeholder = { Text("Add title", fontSize = 24.sp, color = dimText()) },
                        textStyle = TextStyle(fontSize = 24.sp), singleLine = true, colors = plainFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    EditDivider()
                    EditRow(Icons.Default.AccessTime) {
                        Text("All-day", Modifier.weight(1f))
                        Switch(allDay, { allDay = it })
                    }
                    EditRow(null) {
                        Text(Format.date(start.toLocalDate()), Modifier.weight(1f).clickable { picker = "startDate" }.padding(vertical = 6.dp), fontWeight = FontWeight.Medium)
                        if (!allDay) Text(timeText(start), Modifier.clickable { picker = "startTime" }.padding(vertical = 6.dp, horizontal = 4.dp))
                    }
                    EditRow(null) {
                        Text(Format.date(end.toLocalDate()), Modifier.weight(1f).clickable { picker = "endDate" }.padding(vertical = 6.dp), fontWeight = FontWeight.Medium)
                        if (!allDay) Text(timeText(end), Modifier.clickable { picker = "endTime" }.padding(vertical = 6.dp, horizontal = 4.dp))
                    }
                    EditRow(Icons.Default.Public) {
                        Text(zone.getDisplayName(DateTextStyle.FULL, Locale.getDefault()), color = dimText())
                    }
                    if (lockedRepeat) {
                        EditRow(Icons.Default.Repeat) { Text("Repeats (change the repeat rule in the system calendar)", color = dimText()) }
                    } else {
                        EditRow(Icons.Default.Repeat, onClick = { dialog = "repeat" }) {
                            Text(Format.repeatText(repeat, start.toLocalDate(), zone))
                        }
                    }
                    EditDivider()
                    EditRow(Icons.Default.LocationOn) {
                        TextField(
                            location, { location = it }, Modifier.weight(1f).offset(x = (-16).dp), placeholder = { Text("Add location") },
                            singleLine = true, colors = plainFieldColors(),
                        )
                    }
                    reminders.sortedDescending().forEachIndexed { i, m ->
                        EditRow(if (i == 0) Icons.Default.Notifications else null) {
                            Text(Format.reminderLabel(m, allDay), Modifier.weight(1f))
                            IconButton(onClick = { reminders = reminders - m }) { Icon(Icons.Default.Close, "Remove", tint = dimText()) }
                        }
                    }
                    EditRow(if (reminders.isEmpty()) Icons.Default.Notifications else null, onClick = { dialog = "reminder" }) {
                        Text("Add notification", color = dimText())
                    }
                    EditDivider()
                    Box {
                        EditRow(null, onClick = { calMenu = true }) {
                            ColorDot(cal?.color ?: DefaultCalendarColor, 14)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(cal?.name ?: "Calendar")
                                if (!cal?.account.isNullOrEmpty()) Text(cal?.account.orEmpty(), fontSize = 12.sp, color = dimText())
                            }
                        }
                        DropdownMenu(calMenu, { calMenu = false }, offset = androidx.compose.ui.unit.DpOffset(60.dp, 0.dp)) {
                            writable.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c.name + if (c.account.isNotEmpty()) "  ·  ${c.account}" else "") },
                                    leadingIcon = { ColorDot(c.color, 14) },
                                    onClick = { calendarId = c.id; calMenu = false },
                                )
                            }
                        }
                    }
                    EditRow(Icons.Default.Palette, onClick = { dialog = "color" }) {
                        ColorDot(shownColor, 14)
                        Spacer(Modifier.width(10.dp))
                        Text(if (color == 0) "Calendar colour" else EventPalette.firstOrNull { it.second == color }?.first ?: "Custom colour")
                    }
                    EditDivider()
                    EditRow(Icons.AutoMirrored.Filled.Notes) {
                        TextField(
                            notes, { notes = it }, Modifier.weight(1f).offset(x = (-16).dp), placeholder = { Text("Add description") },
                            minLines = 2, colors = plainFieldColors(),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }

    when (dialog) {
        "repeat" -> RepeatDialog(
            start.toLocalDate(), repeat, zone,
            onPick = { repeat = it; dialog = null },
            onCustom = { dialog = "custom" },
            onDismiss = { dialog = null },
        )
        "custom" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Custom repeat") },
            text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) { RepeatEditor(if (repeat.freq == Freq.NONE) Repeat(Freq.WEEKLY) else repeat, start.toLocalDate(), zone) { repeat = it } } },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("Done") } },
        )
        "reminder" -> {
            val choices = (if (allDay) AllDayReminderChoices else ReminderChoices).filter { it !in reminders }
            AlertDialog(
                onDismissRequest = { dialog = null },
                title = { Text("Add notification") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        choices.forEach { m ->
                            Text(
                                Format.reminderLabel(m, allDay),
                                Modifier.fillMaxWidth().clickable { reminders = reminders + m; dialog = null }.padding(vertical = 12.dp),
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
            )
        }
        "color" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Event colour") },
            text = {
                ColorChoices(cal?.color ?: DefaultCalendarColor, color, calendarLabel = "Calendar colour") { color = it; dialog = null }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
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

private fun timeText(t: LocalDateTime): String = Format.time(t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

@Composable
private fun plainFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent,
)

@Composable
private fun EditDivider() {
    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EditRow(icon: ImageVector?, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp).heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp)) { if (icon != null) Icon(icon, null, tint = dimText()) }
        Spacer(Modifier.width(20.dp))
        content()
    }
}

/** Google-style quick repeat choices; "Custom..." opens the full editor. */
@Composable
private fun RepeatDialog(start: LocalDate, current: Repeat, zone: ZoneId, onPick: (Repeat) -> Unit, onCustom: () -> Unit, onDismiss: () -> Unit) {
    val options = listOf(
        Repeat() to "Does not repeat",
        Repeat(Freq.DAILY) to "Every day",
        Repeat(Freq.WEEKLY) to "Every week on " + start.dayOfWeek.getDisplayName(DateTextStyle.FULL, Locale.getDefault()),
        Repeat(Freq.MONTHLY) to "Every month on day ${start.dayOfMonth}",
        Repeat(Freq.YEARLY) to "Every year on " + java.time.format.DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()).format(start),
        Repeat(Freq.WEEKLY, weekdays = 31) to "Every weekday (Monday to Friday)",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column {
                options.forEach { (r, label) ->
                    val selected = r.freq == current.freq && r.interval == current.interval && r.weekdays == current.weekdays &&
                        current.until == null && current.count == null
                    Row(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, Modifier.weight(1f), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        if (selected) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("Custom…", Modifier.fillMaxWidth().clickable { onCustom() }.padding(vertical = 12.dp))
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatEditor(r: Repeat, startDate: LocalDate, zone: ZoneId, onChange: (Repeat) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    fun unit(f: Freq) = when (f) {
        Freq.NONE -> "Does not repeat"; Freq.DAILY -> "day(s)"; Freq.WEEKLY -> "week(s)"; Freq.MONTHLY -> "month(s)"; Freq.YEARLY -> "year(s)"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Every")
        OutlinedTextField(
            r.interval.toString(), { s -> onChange(r.copy(interval = s.filter(Char::isDigit).take(3).toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
            modifier = Modifier.width(76.dp), singleLine = true,
        )
        Box {
            OutlinedButton(onClick = { menu = true }) { Text(unit(r.freq)) }
            DropdownMenu(menu, { menu = false }) {
                listOf(Freq.DAILY, Freq.WEEKLY, Freq.MONTHLY, Freq.YEARLY).forEach { f ->
                    DropdownMenuItem(text = { Text(unit(f)) }, onClick = { onChange(r.copy(freq = f, weekdays = if (f == Freq.WEEKLY) r.weekdays else 0)); menu = false })
                }
            }
        }
    }
    if (r.freq == Freq.WEEKLY) {
        val mask = if (r.weekdays == 0) 1 shl (startDate.dayOfWeek.value - 1) else r.weekdays
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            DayLabels.forEachIndexed { i, d ->
                val on = mask and (1 shl i) != 0
                Box(
                    Modifier.size(34.dp).clip(CircleShape).background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable {
                            val next = mask xor (1 shl i)
                            if (next != 0) onChange(r.copy(weekdays = next))
                        },
                    contentAlignment = Alignment.Center,
                ) { Text(d, fontSize = 13.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface) }
            }
        }
    }
    Text("Ends", color = dimText(), fontSize = 13.sp)
    var ends by remember(r.freq) { mutableStateOf(if (r.until != null) "until" else if (r.count != null) "count" else "never") }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(ends == "never", { ends = "never"; onChange(r.copy(until = null, count = null)) }, label = { Text("Never") })
        FilterChip(ends == "count", { ends = "count"; onChange(r.copy(until = null, count = r.count ?: 10)) }, label = { Text("After") })
        FilterChip(
            ends == "until",
            { ends = "until"; onChange(r.copy(count = null, until = r.until ?: startDate.plusMonths(3).atTime(23, 59).atZone(zone).toInstant().toEpochMilli())) },
            label = { Text("On") },
        )
    }
    if (ends == "count") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                (r.count ?: 10).toString(), { s -> onChange(r.copy(count = s.filter(Char::isDigit).take(4).toIntOrNull()?.coerceAtLeast(1) ?: 1)) },
                singleLine = true, modifier = Modifier.width(96.dp),
            )
            Text("times")
        }
    }
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

/**
 * The Google event colours as a grid, with "calendar colour" first. [selected] is 0 for the calendar colour.
 * Also used by the calendar editor, where [calendarLabel] is null and only the palette is offered.
 */
@Composable
fun ColorChoices(calendarColor: Int, selected: Int, calendarLabel: String?, onPick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (calendarLabel != null) {
            Row(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).clickable { onPick(0) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(calendarColor, selected == 0)
                Spacer(Modifier.width(12.dp))
                Text(calendarLabel)
            }
        }
        EventPalette.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { (_, c) -> Swatch(c, selected == c, Modifier.clickable { onPick(c) }) }
            }
        }
    }
}

@Composable
private fun Swatch(argb: Int, selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).clip(CircleShape).background(Color(argb)), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Default.Check, null, Modifier.size(20.dp), tint = onEventColor(argb))
    }
}
