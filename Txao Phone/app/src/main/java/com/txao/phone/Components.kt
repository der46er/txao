package com.txao.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val AvatarTints = listOf(
    Color(0xFFB79CFF), Color(0xFF8FA3D6), Color(0xFFD7A6C6), Color(0xFF8FE3B5), Color(0xFFF2C58F), Color(0xFF8FD6E3),
)

/** Round initial avatar, tinted by name so each person keeps a colour. */
@Composable
fun Avatar(name: String, size: Dp = 44.dp) {
    val tint = AvatarTints[(name.hashCode() and 0x7fffffff) % AvatarTints.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "#",
            fontSize = (size.value * 0.4f).sp, fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ActionChip(icon: ImageVector, label: String, tint: Color = Color.White, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Box(Modifier.size(52.dp).glass(26.dp), contentAlignment = Alignment.Center) { Icon(icon, label, tint = tint) }
        Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text, Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Lavender,
    )
}

@Composable
fun PersonRow(name: String, subtitle: String, onClick: () -> Unit, nameColor: Color = Color.Unspecified, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(name)
        Column(Modifier.weight(1f, fill = true)) {
            Text(name, color = nameColor, maxLines = 1)
            if (subtitle.isNotEmpty()) Text(subtitle, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}
