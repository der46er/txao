package com.txao.authenticator

import android.app.KeyguardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(
    settings: Settings, onBack: () -> Unit,
    onImport: () -> Unit, onExportEncrypted: () -> Unit, onExportPlain: () -> Unit,
) {
    val context = LocalContext.current
    val canLock = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("SETTINGS", fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        Section("Codes") {
            SwitchRow("Hide codes", "Show dots until you tap a card", settings.hideCodes, settings::setHideCodes)
            SwitchRow("Show next code", "Preview the upcoming code in the last 10 seconds", settings.showNext, settings::setShowNext)
            SwitchRow("Group digits", "123 456 instead of 123456", settings.groupDigits, settings::setGroupDigits)
        }
        Section("Privacy") {
            SwitchRow(
                "App lock",
                if (canLock) "Ask for your phone's PIN, pattern or fingerprint to open the app" else "Set a screen lock on your phone first",
                settings.appLock && canLock, settings::setAppLock, enabled = canLock,
            )
            SwitchRow("Clear clipboard", "Wipe a copied code after 30 seconds", settings.clearClipboard, settings::setClearClipboard)
            Note("Screenshots and the recent-apps preview are always blocked.")
        }
        Section("Data") {
            ActionRow("Import accounts", "Setup links, Google Authenticator export, Aegis, or a Txao backup", onImport)
            ActionRow("Export encrypted backup", "Password-protected file you can keep anywhere", onExportEncrypted)
            ActionRow("Export as plain text", "Unencrypted otpauth links. Handle with care", onExportPlain)
        }
        Section("About") {
            Note("Txao Authenticator 1.1.0. Accounts never leave this phone unless you export them. No network access, no accounts, no analytics.")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title.uppercase(), fontSize = 12.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().glass().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun ActionRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
}
