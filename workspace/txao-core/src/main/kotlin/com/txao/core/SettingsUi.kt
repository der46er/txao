package com.txao.core

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * The standard TXAO settings screen (CONVENTIONS.md, "Settings screen"): Appearance, app-specific
 * sections ([extra]), Sync across devices (when [sync] is given), Privacy, About.
 * Put it inside [GlassBackground]. [dataNoun] is what the app syncs, e.g. "events" or "photos' details".
 */
@Composable
fun TxaoSettingsScreen(
    prefs: TxaoPrefs,
    appName: String,
    onBack: () -> Unit,
    sync: SyncController? = null,
    dataNoun: String = "data",
    privacyText: String = "No analytics, no ads, no accounts, no trackers. Your data stays in this app's private storage (excluded from backups). The network is used only if you turn on sync, and then only to talk to your own server, with everything encrypted by a key that never leaves your devices.",
    extra: LazyListScope.() -> Unit = {},
) {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    BackHandler { onBack() }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back") { onBack() }
            Text("Settings", style = MaterialTheme.typography.titleLarge, color = c.onSurface, modifier = Modifier.padding(start = 8.dp))
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { AppearanceSection(prefs) }
            extra()
            if (sync != null) item { SyncSection(prefs, sync, dataNoun) }
            item {
                SettingsSection("Privacy") {
                    Text(privacyText, style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
            item {
                val info = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0) }
                Text(
                    "$appName  ·  version ${info.versionName} (build ${info.longVersionCode})",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), textAlign = TextAlign.Center,
                )
                Box(Modifier.height(24.dp))
            }
        }
    }
}

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
fun SyncSection(prefs: TxaoPrefs, sync: SyncController, dataNoun: String = "data") {
    val c = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var showKey by remember { mutableStateOf(false) }
    var enterKey by remember { mutableStateOf(false) }
    var keyInput by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    var connectError by remember { mutableStateOf<String?>(null) }
    var newKeyShown by remember { mutableStateOf(false) }
    var confirmRemoveKey by remember { mutableStateOf(false) }

    SettingsSection("Sync across devices") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Optional. Sync your $dataNoun end-to-end encrypted through a server you run yourself (the Txao server, see the server folder in Txao Notes). They are encrypted on this device with your key, so the server can never read them. Nothing is sent anywhere unless you set this up. The same server and key work in every Txao app.",
                style = MaterialTheme.typography.bodyMedium, color = c.onSurfaceVariant,
            )
            OutlinedTextField(
                value = prefs.serverUrl, onValueChange = { prefs.serverUrl = it.trim(); connectError = null },
                label = { Text("Server address") }, placeholder = { Text("https://txao.example.com") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = prefs.accessCode, onValueChange = { prefs.accessCode = it.trim(); connectError = null },
                label = { Text("Server access code (if your server has one)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            )
        }
        SwitchRow("Allow http:// (unencrypted)", "Only for a server on your own home network. Your data stays encrypted either way", prefs.allowHttp) { prefs.allowHttp = it }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Sync key", color = c.onSurface, fontWeight = FontWeight.Medium)
            val key = prefs.syncKey
            if (key != null) {
                Text(if (showKey) key else key.take(9) + "••••-••••-••••-••••-••••", style = MaterialTheme.typography.bodyMedium, color = c.onSurface)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") }
                    TextButton(onClick = { copySensitive(ctx, key) }) { Text("Copy") }
                    TextButton(onClick = { confirmRemoveKey = true }) { Text("Remove") }
                }
                Text(
                    "Use the same key on every device and in every Txao app. Keep a copy somewhere safe: if you lose it, synced data can't be decrypted by anyone.",
                    style = MaterialTheme.typography.bodySmall, color = c.onSurfaceVariant,
                )
            } else if (!enterKey) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { prefs.syncKey = TxaoCrypto.generateKey(); newKeyShown = true; showKey = true }) { Text("Create a new key") }
                    TextButton(onClick = { enterKey = true }) { Text("I already have a key") }
                }
            } else {
                OutlinedTextField(
                    value = keyInput, onValueChange = { keyInput = it; connectError = null },
                    label = { Text("Paste your key") }, placeholder = { Text("TXAO-XXXX-XXXX-…") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
                    isError = keyInput.isNotBlank() && TxaoCrypto.parseKey(keyInput) == null,
                )
                Row {
                    TextButton(enabled = TxaoCrypto.parseKey(keyInput) != null, onClick = {
                        prefs.syncKey = TxaoCrypto.formatKey(TxaoCrypto.parseKey(keyInput)!!); keyInput = ""; enterKey = false
                    }) { Text("Use this key") }
                    TextButton(onClick = { enterKey = false; keyInput = "" }) { Text("Cancel") }
                }
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!prefs.syncEnabled) {
                TextButton(
                    enabled = !testing && prefs.serverUrl.isNotBlank() && prefs.syncKey != null,
                    onClick = {
                        testing = true; connectError = null
                        scope.launch {
                            val err = sync.testConnection(prefs.serverUrl, prefs.accessCode, prefs.syncKey ?: "", prefs.allowHttp)
                            testing = false
                            if (err == null) { prefs.syncEnabled = true; sync.begin() } else connectError = err
                        }
                    },
                ) { Text(if (testing) "Connecting…" else "Connect and sync") }
            } else {
                val st = sync.state
                Text(
                    when (st) {
                        is SyncState.Syncing -> "Syncing…"
                        is SyncState.Done -> if (System.currentTimeMillis() - st.at < 60_000) "Synced just now" else "Synced " + android.text.format.DateUtils.getRelativeTimeSpanString(st.at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS)
                        is SyncState.Failed -> st.message
                        else -> "Sync is on"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (st is SyncState.Failed) c.error else c.onSurfaceVariant,
                )
                Row {
                    TextButton(onClick = { scope.launch { sync.syncNow() } }) { Text("Sync now") }
                    TextButton(onClick = { prefs.syncEnabled = false }) { Text("Turn off sync") }
                }
            }
            connectError?.let { Text(it, color = c.error, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (newKeyShown) {
        AlertDialog(
            onDismissRequest = { newKeyShown = false },
            title = { Text("Save your sync key") },
            text = { Text("${prefs.syncKey?.replace("-", " ")}\n\nCopy it somewhere safe (a password manager is ideal). You'll enter it on your other devices and in your other Txao apps. If you lose it, synced data can't be recovered.") },
            confirmButton = { TextButton(onClick = { prefs.syncKey?.let { copySensitive(ctx, it) }; newKeyShown = false }) { Text("Copy and close") } },
            dismissButton = { TextButton(onClick = { newKeyShown = false }) { Text("Close") } },
        )
    }
    if (confirmRemoveKey) {
        AlertDialog(
            onDismissRequest = { confirmRemoveKey = false },
            title = { Text("Remove the key from this device?") },
            text = { Text("Sync turns off. Your data here stays, but you'll need the key to sync again. Make sure you've saved it.") },
            confirmButton = { TextButton(onClick = { prefs.syncKey = null; prefs.syncEnabled = false; confirmRemoveKey = false }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { confirmRemoveKey = false }) { Text("Cancel") } },
        )
    }
}

/** Copies text flagged as sensitive so keyboards and the clipboard preview hide it. */
fun copySensitive(ctx: Context, text: String, label: String = "Txao sync key") {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
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
