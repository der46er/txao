package com.txao.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSection(prefs: TxaoPrefs) {
    val c = MaterialTheme.colorScheme
    SettingsSection("Appearance") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme", color = c.onSurface)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf("Dark", "Light", "System").forEachIndexed { i, label ->
                    SegmentedButton(selected = prefs.theme == i, onClick = { prefs.theme = i }, shape = SegmentedButtonDefaults.itemShape(i, 3)) { Text(label) }
                }
            }
        }
        SwitchRow("Material You colors", "Use your wallpaper's colors (Android 12+)", prefs.dynamicColor) { prefs.dynamicColor = it }
        SwitchRow("Glass effect", "Frosted translucent surfaces. Turn off for solid ones", prefs.glass) { prefs.glass = it }
        SwitchRow("Haptic feedback", "Vibrate lightly on button presses", prefs.haptics) { prefs.haptics = it }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        GlassCard(Modifier.fillMaxWidth()) { Column { content() } }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = c.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
