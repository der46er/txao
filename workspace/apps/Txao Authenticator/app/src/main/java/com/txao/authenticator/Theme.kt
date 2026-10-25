package com.txao.authenticator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TxaoBg = Color(0xFF0B0E14)
val Lavender = Color(0xFFCDB8FF)
val GlassFill = Color(0x14FFFFFF)
val GlassStroke = Color(0x24FFFFFF)
val GlassShape = RoundedCornerShape(20.dp)

/** Search pill and bars, the same translucent surface the Files and Phone apps use. */
val BarSurface = Color(0x1FFFFFFF)
val FabColor = Color(0xFFCDB8FF)
val GlassBorder = BorderStroke(1.dp, GlassStroke)

private val Scheme = darkColorScheme(
    primary = Lavender,
    onPrimary = Color(0xFF2E1B5C),
    primaryContainer = Color(0xFF4A3A7A),
    onPrimaryContainer = Color(0xFFF1E8FF),
    background = TxaoBg,
    onBackground = Color.White,
    surface = Color(0xFF141824),
    surfaceContainer = Color(0xFF1A1F2E),
    surfaceContainerHigh = Color(0xFF242938),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF242938),
    onSurfaceVariant = Color(0xFFB9BCCB),
    secondaryContainer = Color(0xFF333B50),
    onSecondaryContainer = Color(0xFFE6E8F2),
    error = Color(0xFFFF8A80),
)

@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides Scheme.onBackground, content = content,
        )
    }
}

/** Translucent "glass" card surface shared by the Txao apps. */
fun Modifier.glass(): Modifier = this.clip(GlassShape).background(GlassFill).border(GlassBorder, GlassShape)

/** The suite wallpaper: deep blue-black with soft lavender, rose and blue glows. */
@Composable
fun TxaoBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.background(TxaoBg).drawBehind {
            drawRect(Brush.radialGradient(listOf(Color(0x668FA3D6), Color.Transparent), Offset(size.width * 0.2f, size.height * 0.05f), size.width * 0.8f))
            drawRect(Brush.radialGradient(listOf(Color(0x8CB79CFF), Color.Transparent), Offset(size.width * 0.9f, size.height * 0.85f), size.width))
            drawRect(Brush.radialGradient(listOf(Color(0x66D7A6C6), Color.Transparent), Offset(size.width * 0.05f, size.height * 0.95f), size.width * 0.7f))
        },
        content = { content() },
    )
}
