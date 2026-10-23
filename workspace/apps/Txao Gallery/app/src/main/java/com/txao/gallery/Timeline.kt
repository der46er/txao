package com.txao.gallery

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val Muted = Color(0xB3FFFFFF)
private val HandleHeight = 44.dp

/**
 * The photo timeline: day headers, a draggable date scrubber on the right edge and
 * pinch-to-change-columns, like a cloud photo library but fully on device.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Timeline(
    items: List<MediaItem>,
    grouped: Boolean,
    columns: Int,
    favorites: Set<Long>,
    selected: Set<Long>,
    onClick: (MediaItem, Int) -> Unit,
    onLongClick: (MediaItem) -> Unit,
    onColumns: (Int) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyGridState = rememberLazyGridState(),
    header: (@Composable () -> Unit)? = null,
    bottomPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val entries = remember(items, grouped) { buildEntries(items, grouped) }
    val px = (1080 / columns).coerceAtLeast(160)
    val selecting = selected.isNotEmpty()
    val scope = rememberCoroutineScope()
    val columnsNow by rememberUpdatedState(columns)
    val onColumnsNow by rememberUpdatedState(onColumns)
    val headerOffset = if (header != null) 1 else 0

    var trackHeight by remember { mutableStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableStateOf(0f) }
    var dragMonth by remember { mutableStateOf("") }
    val handleHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { HandleHeight.roundToPx() }

    Box(
        modifier.pointerInput(Unit) {
            // Observe pinch on the initial pass without consuming, so scrolling still works.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var acc = 1f
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.count { it.pressed } > 1) {
                        acc *= event.calculateZoom()
                        if (acc > 1.35f) { onColumnsNow(columnsNow - 1); acc = 1f }
                        else if (acc < 0.72f) { onColumnsNow(columnsNow + 1); acc = 1f }
                    }
                } while (event.changes.any { it.pressed })
            }
        },
    ) {
        LazyVerticalGrid(
            state = state,
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (header != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "header") { header() }
            }
            entries.forEachIndexed { i, entry ->
                when (entry) {
                    is Entry.Header -> item(span = { GridItemSpan(maxLineSpan) }, key = "h$i") {
                        Text(
                            entry.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 14.dp, top = 16.dp, bottom = 6.dp),
                        )
                    }
                    is Entry.Cell -> item(key = "m${entry.item.id}") {
                        Thumb(
                            entry.item, px,
                            Modifier.aspectRatio(1f).combinedClickable(
                                onClick = { onClick(entry.item, entry.index) },
                                onLongClick = { onLongClick(entry.item) },
                            ),
                            favorite = entry.item.id in favorites,
                            selected = if (selecting) entry.item.id in selected else null,
                        )
                    }
                }
            }
        }

        if (grouped && entries.size > 40) {
            val position = if (dragging) dragFraction else {
                val total = (entries.size + headerOffset - 1).coerceAtLeast(1)
                (state.firstVisibleItemIndex.toFloat() / total).coerceIn(0f, 1f)
            }
            Box(
                Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(36.dp)
                    .onSizeChanged { trackHeight = it.height }
                    .pointerInput(entries) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, _ ->
                            val f = (change.position.y / size.height).coerceIn(0f, 1f)
                            dragFraction = f
                            val idx = scrubIndex(f, entries.size)
                            dragMonth = entries[idx].month
                            scope.launch { state.scrollToItem(idx + headerOffset) }
                        }
                    },
            ) {
                val y = (position * (trackHeight - handleHeightPx)).toInt().coerceAtLeast(0)
                if (dragging) {
                    Box(
                        Modifier.align(Alignment.TopEnd).offset { IntOffset(-44.dp.roundToPx(), y) }
                            .size(width = 150.dp, height = HandleHeight)
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.CenterEnd,
                    ) {
                        Text(
                            dragMonth, color = AccentText, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Accent).padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
                Box(
                    Modifier.align(Alignment.TopEnd).offset { IntOffset(0, y) }
                        .size(width = if (dragging) 30.dp else 18.dp, height = HandleHeight).padding(vertical = 8.dp, horizontal = 4.dp)
                        .clip(RoundedCornerShape(50)).background(if (dragging) Accent else Color(0x66FFFFFF)),
                )
            }
        }
    }
}
