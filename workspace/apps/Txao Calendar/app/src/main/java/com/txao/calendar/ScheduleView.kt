package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth

private sealed class SRow {
    abstract val key: String
    abstract val date: LocalDate
    data class MonthHeader(val month: YearMonth) : SRow() {
        override val key get() = "m:$month"
        override val date: LocalDate get() = month.atDay(1)
    }
    data class Day(override val date: LocalDate, val occ: List<Occurrence>) : SRow() {
        override val key get() = "d:$date"
    }
}

/** Scrolling agenda: month banners, then one block per day that has events (today is always shown). */
@Composable
fun ScheduleView(
    vm: CalendarViewModel, anchor: LocalDate, jump: Int,
    onAnchor: (LocalDate) -> Unit, onOpen: (Occurrence) -> Unit, onAdd: (LocalDate) -> Unit,
) {
    key(jump) {
        val first = remember { anchor }
        val firstMonth = remember { YearMonth.from(first) }
        var monthsBack by remember { mutableIntStateOf(1) }
        var monthsAhead by remember { mutableIntStateOf(6) }
        val from = firstMonth.minusMonths(monthsBack.toLong()).atDay(1)
        val to = firstMonth.plusMonths(monthsAhead.toLong() + 1).atDay(1)
        val hidden = vm.settings.hiddenCalendars
        val today = LocalDate.now()

        val rows = remember(vm.events.toList(), vm.deviceTick, hidden, from, to) {
            val index = vm.index(from, to)
            val out = ArrayList<SRow>()
            var month: YearMonth? = null
            var d = from
            while (d.isBefore(to)) {
                val occ = index[d].orEmpty()
                if (occ.isNotEmpty() || d == today) {
                    val ym = YearMonth.from(d)
                    if (ym != month) { out += SRow.MonthHeader(ym); month = ym }
                    out += SRow.Day(d, occ)
                }
                d = d.plusDays(1)
            }
            out
        }

        val state = rememberLazyListState()
        val rowsNow by rememberUpdatedState(rows)
        // Start at the first day on or after the anchor (once per jump).
        var placed by remember { mutableStateOf(false) }
        LaunchedEffect(rows.isNotEmpty()) {
            if (!placed && rows.isNotEmpty()) {
                val i = rows.indexOfFirst { it is SRow.Day && !it.date.isBefore(first) }
                val target = if (i < 0) rows.lastIndex else if (rows.getOrNull(i - 1) is SRow.MonthHeader) i - 1 else i
                state.scrollToItem(target.coerceAtLeast(0))
                placed = true
            }
        }
        LaunchedEffect(state, placed) {
            if (!placed) return@LaunchedEffect
            snapshotFlow { state.firstVisibleItemIndex }.collect { i ->
                rowsNow.getOrNull(i)?.let { onAnchor(it.date) }
                if (i <= 2 && monthsBack < 240) monthsBack += 6
                if (i >= rowsNow.size - 6 && monthsAhead < 240) monthsAhead += 6
            }
        }

        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(bottom = 96.dp)) {
            itemsIndexed(rows, key = { _, r -> r.key }) { _, r ->
                when (r) {
                    is SRow.MonthHeader -> MonthBanner(r.month)
                    is SRow.Day -> DayBlock(vm, r, today, onOpen, onAdd)
                }
            }
        }
    }
}

@Composable
private fun MonthBanner(m: YearMonth) {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(44.dp).clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(Format.monthName(m) + if (m.year != LocalDate.now().year) " ${m.year}" else "", Modifier.padding(start = 16.dp), fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DayBlock(vm: CalendarViewModel, r: SRow.Day, today: LocalDate, onOpen: (Occurrence) -> Unit, onAdd: (LocalDate) -> Unit) {
    val isToday = r.date == today
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Column(Modifier.width(48.dp).padding(top = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            DayNumber(r.date, isToday, size = 34, fontSize = 17)
            Text(
                r.date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()).uppercase(),
                fontSize = 10.sp, textAlign = TextAlign.Center, color = if (isToday) MaterialTheme.colorScheme.primary else dimText(),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (r.occ.isEmpty()) {
                Text("Nothing planned", Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAdd(r.date) }.padding(vertical = 10.dp, horizontal = 4.dp), color = dimText(), fontSize = 14.sp)
            }
            r.occ.forEach { o -> EventCard(o, vm.colorOf(o.event), onClick = { onOpen(o) }) }
        }
    }
}
