package com.txao.core

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalGlass = compositionLocalOf { true }
val LocalHaptics = compositionLocalOf { true }
val LocalDarkTheme = compositionLocalOf { true }

/** Dark canvas with soft coloured light behind the glass surfaces. */
@Composable
fun GlassBackground(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit) {
    val c = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    Box(
        modifier
            .fillMaxSize()
            .background(c.background)
            .then(
                if (!glass) Modifier else Modifier.drawBehind {
                    fun orb(color: Color, cx: Float, cy: Float, r: Float) {
                        drawRect(
                            Brush.radialGradient(
                                listOf(color, Color.Transparent),
                                center = Offset(size.width * cx, size.height * cy),
                                radius = size.minDimension * r,
                            )
                        )
                    }
                    orb(c.primary.copy(alpha = 0.26f), 0.10f, 0.62f, 0.9f)
                    orb(c.tertiary.copy(alpha = 0.18f), 0.95f, 0.78f, 0.8f)
                    orb(c.secondary.copy(alpha = 0.18f), 0.75f, 0.08f, 0.7f)
                }
            )
    ) { content() }
}

/** Frosted glass fill with a light edge. [solid] is used when the glass effect is switched off. */
fun Modifier.glassPanel(shape: Shape, tint: Color, glass: Boolean, solid: Color, strength: Float = 1f, edge: Color = Color.White): Modifier {
    if (!glass) return this.clip(shape).background(solid)
    return this
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    tint.copy(alpha = (tint.alpha * 1.4f * strength).coerceAtMost(1f)),
                    tint.copy(alpha = (tint.alpha * 0.85f * strength).coerceAtMost(1f)),
                )
            ),
            shape,
        )
        .border(
            1.dp,
            Brush.linearGradient(listOf(edge.copy(0.32f), edge.copy(0.04f), edge.copy(0.14f))),
            shape,
        )
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, radius: Dp = 28.dp, tint: Color? = null, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    val t = tint ?: c.onSurface.copy(alpha = 0.07f)
    Box(modifier.glassPanel(RoundedCornerShape(radius), t, LocalGlass.current, c.surfaceContainer)) { content() }
}

/** Round icon button that squares its corners while pressed, matching the calculator keys. */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    tinted: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(
        if (pressed) 14.dp else 100.dp,
        spring(0.62f, Spring.StiffnessMediumLow),
        label = "iconRadius",
    )
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "iconScale")
    val shape = RoundedCornerShape(radius)
    val bg = if (tinted) c.primary.copy(alpha = 0.28f) else Color.Transparent
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.35f }
            .clip(shape)
            .background(if (pressed) c.onSurface.copy(alpha = 0.12f) else bg)
            .semantics { contentDescription = description }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = {
                    if (haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                    onClick()
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (tinted) c.primary else c.onSurfaceVariant, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun Fill(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }

/** Motion specs used across every TXAO app (values taken from Calculator keys and the Notes router). */
object TxaoMotion {
    /** Press: corners morph 100dp -> 14..16dp. */
    val pressRadius = spring<androidx.compose.ui.unit.Dp>(0.62f, Spring.StiffnessMediumLow)
    /** Press: scale to 0.92 (icon buttons) / 0.95 (big keys). */
    val pressScale = spring<Float>(0.55f, Spring.StiffnessMedium)
    /** Screen change: fade in 240ms + scale from 0.95 (spring 0.8 / MediumLow) while the old screen fades out in 120ms. */
    const val ScreenInMs = 240
    const val ScreenOutMs = 120
    const val ScreenInitialScale = 0.95f
    val screenScale = spring<Float>(0.8f, Spring.StiffnessMediumLow)
    /** First frame: content fades in over 300ms on top of the already-dark window background. */
    const val AppearMs = 300
}
