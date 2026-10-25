package com.txao.phone

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

class MainActivity : ComponentActivity() {
    private var initialNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialNumber = telNumber(intent)
        setContent { TxaoTheme { App(initialNumber) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initialNumber = telNumber(intent)
    }

    private fun telNumber(i: Intent?) =
        i?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(PhoneUtil::normalize).orEmpty()
}

private val PERMS = arrayOf(
    Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALL_LOG,
    Manifest.permission.WRITE_CALL_LOG, Manifest.permission.WRITE_CONTACTS, Manifest.permission.CALL_PHONE,
)

private fun hasAll(ctx: Context) = PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

private sealed interface Screen {
    data object Keypad : Screen
    data class Detail(val number: String, val title: String, val contactId: Long? = null) : Screen
    data class Edit(val details: ContactDetails) : Screen
    data object Blocked : Screen
}

private val Tabs = listOf("Favorites" to Icons.Filled.Star, "Recents" to Icons.Filled.History, "Contacts" to Icons.Filled.Contacts, "Voicemail" to Icons.Filled.Voicemail)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(initialNumber: String) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAll(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAll(ctx) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val rm = ctx.getSystemService(RoleManager::class.java)
    var isDefault by remember { mutableStateOf(rm.isRoleHeld(RoleManager.ROLE_DIALER)) }
    val prefs = remember { ctx.getSharedPreferences("txao_phone", Context.MODE_PRIVATE) }

    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var recents by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    fun reload() {
        if (!hasAll(ctx)) return
        contacts = Data.contacts(ctx)
        recents = Data.recents(ctx)
        isDefault = rm.isRoleHeld(RoleManager.ROLE_DIALER)
    }
    LaunchedEffect(granted) { reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS) }
    // First launch: ask once to become the default phone app, so calls use our own call screen.
    LaunchedEffect(granted) {
        if (granted && !isDefault && !prefs.getBoolean("asked_role", false) && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            prefs.edit().putBoolean("asked_role", true).apply()
            roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
        }
    }

    var tab by remember { mutableIntStateOf(1) }
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    var screen by remember(initialNumber) { mutableStateOf<Screen?>(if (initialNumber.isNotEmpty()) Screen.Keypad else null) }
    val search = rememberTextFieldState()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val query = search.text.toString().trim()

    BackHandler(screen != null || query.isNotEmpty()) {
        if (screen != null) screen = null else search.edit { replace(0, length, "") }
    }

    // Placing a call never falls back to the system dialer: ask for the permission instead, then dial.
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val callPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val n = pendingCall; pendingCall = null
        if (ok && n != null) placeCall(ctx, n)
    }
    val call: (String) -> Unit = { n -> if (!placeCall(ctx, n)) { pendingCall = n; callPermLauncher.launch(Manifest.permission.CALL_PHONE) } }
    val open: (String, String) -> Unit = { n, t -> screen = Screen.Detail(n, t, contacts.firstOrNull { PhoneUtil.sameNumber(it.number, n) }?.id) }
    val photos = remember(contacts) { contacts.filter { it.photoUri != null }.associate { PhoneUtil.key(it.number) to it.photoUri!! } }
    val resolver: (String) -> String? = remember(photos) { { n -> photos[PhoneUtil.key(n)] } }
    val newContact: (String) -> Unit = { n -> screen = Screen.Edit(ContactDetails(phones = if (n.isEmpty()) emptyList() else listOf(Labeled(ContactsRepo.PHONE_TYPES[0], null, n)))) }

    CompositionLocalProvider(LocalPhotoResolver provides resolver) {
    when (val s = screen) {
        Screen.Keypad -> KeypadScreen(number, contacts, { number = it }, { call(number) }, { screen = null }, newContact)
        is Screen.Detail -> ContactPage(s.contactId, s.number, s.title, { screen = null }, call, { d, n -> screen = Screen.Edit(d ?: ContactDetails(phones = listOf(Labeled(ContactsRepo.PHONE_TYPES[0], null, n)))) }, { reload() })
        is Screen.Edit -> EditorScreen(
            s.details, { screen = null },
            { id -> reload(); screen = if (id != null) Screen.Detail("", s.details.name, id) else null },
            { reload(); screen = null },
        )
        Screen.Blocked -> BlockedScreen { screen = null }
        null -> Scaffold(
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            floatingActionButton = {
                if (granted) FloatingActionButton({ screen = Screen.Keypad }, containerColor = Lavender, contentColor = OnLavender) { Icon(Icons.Filled.Dialpad, "Keypad") }
            },
            bottomBar = {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0x14FFFFFF)) {
                    Tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, label) }, label = { Text(label, fontSize = 11.sp) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = androidx.compose.ui.graphics.Color(0x33CDB8FF)),
                        )
                    }
                }
            },
        ) { pad ->
            Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
                if (!isDefault && granted && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(20.dp).padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Set Txao Phone as your default phone app to get its call screen.", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton({ roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) }) { Text("Set") }
                    }
                }
                // Search pill with overflow menu, like the stock dialer's top bar.
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(52.dp).glass(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, Modifier.padding(start = 16.dp), tint = Muted)
                    Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        if (query.isEmpty()) Text("Search contacts & places", color = Muted)
                        BasicTextField(
                            search, Modifier.fillMaxWidth(), lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
                            textStyle = androidx.compose.ui.text.TextStyle(color = androidx.compose.ui.graphics.Color.White, fontSize = 16.sp),
                            cursorBrush = SolidColor(Lavender),
                        )
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Clear call history") }, { menu = false; confirmClear = true })
                            DropdownMenuItem({ Text("Blocked numbers") }, { menu = false; screen = Screen.Blocked })
                            if (!isDefault && rm.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                                DropdownMenuItem({ Text("Set as default phone app") }, { menu = false; roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)) })
                            }
                        }
                    }
                }
                when {
                    !granted -> Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Contacts, call log and phone access are needed to show your people and recent calls. Nothing leaves your device.")
                        Spacer(Modifier.height(16.dp))
                        Button({ permLauncher.launch(PERMS) }) { Text("Allow access") }
                    }
                    query.isNotEmpty() -> SearchResults(query, contacts, recents, call, open)
                    tab == 0 -> FavoritesScreen(contacts, recents, call, open)
                    tab == 1 -> RecentsScreen(recents, call, open)
                    tab == 2 -> ContactsScreen(contacts, open) { newContact("") }
                    else -> VoicemailScreen()
                }
            }
        }
    }

    }
    if (granted) WhatsNewDialog()
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear call history?") }, text = { Text("All recent calls will be deleted from this phone.") },
            confirmButton = { TextButton({ Data.clearRecents(ctx); reload(); confirmClear = false }) { Text("Clear") } },
            dismissButton = { TextButton({ confirmClear = false }) { Text("Cancel") } },
        )
    }
}
