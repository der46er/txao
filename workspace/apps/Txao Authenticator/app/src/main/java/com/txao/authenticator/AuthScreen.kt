package com.txao.authenticator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private enum class Page { Home, Settings, Scan }

/** What the "enter a password" dialog is for. */
private sealed interface PasswordTask {
    data object Export : PasswordTask
    data class Import(val text: String) : PasswordTask
}

@Composable
fun AuthScreen(vm: AuthViewModel) {
    val context = LocalContext.current
    val settings = vm.settings
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(250)
        }
    }
    var page by remember { mutableStateOf(Page.Home) }
    var showAddChooser by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Account?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Account?>(null) }
    var passwordTask by remember { mutableStateOf<PasswordTask?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }
    var plainWarning by remember { mutableStateOf(false) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    fun finishImport(result: ImportResult) {
        val added = vm.addAll(result.accounts)
        toast(
            "Imported $added account${if (added == 1) "" else "s"}" +
                (if (result.accounts.size > added) ", ${result.accounts.size - added} already there" else "") +
                (if (result.skipped > 0) ", ${result.skipped} skipped" else ""),
        )
    }

    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()
            when {
                text == null -> toast("Could not read that file")
                Backup.isBackup(text) -> passwordTask = PasswordTask.Import(text)
                else -> finishImport(Importer.parse(text))
            }
        }
    }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val content = pendingExport
        pendingExport = null
        if (uri != null && content != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } != null
            }.getOrDefault(false)
            toast(if (ok) "Export saved" else "Could not save the file")
        }
    }

    fun handleScanned(text: String): Boolean {
        if (text.startsWith("otpauth-migration://", ignoreCase = true)) {
            val list = Importer.parseMigration(text) ?: return false
            finishImport(ImportResult(list, 0))
            page = Page.Home
            return true
        }
        if (Account.fromUri(text) == null) return false
        vm.incoming = text
        page = Page.Home
        return true
    }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val text = runCatching { QrDecoder.decode(context.contentResolver, uri) }.getOrNull()
            if (text == null || !handleScanned(text)) toast("No usable QR code found in that image")
        }
    }

    fun importFromFile() {
        vm.skipNextLock = true
        openFile.launch(arrayOf("*/*"))
    }

    BackHandler(enabled = page != Page.Home) { page = Page.Home }

    when (page) {
        Page.Scan -> ScanScreen(onResult = ::handleScanned, onClose = { page = Page.Home }, onPermissionFlow = { vm.skipNextLock = true })
        Page.Settings -> SettingsScreen(
            settings = settings,
            onBack = { page = Page.Home },
            onImport = ::importFromFile,
            onExportEncrypted = { passwordTask = PasswordTask.Export },
            onExportPlain = { plainWarning = true },
        )
        Page.Home -> HomeScreen(
            vm = vm, now = now,
            onSettings = { page = Page.Settings },
            onAdd = { showAddChooser = true },
            onCopy = { copyCode(context, it, settings.clearClipboard) },
            onEdit = { editing = it },
            onDelete = { pendingDelete = it },
        )
    }

    if (showAddChooser) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { showAddChooser = false },
            title = { Text("Add account") },
            text = {
                Column {
                    ChooserRow(Icons.Filled.QrCodeScanner, "Scan a QR code") { showAddChooser = false; page = Page.Scan }
                    ChooserRow(Icons.Filled.Image, "Read a QR code from an image") { showAddChooser = false; vm.skipNextLock = true; pickImage.launch("image/*") }
                    ChooserRow(Icons.Filled.Keyboard, "Enter a setup key") { showAddChooser = false; creating = true }
                    ChooserRow(Icons.Filled.Download, "Import from a file") { showAddChooser = false; importFromFile() }
                }
            },
            confirmButton = { TextButton(onClick = { showAddChooser = false }) { Text("Cancel") } },
        )
    }

    if (creating) {
        AccountEditor(
            initial = null,
            onDismiss = { creating = false },
            onSave = { vm.add(it); creating = false },
        )
    }
    editing?.let { acc ->
        AccountEditor(
            initial = acc,
            onDismiss = { editing = null },
            onSave = { vm.update(it); editing = null },
        )
    }

    pendingDelete?.let { acc ->
        AlertDialog(
            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                toast("That link is not a valid otpauth account")
                vm.incoming = null
            }
        } else {
            AlertDialog(
            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                onDismissRequest = { vm.incoming = null },
                title = { Text("Add this account?") },
                text = { Text("${parsed.title}${if (parsed.subtitle.isNotEmpty()) "\n${parsed.subtitle}" else ""}") },
                confirmButton = { TextButton(onClick = { vm.add(parsed); vm.incoming = null }) { Text("Add") } },
                dismissButton = { TextButton(onClick = { vm.incoming = null }) { Text("Cancel") } },
            )
        }
    }

    if (plainWarning) {
        AlertDialog(
            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            onDismissRequest = { plainWarning = false },
            title = { Text("Export without encryption?") },
            text = { Text("The file will contain every secret key in plain text. Anyone who gets it can generate your codes. Prefer the encrypted backup.") },
            confirmButton = {
                TextButton(onClick = {
                    plainWarning = false
                    pendingExport = vm.plainExport()
                    vm.skipNextLock = true
                    saveFile.launch("txao-authenticator-plain.txt")
                }) { Text("Export anyway") }
            },
            dismissButton = { TextButton(onClick = { plainWarning = false }) { Text("Cancel") } },
        )
    }

    passwordTask?.let { task ->
        PasswordDialog(
            confirm = task is PasswordTask.Export,
            onDismiss = { passwordTask = null },
            onPassword = { pw ->
                passwordTask = null
                when (task) {
                    PasswordTask.Export -> {
                        pendingExport = vm.encryptedBackup(pw)
                        vm.skipNextLock = true
                        saveFile.launch("txao-authenticator-backup.json")
                    }
                    is PasswordTask.Import -> try {
                        finishImport(ImportResult(Backup.decrypt(task.text, pw), 0))
                    } catch (e: Backup.WrongPasswordOrCorrupt) {
                        toast("Wrong password, or the file is damaged")
                    }
                }
            },
        )
    }
}

