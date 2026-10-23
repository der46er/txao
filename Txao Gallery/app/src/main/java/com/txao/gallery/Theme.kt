package com.txao.gallery

import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GlassBg = Color(0xFF0B0E14)
val Accent = Color(0xFFCDB8FF)
val AccentText = Color(0xFF2E1B5C)
val GlassCard = Color(0x33FFFFFF)
val GlassBorder = Color(0x1FFFFFFF)

private val TxaoDark = darkColorScheme(
    background = GlassBg,
    surface = Color(0xFF141824),
    surfaceContainer = Color(0xFF1A1F2E),
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Accent,
    onPrimary = AccentText,
)

/** The gallery is always dark so photos sit on a neutral background. */
@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TxaoDark, content = content)
}

/** The suite wallpaper: deep blue-black with soft lavender, rose and blue glows. */
@Composable
fun GlassBackground(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier.background(GlassBg).drawBehind {
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x668FA3D6), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.05f), size.width * 0.8f))
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x8CB79CFF), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.85f), size.width))
            drawRect(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0x66D7A6C6), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.95f), size.width * 0.7f))
        },
    ) { content() }
}
