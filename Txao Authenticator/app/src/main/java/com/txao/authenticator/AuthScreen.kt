package com.txao.authenticator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun AuthScreen(vm: AuthViewModel) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Lavender, contentColor = MaterialTheme.colorScheme.onPrimary) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Text(
                "AUTHENTICATOR",
                modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
                fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp,
            )
            if (vm.loadFailed) {
                Text(
                    "Your saved accounts could not be unlocked, so changes are disabled to protect them. " +
                        "This can happen after a device restore; the keys do not transfer between phones.",
                    modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                )
            } else if (vm.accounts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No accounts yet.\nTap + to add one from a setup key, an otpauth link or a QR screenshot.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(vm.accounts, key = { it.id }) { account ->
                    AccountCard(
                        account = account,
                        now = now,
                        onCopy = { copyCode(context, account.code(now)) },
                        onNext = { vm.nextCounter(account) },
                        onDelete = { pendingDelete = account },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddAccountDialog(
            onDismiss = { showAdd = false },
            onAdd = { vm.add(it); showAdd = false },
        )
    }

    pendingDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove ${acc.title}?") },
            text = {
                Text(
                    "You will no longer get codes for this account here. Make sure you can still sign in " +
                        "to the service (or have its recovery codes) before removing it.",
                )
            },
            confirmButton = { TextButton(onClick = { vm.remove(acc); pendingDelete = null }) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }

    vm.incoming?.let { text ->
        val parsed = remember(text) { Account.fromUri(text) }
        if (parsed == null) {
            LaunchedEffect(text) {
                Toast.makeText(context, "That link is not a valid otpauth account", Toast.LENGTH_LONG).show()
                vm.incoming = null
            }
        } else {
            AlertDialog(
                onDismissRequest = { vm.incoming = null },
                title = { Text("Add this account?") },
                text = { Text("${parsed.title}${if (parsed.subtitle.isNotEmpty()) "\n${parsed.subtitle}" else ""}") },
                confirmButton = { TextButton(onClick = { vm.add(parsed); vm.incoming = null }) { Text("Add") } },
                dismissButton = { TextButton(onClick = { vm.incoming = null }) { Text("Cancel") } },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountCard(account: Account, now: Long, onCopy: () -> Unit, onNext: () -> Unit, onDelete: () -> Unit) {
    val remaining = Otp.secondsRemaining(now, account.period)
    Row(
        Modifier.fillMaxWidth().glass().combinedClickable(onClick = onCopy, onLongClick = onDelete).padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(account.title, fontWeight = FontWeight.Black, fontSize = 15.sp)
            if (account.subtitle.isNotEmpty()) {
                Text(account.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            Spacer(Modifier.size(6.dp))
            Text(
                Otp.format(account.code(now)).ifEmpty { "—" },
                fontSize = 34.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                color = if (account.type == OtpType.TOTP && remaining <= 5) MaterialTheme.colorScheme.error else Lavender,
            )
        }
        if (account.type == OtpType.TOTP) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { remaining / account.period.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = if (remaining <= 5) MaterialTheme.colorScheme.error else Lavender,
                    trackColor = Color(0x1FFFFFFF),
                )
                Text(remaining.toString(), fontSize = 13.sp)
            }
        } else {
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next code") }
        }
        IconButton(onClick = onDelete, modifier = Modifier.alpha(0.6f)) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove")
        }
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onAdd: (Account) -> Unit) {
    val context = LocalContext.current
    var byLink by remember { mutableStateOf(false) }
    var issuer by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var link by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(OtpType.TOTP) }
    var algorithm by remember { mutableStateOf(OtpAlgorithm.SHA1) }
    var digits by remember { mutableStateOf(6) }
    var period by remember { mutableStateOf("30") }
    var advanced by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching { QrDecoder.decode(context.contentResolver, uri) }.getOrNull()
            if (text == null) error = "No QR code found in that image"
            else {
                link = text
                byLink = true
                error = null
            }
        }
    }

    fun submit() {
        if (byLink) {
            val acc = Account.fromUri(link)
            if (acc == null) error = "Not a valid otpauth link" else onAdd(acc)
            return
        }
        val clean = secret.filter { it != ' ' && it != '-' }.trimEnd('=').uppercase()
        val p = period.toIntOrNull() ?: 0
        when {
            Base32.decode(clean) == null -> error = "The setup key must use the letters A–Z and digits 2–7"
            type == OtpType.TOTP && p <= 0 -> error = "Period must be a positive number of seconds"
            issuer.isBlank() && label.isBlank() -> error = "Enter a name for the account"
            else -> onAdd(
                Account(
                    issuer = issuer.trim(), label = label.trim(), secret = clean, type = type,
                    algorithm = algorithm, digits = digits, period = if (p > 0) p else 30,
                ),
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !byLink, onClick = { byLink = false }, label = { Text("Setup key") })
                    FilterChip(selected = byLink, onClick = { byLink = true }, label = { Text("Link") })
                }
                if (byLink) {
                    OutlinedTextField(
                        value = link, onValueChange = { link = it; error = null },
                        label = { Text("otpauth:// link") }, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { pickImage.launch("image/*") }) { Text("Read QR from image…") }
                } else {
                    OutlinedTextField(issuer, { issuer = it }, label = { Text("Service (e.g. GitHub)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(label, { label = it }, label = { Text("Account (e.g. you@mail.com)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        secret, { secret = it; error = null }, label = { Text("Setup key") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide options" else "More options") }
                    if (advanced) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OtpType.entries.forEach {
                                FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it.name) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OtpAlgorithm.entries.forEach {
                                FilterChip(selected = algorithm == it, onClick = { algorithm = it }, label = { Text(it.uriName) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            listOf(6, 7, 8).forEach {
                                FilterChip(selected = digits == it, onClick = { digits = it }, label = { Text("$it digits") })
                            }
                        }
                        if (type == OtpType.TOTP) {
                            OutlinedTextField(
                                period, { period = it.filter(Char::isDigit) }, label = { Text("Period (seconds)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.width(180.dp),
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = { Button(onClick = ::submit) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Copies [code] and flags it sensitive so Android hides it from the clipboard preview. */
private fun copyCode(context: Context, code: String) {
    if (code.isEmpty()) return
    val clip = ClipData.newPlainText("code", code)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
}
