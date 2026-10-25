package com.txao.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF0B0E14)
val Lavender = Color(0xFFB79CFF)
val GlassFill = Color(0x14FFFFFF)
val GlassStroke = Color(0x1FFFFFFF)
val GlassBorder = BorderStroke(1.dp, GlassStroke)

val NowRed = Color(0xFFEA4335)

/** Readable text colour on top of an event colour. */
fun onEventColor(argb: Int): Color = if (Color(argb).luminance() > 0.55f) Color(0xFF1B1B1F) else Color.White

@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Lavender, onPrimary = Color(0xFF2E1B5C), background = Ink, surface = Ink,
            onBackground = Color(0xFFEDEBF5), onSurface = Color(0xFFEDEBF5),
            surfaceContainer = Color(0xFF171B26), surfaceContainerHigh = Color(0xFF1D2230),
            surfaceContainerHighest = Color(0xFF262B3A), outlineVariant = Color(0xFF2E3342),
            error = Color(0xFFFF8A9B),
        ),
        content = { CompositionLocalProvider(LocalContentColor provides Color(0xFFEDEBF5), content = content) },
    )
}

/** Dark ink with soft lavender / rose / blue glows, matching the launcher icon. */
@Composable
fun GlassBackground(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Ink)
            .drawBehind {
                drawRect(Brush.radialGradient(listOf(Color(0x8CB79CFF), Color.Transparent), Offset(size.width * 0.85f, size.height * 0.85f), size.width * 0.95f))
                drawRect(Brush.radialGradient(listOf(Color(0x66D7A6C6), Color.Transparent), Offset(0f, size.height * 0.77f), size.width * 0.7f))
                drawRect(Brush.radialGradient(listOf(Color(0x668FA3D6), Color.Transparent), Offset(size.width * 0.08f, size.height * 0.04f), size.width * 0.7f))
            },
    ) { content() }
}

fun Modifier.glass(shape: Shape, fill: Color = GlassFill): Modifier = this.clip(shape).background(fill).border(GlassBorder, shape)
