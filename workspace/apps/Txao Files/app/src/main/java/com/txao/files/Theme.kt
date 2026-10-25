package com.txao.files

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

val GlassBg = Color(0xFF0B0E14)
val Accent = Color(0xFFCDB8FF)
val AccentText = Color(0xFF2E1B5C)

/** Pale blue-lavender folder colour, like the folder glyphs of the reference file manager. */
val FolderTint = Color(0xFFA9B1DB)

/** Search pill, chips and bars sit on this translucent surface so the wallpaper shows through. */
val BarSurface = Color(0xCC2A2F3E)
val NavSurface = Color(0xE61A1F2E)

private val TxaoDark = darkColorScheme(
    background = GlassBg,
    surface = Color(0xFF141824),
    surfaceContainer = Color(0xFF1A1F2E),
    surfaceContainerHigh = Color(0xFF242938),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFB9BCCB),
    primary = Accent,
    onPrimary = AccentText,
    primaryContainer = Color(0xFF435275),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondaryContainer = Color(0xFF333B50),
    onSecondaryContainer = Color(0xFFE6E8F2),
)

@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TxaoDark) {
        CompositionLocalProvider(LocalContentColor provides TxaoDark.onBackground, content = content)
    }
}

/** The suite wallpaper: deep blue-black with soft lavender, rose and blue glows. */
@Composable
fun FilesBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(GlassBg).drawBehind {
            drawRect(Brush.radialGradient(listOf(Color(0x668FA3D6), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.05f), size.width * 0.8f))
            drawRect(Brush.radialGradient(listOf(Color(0x8CB79CFF), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.85f), size.width))
            drawRect(Brush.radialGradient(listOf(Color(0x66D7A6C6), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.95f), size.width * 0.7f))
        },
    ) { content() }
}
