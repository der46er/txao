package com.txao.music

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** What the theme needs from an app's settings class. Implement this on it. */
interface TxaoLook {
    /** 0 = dark (default), 1 = light, 2 = follow system */
    val theme: Int
    val dynamicColor: Boolean
    val glass: Boolean
    val haptics: Boolean
}

val TxaoFallbackLight = lightColorScheme(
    primary = Color(0xFF5B4B9A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFF5E6A8C),
    secondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFF8A5A7A),
    background = Color(0xFFF6F4FB),
    surface = Color(0xFFF6F4FB),
    surfaceContainer = Color(0xFFECE9F4),
    surfaceContainerHigh = Color(0xFFE6E3EF),
)

val TxaoFallbackDark = darkColorScheme(
    primary = Color(0xFFE9DDFF),
    onPrimary = Color(0xFF2E1B5C),
    primaryContainer = Color(0xFF435275),
    secondary = Color(0xFF8FA3D6),
    secondaryContainer = Color(0xFF333B50),
    tertiary = Color(0xFFD7A6C6),
    background = Color(0xFF0B0E14),
    surface = Color(0xFF0B0E14),
    onSurface = Color(0xFFE6E8F2),
    surfaceContainer = Color(0xFF161A22),
    surfaceContainerHigh = Color(0xFF20252E),
)

@Composable
fun isTxaoDark(look: TxaoLook): Boolean = when (look.theme) {
    0 -> true
    1 -> false
    else -> isSystemInDarkTheme()
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TxaoTheme(look: TxaoLook, content: @Composable () -> Unit) {
    val dark = isTxaoDark(look)
    val scheme = when {
        look.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        dark -> TxaoFallbackDark
        else -> TxaoFallbackLight
    }
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive()) {
        CompositionLocalProvider(
            LocalGlass provides look.glass,
            LocalHaptics provides look.haptics,
            LocalDarkTheme provides dark,
            content = content,
        )
    }
}
