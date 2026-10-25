package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private const val MID = 5000
private val HourHeight = 48.dp
private val GutterWidth = 44.dp

/**
 * Day (1), 3-day (3) and week (7) views: a header with the dates, a banner row for all-day events and a scrolling
 * 24 h grid. Swipe sideways for the previous / next page; tap an empty slot to get a "New event" block there.
 */
@Composable
fun TimeGridView(
    vm: CalendarViewModel, days: Int, anchor: LocalDate, jump: Int,
    onAnchor: (LocalDate) -> Unit, onOpen: (Occurrence) -> Unit, onNew: (LocalDateTime) -> Unit,
    onDayHeader: (LocalDate) -> Unit, onMoreDay: (LocalDate) -> Unit,
) {
    key(days, jump) {
        val first = remember { anchor }
        val weekStart = DayOfWeek.of(vm.settings.weekStart)
        val base = remember { if (days == 7) first.minusDays(((first.dayOfWeek.value - weekStart.value + 7) % 7).toLong()) else first }
        val pager = rememberPagerState(initialPage = MID, pageCount = { MID * 2 })
        val density = LocalDensity.current
        val scroll = rememberScrollState(with(density) { ((LocalTime.now().hour - 1).coerceAtLeast(0) * HourHeight.toPx()).toInt() })
        var ghost by remember { mutableStateOf<Pair<LocalDate, Int>?>(null) }
        var now by remember { mutableStateOf(LocalTime.now()) }

        LaunchedEffect(Unit) { while (true) { delay(30_000); now = LocalTime.now() } }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                ghost = null
                onAnchor(if (page == MID) first else base.plusDays(((page - MID) * days).toLong()))
            }
        }

        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            GridPage(
                vm, base.plusDays(((page - MID) * days).toLong()), days, scroll, ghost, { ghost = it }, now,
                onOpen, onNew, onDayHeader, onMoreDay,
            )
        }
    }
}

