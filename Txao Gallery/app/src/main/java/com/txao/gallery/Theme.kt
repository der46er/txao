package com.txao.gallery

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GlassBg = Color(0xFF0D0D0D)
val GlassCard = Color(0xE0121212)
val GlassBorder = Color(0x1FFFFFFF)

private val TxaoDark = darkColorScheme(
    background = GlassBg,
    surface = Color(0xFF121212),
    surfaceContainer = Color(0xFF181818),
    onBackground = Color.White,
    onSurface = Color.White,
    primary = Color.White,
    onPrimary = Color.Black,
)

/** The gallery is always dark so photos sit on a neutral background. */
@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = TxaoDark, content = content)
}
