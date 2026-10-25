package com.txao.calendar

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private data class Editing(val event: Event, val isNew: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarApp(vm: CalendarViewModel, draftText: String?, onDraftConsumed: () -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.systemDefault()
    val view = vm.settings.view
    var anchor by remember { mutableStateOf(LocalDate.now()) }
    var jump by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var viewing by remember { mutableStateOf<Occurrence?>(null) }
    var daySheet by remember { mutableStateOf<LocalDate?>(null) }
    var miniOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCalendars by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    fun jumpTo(d: LocalDate) { anchor = d; jump++ }

    fun defaultTime(day: LocalDate): LocalDateTime =
        if (day == LocalDate.now()) day.atTime(LocalTime.of((LocalTime.now().hour + 1).coerceAtMost(23), 0)) else day.atTime(9, 0)

    fun newEvent(start: LocalDateTime, title: String = "", notes: String = ""): Event {
        val s = start.atZone(zone).toInstant().toEpochMilli()
        val rem = vm.settings.defaultReminder
        return Event(
            id = vm.newId(), title = title, notes = notes, start = s, end = s + vm.settings.defaultDuration * 60_000L,
            reminders = if (rem >= 0) listOf(rem) else emptyList(),
            calendarId = vm.localCalendars.firstOrNull { vm.isVisible(it.id) }?.id ?: LOCAL_CALENDAR,
        )
    }

    // An incoming "share text" / app shortcut opens the editor once.
    LaunchedEffect(draftText) {
        if (draftText != null) {
            editing = Editing(newEvent(defaultTime(anchor), draftText.lineSequence().firstOrNull().orEmpty().take(80), draftText.lines().drop(1).joinToString("\n").trim()), true)
            onDraftConsumed()
        }
    }

    val importer = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching { ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val n = text?.let { vm.importIcs(it) } ?: 0
            Toast.makeText(ctx, if (n == 0) "No events found in that file" else "Imported $n event(s)", Toast.LENGTH_LONG).show()
        }
    }
    val devicePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        vm.setDeviceCalendars(r.isNotEmpty() && r.values.all { it })
    }
    fun toggleDevice(on: Boolean) {
        if (!on) vm.setDeviceCalendars(false)
        else if (DeviceCalendars.granted(ctx)) vm.setDeviceCalendars(true)
        else devicePermission.launch(DeviceCalendars.PERMISSIONS)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContent(
                vm, view,
                onView = { v -> vm.settings.view = v; miniOpen = false; scope.launch { drawer.close() } },
                onEnableDevice = { scope.launch { drawer.close() }; toggleDevice(true) },
                onManage = { scope.launch { drawer.close() }; showCalendars = true },
                onSettings = { scope.launch { drawer.close() }; showSettings = true },
            )
        },
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopBar(
                    title = Format.monthTitle(anchor), hasMenu = view != ViewMode.MONTH, open = miniOpen,
                    onMenu = { scope.launch { drawer.open() } }, onTitle = { miniOpen = !miniOpen },
                    onSearch = { showSearch = true }, onToday = { jumpTo(LocalDate.now()) },
                )
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { editing = Editing(newEvent(defaultTime(anchor)), true) },
                    containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = RoundedCornerShape(16.dp),
                ) { Icon(Icons.Default.Add, "New event") }
            },
        ) { pad ->
            Column(Modifier.padding(pad).fillMaxSize()) {
                AnimatedVisibility(miniOpen && view != ViewMode.MONTH) {
                    MiniMonth(anchor, DayOfWeek.of(vm.settings.weekStart)) { jumpTo(it); miniOpen = false }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    val open: (Occurrence) -> Unit = { viewing = it }
                    when (view) {
                        ViewMode.SCHEDULE -> ScheduleView(
                            vm, anchor, jump, onAnchor = { anchor = it }, onOpen = open,
                            onAdd = { d -> editing = Editing(newEvent(defaultTime(d)), true) },
                        )
                        ViewMode.DAY, ViewMode.THREE_DAYS, ViewMode.WEEK -> TimeGridView(
                            vm, when (view) { ViewMode.DAY -> 1; ViewMode.THREE_DAYS -> 3; else -> 7 }, anchor, jump,
                            onAnchor = { anchor = it }, onOpen = open,
                            onNew = { editing = Editing(newEvent(it), true) },
                            onDayHeader = { d -> if (view != ViewMode.DAY) { vm.settings.view = ViewMode.DAY; jumpTo(d) } },
                            onMoreDay = { daySheet = it },
                        )
                        ViewMode.MONTH -> MonthView(vm, anchor, jump, onAnchor = { anchor = it }, onDay = { daySheet = it })
                    }
                }
            }
        }
    }

    daySheet?.let { d ->
        DaySheet(
            vm, d, onClose = { daySheet = null },
            onOpen = { daySheet = null; viewing = it },
            onAdd = { daySheet = null; editing = Editing(newEvent(defaultTime(d)), true) },
            onOpenDay = { daySheet = null; vm.settings.view = ViewMode.DAY; jumpTo(d) },
        )
    }
    editing?.let { ed ->
        EventEditor(
            ed.event, ed.isNew, vm.calendars,
            onSave = { updated ->
                vm.setVisible(updated.calendarId, true)
                vm.save(updated, if (ed.isNew) null else ed.event)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
    viewing?.let { occ ->
        EventDetail(occ, vm, onEdit = { e -> viewing = null; editing = Editing(e, false) }, onClose = { viewing = null })
    }
    if (showSearch) SearchScreen(vm, onOpen = { showSearch = false; viewing = it }, onClose = { showSearch = false })
    if (showCalendars) ManageCalendarsDialog(vm, onClose = { showCalendars = false })
    if (showSettings) {
        SettingsDialog(
            vm, onDeviceToggle = { toggleDevice(it) },
            onImport = { importer.launch("*/*") },
            onExport = { shareIcs(ctx, vm.exportIcs()) },
            onClose = { showSettings = false },
        )
    }
}

@Composable
private fun TopBar(title: String, hasMenu: Boolean, open: Boolean, onMenu: () -> Unit, onTitle: () -> Unit, onSearch: () -> Unit, onToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(56.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, "Menu") }
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).clickable(enabled = hasMenu, onClick = onTitle).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, fontSize = 22.sp, maxLines = 1)
            if (hasMenu) Icon(if (open) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown, null)
        }
        IconButton(onClick = onSearch) { Icon(Icons.Default.Search, "Search") }
        IconButton(onClick = onToday) {
            Box(
                Modifier.size(22.dp).border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(5.dp)),
                contentAlignment = Alignment.Center,
            ) { Text(LocalDate.now().dayOfMonth.toString(), fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun DrawerContent(
    vm: CalendarViewModel, view: ViewMode, onView: (ViewMode) -> Unit,
    onEnableDevice: () -> Unit, onManage: () -> Unit, onSettings: () -> Unit,
) {
    val views: List<Triple<ViewMode, String, ImageVector>> = listOf(
        Triple(ViewMode.SCHEDULE, "Schedule", Icons.Default.ViewAgenda),
        Triple(ViewMode.DAY, "Day", Icons.Default.ViewDay),
        Triple(ViewMode.THREE_DAYS, "3 days", Icons.Default.ViewColumn),
        Triple(ViewMode.WEEK, "Week", Icons.Default.ViewWeek),
        Triple(ViewMode.MONTH, "Month", Icons.Default.CalendarViewMonth),
    )
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text("Calendar", Modifier.padding(horizontal = 28.dp, vertical = 20.dp), fontSize = 22.sp)
            views.forEach { (mode, label, icon) ->
                NavigationDrawerItem(
                    label = { Text(label) }, icon = { Icon(icon, null) }, selected = view == mode, onClick = { onView(mode) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            vm.localCalendars.forEach { CalendarCheck(vm, it) }
            vm.deviceCalendars.groupBy { it.account }.forEach { (account, list) ->
                Text(account, Modifier.padding(start = 28.dp, top = 12.dp, bottom = 2.dp), fontSize = 12.sp, color = dimText())
                list.forEach { CalendarCheck(vm, it) }
            }
            if (!vm.settings.deviceCalendars) {
                NavigationDrawerItem(
                    label = { Text("Show device calendars") }, icon = { Icon(Icons.Default.Add, null) }, selected = false, onClick = onEnableDevice,
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            NavigationDrawerItem(
                label = { Text("Manage calendars") }, icon = { Icon(Icons.Default.Edit, null) }, selected = false, onClick = onManage,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            NavigationDrawerItem(
                label = { Text("Settings") }, icon = { Icon(Icons.Default.Settings, null) }, selected = false, onClick = onSettings,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
    }
}

@Composable
private fun CalendarCheck(vm: CalendarViewModel, c: Cal) {
    val on = vm.isVisible(c.id)
    Row(
        Modifier.fillMaxWidth().clickable { vm.setVisible(c.id, !on) }.padding(start = 20.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            on, { vm.setVisible(c.id, it) },
            colors = CheckboxDefaults.colors(checkedColor = Color(c.color), uncheckedColor = Color(c.color), checkmarkColor = onEventColor(c.color)),
        )
        Text(c.name, Modifier.padding(start = 4.dp), maxLines = 1)
    }
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
