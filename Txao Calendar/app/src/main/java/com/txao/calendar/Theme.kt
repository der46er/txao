package com.txao.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF0B0E14)
val Lavender = Color(0xFFB79CFF)
val GlassFill = Color(0x14FFFFFF)
val GlassStroke = Color(0x1FFFFFFF)
val GlassBorder = BorderStroke(1.dp, GlassStroke)

/** Event colours, chosen by index so they survive a palette tweak. */
val EventColors = listOf(
    Color(0xFFB79CFF), Color(0xFF8FA3D6), Color(0xFF7FD6C2), Color(0xFFE6C77A),
    Color(0xFFF0A07C), Color(0xFFD7A6C6), Color(0xFF9AD27A), Color(0xFFE88A9A),
)

fun eventColor(i: Int): Color = EventColors[Math.floorMod(i, EventColors.size)]

@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lavender, onPrimary = Color(0xFF2E1B5C), background = Ink, surface = Ink,
            onBackground = Color(0xFFEDEBF5), onSurface = Color(0xFFEDEBF5),
            surfaceContainer = Color(0xFF171B26), surfaceContainerHigh = Color(0xFF1D2230),
            error = Color(0xFFFF8A9B),
        ),
        content = content,
    )
}

/** Dark ink with soft lavender / rose / blue glows, matching the launcher icon. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink)
            .background(Brush.radialGradient(listOf(Color(0x55B79CFF), Color.Transparent), center = Offset(900f, 1900f), radius = 1100f))
            .background(Brush.radialGradient(listOf(Color(0x40D7A6C6), Color.Transparent), center = Offset(0f, 1700f), radius = 900f))
            .background(Brush.radialGradient(listOf(Color(0x408FA3D6), Color.Transparent), center = Offset(100f, 100f), radius = 900f)),
    ) { content() }
}

fun Modifier.glass(shape: Shape, fill: Color = GlassFill): Modifier = this.clip(shape).background(fill).border(GlassBorder, shape)
