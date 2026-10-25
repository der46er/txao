package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.util.Locale

private const val MID = 5000
private val ChipHeight = 15.dp

/** Month grid with vertical paging between months, like Google Calendar. Tapping a day reports it through [onDay]. */
@Composable
fun MonthView(vm: CalendarViewModel, anchor: LocalDate, jump: Int, onAnchor: (LocalDate) -> Unit, onDay: (LocalDate) -> Unit) {
    key(jump) {
        val first = remember { anchor }
        val base = remember { YearMonth.from(first) }
        val pager = rememberPagerState(initialPage = MID, pageCount = { MID * 2 })
        val weekStart = DayOfWeek.of(vm.settings.weekStart)
        val weekNumbers = vm.settings.showWeekNumbers

        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                val m = base.plusMonths((page - MID).toLong())
                onAnchor(m.atDay(minOf(first.dayOfMonth, m.lengthOfMonth())))
            }
        }

        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(end = 0.dp)) {
                if (weekNumbers) Spacer(Modifier.width(22.dp))
                for (i in 0 until 7) {
                    Text(
                        weekStart.plus(i.toLong()).getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1).uppercase(),
                        Modifier.weight(1f).padding(vertical = 6.dp), textAlign = TextAlign.Center, fontSize = 12.sp, color = dimText(),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            VerticalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
                MonthPage(vm, base.plusMonths((page - MID).toLong()), weekStart, weekNumbers, onDay)
            }
        }
    }
}

@Composable
private fun MonthPage(vm: CalendarViewModel, month: YearMonth, weekStart: DayOfWeek, weekNumbers: Boolean, onDay: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - weekStart.value + 7) % 7)
    val gridStart = first.minusDays(lead.toLong())
    val rows = (lead + month.lengthOfMonth() + 6) / 7
    val hidden = vm.settings.hiddenCalendars
    val index = remember(vm.events.toList(), vm.deviceTick, hidden, gridStart, rows) { vm.index(gridStart, gridStart.plusDays(rows * 7L)) }
    val today = LocalDate.now()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rowHeight: Dp = maxHeight / rows
        val chips = (((rowHeight - 24.dp) / ChipHeight).toInt()).coerceAtLeast(0)
        Column(Modifier.fillMaxSize()) {
            for (row in 0 until rows) {
                Column(Modifier.weight(1f).fillMaxWidth()) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        if (weekNumbers) {
                            Text(
                                gridStart.plusDays(row * 7L).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR).toString(),
                                Modifier.width(22.dp).padding(top = 6.dp), textAlign = TextAlign.Center, fontSize = 10.sp, color = dimText(),
                            )
                        }
                        for (col in 0 until 7) {
                            val day = gridStart.plusDays((row * 7 + col).toLong())
                            DayCell(day, day.month == month.month, day == today, index[day].orEmpty(), chips, vm, Modifier.weight(1f).fillMaxHeight()) { onDay(day) }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate, inMonth: Boolean, today: Boolean, occ: List<Occurrence>, maxChips: Int, vm: CalendarViewModel, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        DayNumber(day, today, size = 22, fontSize = 11, dim = !inMonth, modifier = Modifier.padding(top = 2.dp, bottom = 1.dp))
        val shown = if (occ.size <= maxChips) occ else occ.take((maxChips - 1).coerceAtLeast(0))
        shown.forEach { o -> MonthChip(o, vm.colorOf(o.event), dim = !inMonth) }
        if (shown.size < occ.size) {
            Text("+${occ.size - shown.size}", fontSize = 10.sp, color = dimText(), maxLines = 1)
        }
    }
}

@Composable
private fun MonthChip(o: Occurrence, color: Int, dim: Boolean) {
    val filled = o.banner
    val c = Color(color).copy(alpha = if (dim) .45f else 1f)
    Box(
        Modifier.fillMaxWidth().height(ChipHeight).padding(horizontal = 1.dp, vertical = 0.5.dp).clip(RoundedCornerShape(3.dp))
            .background(if (filled) c else c.copy(alpha = c.alpha * .28f)),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            titleOf(o.event), Modifier.padding(horizontal = 3.dp), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip,
            color = if (filled) onEventColor(color) else MaterialTheme.colorScheme.onSurface,
        )
    }
}