@Composable
private fun ChooserRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, contentDescription = null)
        Spacer(Modifier.size(12.dp))
        Text(text, modifier = Modifier.weight(1f), fontSize = 15.sp)
    }
}

@Composable
private fun HomeScreen(
    vm: AuthViewModel, now: Long,
    onSettings: () -> Unit, onAdd: () -> Unit,
    onCopy: (String) -> Unit, onEdit: (Account) -> Unit, onDelete: (Account) -> Unit,
) {
    val revealedAt = remember { mutableStateMapOf<String, Long>() }
    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd, shape = RoundedCornerShape(20.dp), containerColor = FabColor, contentColor = Color(0xFF2E1B5C)) {
                Icon(Icons.Filled.Add, contentDescription = "Add account")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Authenticator", modifier = Modifier.weight(1f).padding(start = 8.dp),
                    fontSize = 24.sp, fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
            }
            if (vm.accounts.isNotEmpty()) {
                SearchBar(vm.query, onChange = { vm.query = it })
                Spacer(Modifier.height(10.dp))
            }
            when {
                vm.loadFailed -> Text(
                    "Your saved accounts could not be unlocked, so changes are disabled to protect them. " +
                        "This can happen after a device restore; the keys do not transfer between phones.",
                    modifier = Modifier.fillMaxWidth().glass().padding(16.dp),
                    color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                )
                vm.accounts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No accounts yet.\nTap + to scan a QR code, enter a setup key or import a file.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                    )
                }
                vm.visible.isEmpty() -> Text("Nothing matches “${vm.query}”.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(vm.visible, key = { it.id }) { account ->
                    val hidden = vm.settings.hideCodes && now - (revealedAt[account.id] ?: 0L) > REVEAL_MS
                    AccountCard(
                        account = account, now = now, hidden = hidden, showNext = vm.settings.showNext,
                        group = vm.settings.groupDigits,
                        onTap = {
                            if (hidden) revealedAt[account.id] = now else onCopy(account.code(now))
                        },
                        onNext = { vm.nextCounter(account) },
                        onEdit = { onEdit(account) },
                        onDelete = { onDelete(account) },
                        onMove = { vm.move(account, it) },
                        canMove = vm.query.isBlank(),
                        onCopy = { onCopy(account.code(now)) },
                    )
                }
            }
        }
    }
}

