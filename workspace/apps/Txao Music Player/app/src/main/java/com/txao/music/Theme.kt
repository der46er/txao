package com.txao.music

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Glass = Color(0xE0121212)
val GlassBorder = Color(0x1FFFFFFF)

private val scheme = darkColorScheme(
    primary = Color.White,
    onPrimary = Color.Black,
    background = Color(0xFF0D0D0D),
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFB5B5B5),
)

/** The app is dark-only, matching the other TXAO apps. */
@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
