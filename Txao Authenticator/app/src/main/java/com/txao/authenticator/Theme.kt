package com.txao.authenticator

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val TxaoBg = Color(0xFF0B0E14)
val Lavender = Color(0xFFCDB8FF)
val GlassFill = Color(0xE0121212)
val GlassStroke = Color(0x1FFFFFFF)
val GlassShape = RoundedCornerShape(24.dp)
val GlassBorder = BorderStroke(1.dp, GlassStroke)

private val Scheme = darkColorScheme(
    primary = Lavender,
    onPrimary = Color(0xFF2E1B5C),
    primaryContainer = Color(0xFF4A3A7A),
    onPrimaryContainer = Color(0xFFF1E8FF),
    background = TxaoBg,
    onBackground = Color.White,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1E1E24),
    onSurfaceVariant = Color(0xFFA1A1AA),
    error = Color(0xFFFF8A80),
)

@Composable
fun TxaoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}

/** Translucent "glass" card surface shared by the Txao apps. */
fun Modifier.glass(): Modifier = this.background(GlassFill, GlassShape).border(GlassBorder, GlassShape)
