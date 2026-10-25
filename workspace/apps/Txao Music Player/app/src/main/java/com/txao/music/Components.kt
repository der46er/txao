package com.txao.music

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cover art for [key] (a track's Uri string), decoded at about [decodeSize], with a soft gradient and a note as the fallback.
 * Size it with [modifier] (e.g. `Modifier.fillMaxWidth().aspectRatio(1f)`).
 */
@Composable
fun Artwork(key: String?, decodeSize: Dp, modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(14.dp)) {
    val ctx = LocalContext.current
    val c = MaterialTheme.colorScheme
    val px = with(LocalDensity.current) { decodeSize.roundToPx() }.coerceIn(96, 1024)
    val bmp by produceState<Bitmap?>(Art.peek(key, px), key, px) {
        value = if (key == null) null else Art.peek(key, px) ?: withContext(Dispatchers.IO) { Art.load(ctx, key, px) }
    }
    Box(modifier.clip(shape).background(Brush.linearGradient(listOf(c.primary.copy(0.30f), c.tertiary.copy(0.20f), c.secondary.copy(0.20f))))) {
        Crossfade(bmp, animationSpec = tween(220), label = "art", modifier = Modifier.fillMaxSize()) { b ->
            if (b != null) {
                Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MusicNote, null, tint = c.onSurface.copy(0.35f), modifier = Modifier.fillMaxSize(0.4f))
                }
            }
        }
    }
}


/** Wide pill button: [primary] is the filled lavender action, otherwise a quiet glass one. Squares its corners while pressed. */
@Composable
fun TxaoButton(
    text: String,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val haptics = LocalHaptics.current
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val radius by animateDpAsState(if (pressed) 16.dp else 100.dp, spring(0.62f, Spring.StiffnessMediumLow), label = "btnRadius")
    val scale by animateFloatAsState(if (pressed) 0.95f else 1f, spring(0.55f, Spring.StiffnessMedium), label = "btnScale")
    val tint = if (primary) c.primary.copy(alpha = 0.92f) else c.onSurface.copy(alpha = 0.085f)
    val content = if (primary) c.onPrimary else c.onSurface
    Row(
        modifier
            .height(52.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .glassPanel(RoundedCornerShape(radius), tint, LocalGlass.current, if (primary) c.primary else c.surfaceContainerHigh, if (pressed) 1.7f else 1f)
            .combinedClickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = {
                if (haptics) haptic.performHapticFeedback(HapticFeedbackType.VirtualKey)
                onClick()
            })
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = content, modifier = Modifier.size(22.dp))
        Text(text, color = content, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp))
    }
}

val CircleArt = CircleShape
