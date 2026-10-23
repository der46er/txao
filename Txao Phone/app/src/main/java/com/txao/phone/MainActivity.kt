package com.txao.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var initialNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialNumber = intent?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(PhoneUtil::normalize).orEmpty()
        setContent { TxaoTheme { App(initialNumber) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.takeIf { it.scheme == "tel" }?.let { initialNumber = PhoneUtil.normalize(it.schemeSpecificPart) }
    }
}

private val PERMS = arrayOf(
    Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.WRITE_CALL_LOG, Manifest.permission.CALL_PHONE,
)

private fun hasAll(ctx: Context) = PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

private fun placeCall(ctx: Context, number: String) {
    val n = PhoneUtil.normalize(number)
    if (n.isEmpty()) return
    val uri = Uri.fromParts("tel", n, null)
    try {
        ctx.getSystemService(TelecomManager::class.java).placeCall(uri, null)
    } catch (_: SecurityException) {
        ctx.startActivity(Intent(Intent.ACTION_DIAL, uri))
    }
}

@Composable
private fun App(initialNumber: String) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAll(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAll(ctx) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val rm = ctx.getSystemService(RoleManager::class.java)
    val isDefault = rm.isRoleHeld(RoleManager.ROLE_DIALER)

    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS) }

    var tab by remember { mutableIntStateOf(0) }
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = Glass) {
                listOf("Keypad" to Icons.Filled.Dialpad, "Recents" to Icons.Filled.History, "Contacts" to Icons.Filled.Contacts)
                    .forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label) })
                    }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (!isDefault && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp).clip(CardShape).background(Glass).border(1.dp, GlassBorder, CardShape).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Make Txao Phone your default phone app to handle calls.", Modifier.weight(1f), fontSize = 13.sp)
                    TextButton({ roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) }) { Text("Set") }
                }
            }
            when {
                tab == 0 -> Keypad(number, { number = it }) { placeCall(ctx, number) }
                !granted -> NeedPermissions { permLauncher.launch(PERMS) }
                tab == 1 -> Recents(onCall = { placeCall(ctx, it) })
                else -> Contacts(onCall = { placeCall(ctx, it) })
            }
        }
    }
}

@Composable
private fun NeedPermissions(onGrant: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Contacts, call log and phone access are needed to show your people and recent calls. Nothing leaves your device.", fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Button(onGrant) { Text("Allow access") }
    }
}

@Composable
private fun Keypad(number: String, onChange: (String) -> Unit, onCall: () -> Unit) {
    val ctx = LocalContext.current
    val contacts = remember(number.isEmpty()) { if (hasAll(ctx)) Data.contacts(ctx) else emptyList() }
    val matches = remember(number, contacts) {
        if (number.isEmpty()) emptyList() else contacts.filter { PhoneUtil.matches(number, it.name, it.number) }.take(3)
    }
    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            matches.forEach { c ->
                Text("${c.name} · ${PhoneUtil.pretty(c.number)}", Modifier.fillMaxWidth().clickable { onChange(PhoneUtil.normalize(c.number)) }.padding(8.dp), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(number.ifEmpty { " " }, fontSize = 36.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(12.dp))
        val keys = listOf("1" to "", "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL", "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "*" to "", "0" to "+", "#" to "")
        keys.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { (k, sub) ->
                    Column(
                        Modifier.padding(vertical = 6.dp).size(72.dp).clip(CircleShape).background(Glass).border(1.dp, GlassBorder, CircleShape)
                            .clickable { onChange(number + k) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        if (sub.isNotEmpty()) Text(sub, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.size(48.dp))
            Box(Modifier.size(72.dp).clip(CircleShape).background(Accent).clickable(onClick = onCall), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Call, "Call", tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            IconButton({ onChange(number.dropLast(1)) }, Modifier.size(48.dp)) { Icon(Icons.AutoMirrored.Filled.Backspace, "Delete") }
        }
    }
}

@Composable
private fun Recents(onCall: (String) -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(Data.recents(ctx)) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("RECENTS", Modifier.weight(1f), fontSize = 20.sp, fontWeight = FontWeight.Black)
            TextButton({ Data.clearRecents(ctx); items = emptyList() }) { Text("Clear") }
        }
        if (items.isEmpty()) Text("No calls yet.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn {
            items(items, key = { it.id }) { c ->
                val label = c.name?.takeIf { it.isNotBlank() } ?: PhoneUtil.pretty(c.number).ifEmpty { "Unknown" }
                val icon = when { c.missed -> Icons.AutoMirrored.Filled.CallMissed; c.outgoing -> Icons.AutoMirrored.Filled.CallMade; else -> Icons.AutoMirrored.Filled.CallReceived }
                Row(Modifier.fillMaxWidth().clickable { onCall(c.number) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = if (c.missed) Danger else Accent)
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(label, color = if (c.missed) Danger else Color.Unspecified)
                        Text(
                            DateUtils.getRelativeTimeSpanString(c.date).toString() + if (c.duration > 0) " · " + PhoneUtil.duration(c.duration) else "",
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(Icons.Filled.Call, "Call back", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Contacts(onCall: (String) -> Unit) {
    val ctx = LocalContext.current
    val all = remember { Data.contacts(ctx) }
    var q by remember { mutableStateOf("") }
    val shown = remember(q, all) {
        if (q.isBlank()) all else all.filter { it.name.contains(q, true) || PhoneUtil.normalize(it.number).contains(PhoneUtil.normalize(q)) && PhoneUtil.normalize(q).isNotEmpty() }
    }
    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Search contacts") }, singleLine = true, leadingIcon = { Icon(Icons.Filled.Search, null) })
        LazyColumn {
            items(shown, key = { "${it.id}/${it.number}" }) { c ->
                Row(Modifier.fillMaxWidth().clickable { onCall(c.number) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(Glass).border(1.dp, GlassBorder, CircleShape), contentAlignment = Alignment.Center) {
                        Text(c.name.take(1).uppercase(), fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(c.name)
                        Text(PhoneUtil.pretty(c.number), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
