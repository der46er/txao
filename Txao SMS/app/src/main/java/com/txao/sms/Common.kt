package com.txao.sms

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import java.util.Date

@Composable
fun Header(title: String, onBack: (() -> Unit)? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        else Spacer(Modifier.size(12.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

private val avatarHues = listOf(
    Color(0xFFB79CFF), Color(0xFF8FA3D6), Color(0xFFD7A6C6), Color(0xFF7FD1C7),
    Color(0xFFE5B98A), Color(0xFF9CCB8F), Color(0xFFE08F9B), Color(0xFF8CC4E8),
)

/** Round contact picture: a tinted circle with the first letter, a person icon for numbers, a tick when selected. */
@Composable
fun Avatar(label: String, size: Int = 48, selected: Boolean = false) {
    val hue = avatarHues[(label.hashCode() and 0x7fffffff) % avatarHues.size]
    val letter = label.firstOrNull { it.isLetter() }?.uppercase()
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(if (selected) Lavender else hue.copy(alpha = 0.26f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, "Selected", tint = OnLavender, modifier = Modifier.size((size / 2).dp))
            letter != null -> Text(letter, color = hue, fontWeight = FontWeight.Medium, fontSize = (size / 2.3).sp)
            else -> Icon(Icons.Default.Person, null, tint = hue, modifier = Modifier.size((size / 1.9).dp))
        }
    }
}

@Composable
fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
)

@Composable
fun Confirm(title: String, text: String, action: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(action, color = Danger) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

fun listTime(context: Context, ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    return when {
        sameDay(now.timeInMillis, ms) -> DateFormat.getTimeFormat(context).format(Date(ms))
        now.timeInMillis - ms < 6 * 86_400_000L -> DateFormat.format("EEE", ms).toString()
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR) -> DateFormat.format("d MMM", ms).toString()
        else -> DateFormat.getDateFormat(context).format(Date(ms))
    }
}

/** "Today • 14:05", "Yesterday • 09:12", "Mon, 3 Mar • 18:40": the divider between bursts of messages. */
fun burstLabel(context: Context, ms: Long): String {
    val time = DateFormat.getTimeFormat(context).format(Date(ms))
    val now = System.currentTimeMillis()
    val day = when {
        sameDay(now, ms) -> "Today"
        sameDay(now - 86_400_000L, ms) -> "Yesterday"
        else -> DateFormat.format("EEE, d MMM", ms).toString()
    }
    return "$day • $time"
}

fun sameDay(a: Long, b: Long): Boolean {
    val x = Calendar.getInstance().apply { timeInMillis = a }
    val y = Calendar.getInstance().apply { timeInMillis = b }
    return x.get(Calendar.YEAR) == y.get(Calendar.YEAR) && x.get(Calendar.DAY_OF_YEAR) == y.get(Calendar.DAY_OF_YEAR)
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