@Composable
private fun GridPage(
    vm: CalendarViewModel, firstDay: LocalDate, n: Int, scroll: androidx.compose.foundation.ScrollState,
    ghost: Pair<LocalDate, Int>?, onGhost: (Pair<LocalDate, Int>?) -> Unit, now: LocalTime,
    onOpen: (Occurrence) -> Unit, onNew: (LocalDateTime) -> Unit, onDayHeader: (LocalDate) -> Unit, onMoreDay: (LocalDate) -> Unit,
) {
    val hidden = vm.settings.hiddenCalendars
    val index = remember(vm.events.toList(), vm.deviceTick, hidden, firstDay, n) { vm.index(firstDay, firstDay.plusDays(n.toLong())) }
    val today = LocalDate.now()
    val dayList = (0 until n).map { firstDay.plusDays(it.toLong()) }
    val banners = dayList.map { d -> index[d].orEmpty().filter { it.banner } }
    val is24 = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val hourLabels = remember(is24) {
        val f = DateTimeFormatter.ofPattern(if (is24) "HH:mm" else "h a", Locale.getDefault())
        (0..23).map { LocalTime.of(it, 0).format(f) }
    }
    val compact = n > 3

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
            Spacer(Modifier.width(GutterWidth))
            dayList.forEach { d ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable { onDayHeader(d) }.padding(start = if (n == 1) 12.dp else 0.dp),
                    horizontalAlignment = if (n == 1) Alignment.Start else Alignment.CenterHorizontally,
                ) {
                    Text(
                        d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                        fontSize = 11.sp, color = if (d == today) MaterialTheme.colorScheme.primary else dimText(),
                    )
                    DayNumber(d, d == today, size = if (compact) 28 else 34, fontSize = if (compact) 14 else 18)
                }
            }
        }
        if (banners.any { it.isNotEmpty() }) {
            Row(Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                Spacer(Modifier.width(GutterWidth))
                banners.forEachIndexed { i, list ->
                    Column(Modifier.weight(1f).padding(horizontal = 1.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val shown = if (list.size <= 3) list else list.take(2)
                        shown.forEach { o -> BannerChip(o, vm.colorOf(o.event), compact) { onOpen(o) } }
                        if (shown.size < list.size) {
                            Text("+${list.size - shown.size} more", Modifier.clickable { onMoreDay(dayList[i]) }.padding(horizontal = 2.dp), fontSize = 10.sp, color = dimText(), maxLines = 1)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll)) {
            Box(Modifier.width(GutterWidth).height(HourHeight * 24)) {
                for (h in 1..23) {
                    Text(
                        hourLabels[h], Modifier.offset(y = HourHeight * h - 7.dp).width(GutterWidth).padding(end = 6.dp),
                        fontSize = 10.sp, textAlign = TextAlign.End, color = dimText(), maxLines = 1,
                    )
                }
            }
            dayList.forEach { d ->
                DayColumn(vm, d, index[d].orEmpty(), d == today, now, ghost, onGhost, onOpen, onNew, compact, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BannerChip(o: Occurrence, color: Int, compact: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(if (compact) 16.dp else 20.dp).clip(RoundedCornerShape(4.dp)).background(Color(color)).clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(titleOf(o.event), Modifier.padding(horizontal = 4.dp), fontSize = if (compact) 10.sp else 12.sp, maxLines = 1, overflow = TextOverflow.Clip, color = onEventColor(color))
    }
}

@Composable
private fun DayColumn(
    vm: CalendarViewModel, day: LocalDate, occ: List<Occurrence>, isToday: Boolean, now: LocalTime,
    ghost: Pair<LocalDate, Int>?, onGhost: (Pair<LocalDate, Int>?) -> Unit,
    onOpen: (Occurrence) -> Unit, onNew: (LocalDateTime) -> Unit, compact: Boolean, modifier: Modifier,
) {
    val placed = remember(occ, day) { DayLayout.place(day, occ) }
    val line = MaterialTheme.colorScheme.outlineVariant
    BoxWithConstraints(
        modifier.height(HourHeight * 24)
            .drawBehind {
                val h = HourHeight.toPx()
                for (i in 1..23) drawLine(line, Offset(0f, i * h), Offset(size.width, i * h), 1f)
                drawLine(line, Offset(0f, 0f), Offset(0f, size.height), 1f)
            }
            .pointerInput(day) {
                detectTapGestures { off ->
                    val minutes = ((off.y / HourHeight.toPx()) * 60).toInt() / 30 * 30
                    onGhost(day to minutes.coerceIn(0, 23 * 60))
                }
            },
    ) {
        placed.forEach { p ->
            val colW = maxWidth / p.columns
            val color = vm.colorOf(p.occ.event)
            val fg = onEventColor(color)
            val height = HourHeight * ((p.bottomMin - p.topMin) / 60f)
            Box(
                Modifier.offset(x = colW * p.column, y = HourHeight * (p.topMin / 60f)).size(colW, height).padding(1.dp)
                    .clip(RoundedCornerShape(5.dp)).background(Color(color)).clickable { onOpen(p.occ) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                Column {
                    Text(
                        titleOf(p.occ.event), color = fg, fontSize = if (compact) 10.sp else 12.sp, fontWeight = FontWeight.Medium,
                        maxLines = if (height >= 40.dp && !compact) 2 else 1, overflow = TextOverflow.Ellipsis, lineHeight = if (compact) 11.sp else 14.sp,
                    )
                    if (height >= 36.dp && !compact) {
                        Text(Format.time(p.occ.start) + " – " + Format.time(p.occ.end), color = fg.copy(alpha = .85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (ghost != null && ghost.first == day) {
            val at = ghost.second
            Box(
                Modifier.offset(y = HourHeight * (at / 60f)).fillMaxWidth().height(HourHeight).padding(1.dp)
                    .clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary)
                    .clickable { onGhost(null); onNew(day.atTime(at / 60, at % 60)) }.padding(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    if (!compact) Text(" New event", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                }
            }
        }
        if (isToday) {
            val y = HourHeight * ((now.hour * 60 + now.minute) / 60f)
            Box(
                Modifier.offset(y = y - 5.dp).fillMaxWidth().height(10.dp).drawBehind {
                    drawLine(NowRed, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 2.dp.toPx())
                    drawCircle(NowRed, 5.dp.toPx(), Offset(5.dp.toPx(), size.height / 2))
                },
            )
        }
    }
}
