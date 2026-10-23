package com.txao.phone

import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Glass = Color(0xE0121212)
val GlassBorder = Color(0x1FFFFFFF)
val Accent = Color(0xFF3DDC84)
val Danger = Color(0xFFFF4D4D)
val CardShape = RoundedCornerShape(20.dp)

private val scheme: ColorScheme = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black,
    background = Color(0xFF0D0D0D), onBackground = Color.White,
    surface = Color(0xFF121212), onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1C), onSurfaceVariant = Color(0xFFB0B0B0),
)

@Composable
fun TxaoTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = scheme, content = content)