private const val REVEAL_MS = 10_000L

@Composable
private fun SearchBar(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(100.dp)).background(BarSurface).border(GlassBorder, RoundedCornerShape(100.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White)
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (query.isEmpty()) Text("Search accounts", color = Color(0xFF9EA3B8), fontSize = 16.sp)
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = onChange, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Lavender),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AccountCard(
    account: Account, now: Long, hidden: Boolean, showNext: Boolean, group: Boolean, canMove: Boolean,
    onTap: () -> Unit, onNext: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit,
    onMove: (Int) -> Unit, onCopy: () -> Unit,
) {
    val timed = account.type != OtpType.HOTP
    val period = if (account.type == OtpType.STEAM) 30 else account.period
    val remaining = Otp.secondsRemaining(now, period)
    val urgent = timed && remaining <= 5
    var menu by remember { mutableStateOf(false) }
    val code = account.code(now)
    fun shown(c: String) = if (group) Otp.format(c) else c

    Row(
        Modifier.fillMaxWidth().glass()
            .combinedClickable(onClick = onTap, onLongClick = { menu = true })
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(Color(0x26CDB8FF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(account.title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?", color = Lavender, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(account.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            if (account.subtitle.isNotEmpty()) {
                Text(account.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.size(2.dp))
            Text(
                if (hidden) "•".repeat(code.length.coerceAtLeast(5)) else shown(code).ifEmpty { "—" },
                fontSize = 26.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace,
                color = if (urgent) MaterialTheme.colorScheme.error else Lavender,
            )
            if (showNext && timed && !hidden && remaining <= 10) {
                val next = account.code(now + remaining * 1000L)
                Text("Next  ${shown(next)}", fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (timed) {
            Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { remaining / period.toFloat() },
                    modifier = Modifier.fillMaxSize(),
                    color = if (urgent) MaterialTheme.colorScheme.error else Lavender,
                    trackColor = Color(0x1FFFFFFF),
                )
                Text(remaining.toString(), fontSize = 12.sp)
            }
        } else {
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next code") }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Copy code") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onEdit() })
                if (canMove) {
                    DropdownMenuItem(text = { Text("Move up") }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowUp, null) }, onClick = { menu = false; onMove(-1) })
                    DropdownMenuItem(text = { Text("Move down") }, leadingIcon = { Icon(Icons.Filled.KeyboardArrowDown, null) }, onClick = { menu = false; onMove(1) })
                }
                DropdownMenuItem(text = { Text("Remove") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
private fun PasswordDialog(confirm: Boolean, onDismiss: () -> Unit, onPassword: (CharArray) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val problem = when {
        confirm && pw.length < 8 -> "Use at least 8 characters"
        confirm && pw != again -> "The passwords do not match"
        !confirm && pw.isEmpty() -> "Enter the backup password"
        else -> null
    }
    AlertDialog(
            shape = RoundedCornerShape(28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        onDismissRequest = onDismiss,
        title = { Text(if (confirm) "Encrypt backup" else "Backup password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirm) Text("You need this password to restore the backup. It cannot be recovered.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    pw, { pw = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) OutlinedTextField(
                    again, { again = it }, label = { Text("Repeat password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm && problem != null && pw.isNotEmpty()) Text(problem, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = { Button(enabled = problem == null, onClick = { onPassword(pw.toCharArray()) }) { Text(if (confirm) "Export" else "Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Copies [code] flagged as sensitive (hidden from Android's clipboard preview) and optionally wipes it after 30 s. */
private fun copyCode(context: Context, code: String, wipe: Boolean) {
    if (code.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("code", code)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
    if (wipe) {
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching { clipboard.setPrimaryClip(ClipData.newPlainText("", "")) }
        }, 30_000)
    }
}
