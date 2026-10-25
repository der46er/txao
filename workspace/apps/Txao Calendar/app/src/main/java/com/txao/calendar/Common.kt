package com.txao.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

val DimAlpha = 0.62f

@Composable
fun dimText(): Color = MaterialTheme.colorScheme.onSurface.copy(alpha = DimAlpha)

fun titleOf(e: Event): String = e.title.ifEmpty { "(No title)" }

/** Filled event card used by the schedule view, the day sheet and search results (Google-style colour block). */
@Composable
fun EventCard(o: Occurrence, color: Int, modifier: Modifier = Modifier, subtitle: String? = null, onClick: () -> Unit) {
    val fg = onEventColor(color)
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(color)).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(titleOf(o.event), color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle ?: Format.timeRange(o), color = fg.copy(alpha = .85f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Day number in a circle; filled when it is today. */
@Composable
fun DayNumber(day: LocalDate, today: Boolean, size: Int = 28, fontSize: Int = 14, dim: Boolean = false, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size.dp).clip(CircleShape).background(if (today) MaterialTheme.colorScheme.primary else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            day.dayOfMonth.toString(), fontSize = fontSize.sp, textAlign = TextAlign.Center,
            fontWeight = if (today) FontWeight.Bold else FontWeight.Normal,
            color = if (today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (dim) .4f else 1f),
        )
    }
}

/** Small coloured square/dot, used for calendar and event colours. */
@Composable
fun ColorDot(argb: Int, size: Int = 12, modifier: Modifier = Modifier) {
    Box(modifier.size(size.dp).clip(CircleShape).background(Color(argb)))
}
