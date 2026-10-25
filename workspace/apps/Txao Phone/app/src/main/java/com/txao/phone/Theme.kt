package com.txao.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val Ink = Color(0xFF0B0E14)
val Lavender = Color(0xFFCDB8FF)
val OnLavender = Color(0xFF2E1B5C)
val Danger = Color(0xFFFF8A9B)
val Mint = Color(0xFF8FE3B5)
val OnMint = Color(0xFF06301B)
val Muted = Color(0xB3FFFFFF)

private val scheme = darkColorScheme(
    primary = Lavender, onPrimary = OnLavender,
    background = Ink, onBackground = Color(0xFFF2F2F7),
    surface = Ink, onSurface = Color(0xFFF2F2F7),
    onSurfaceVariant = Muted, error = Danger,
)

/** Dark ink with soft lavender / rose / blue glows, like the other Txao apps. */
@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme) {
        Box(
            Modifier.fillMaxSize().background(Ink)
                .background(Brush.radialGradient(listOf(Color(0x8CB79CFF), Color.Transparent), center = Offset(1000f, 2200f), radius = 1100f))
                .background(Brush.radialGradient(listOf(Color(0x66D7A6C6), Color.Transparent), center = Offset(0f, 2000f), radius = 800f))
                .background(Brush.radialGradient(listOf(Color(0x668FA3D6), Color.Transparent), center = Offset(100f, 100f), radius = 800f)),
        ) { CompositionLocalProvider(LocalContentColor provides scheme.onBackground) { content() } }
    }
}

/** Translucent frosted surface. */
fun Modifier.glass(radius: Dp = 24.dp, fill: Color = Color(0x14FFFFFF)): Modifier {
    val shape = RoundedCornerShape(radius)
    return this.clip(shape).background(fill).border(1.dp, Color(0x24FFFFFF), shape)
}
