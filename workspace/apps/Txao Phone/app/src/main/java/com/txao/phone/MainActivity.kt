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
    private var openNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initialNumber = telNumber(intent)
        openNumber = contactNumber(intent)
        setContent { TxaoTheme { App(initialNumber, openNumber) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        initialNumber = telNumber(intent)
        openNumber = contactNumber(intent)
    }

    private fun telNumber(i: Intent?) =
        if (i?.action == ACTION_OPEN_CONTACT) ""
        else i?.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart?.let(PhoneUtil::normalize).orEmpty()

    /** Other Txao apps (e.g. Txao SMS) open a person's contact page with this action and the number as an extra. */
    private fun contactNumber(i: Intent?) =
        if (i?.action == ACTION_OPEN_CONTACT) i.getStringExtra(EXTRA_NUMBER)?.let(PhoneUtil::normalize).orEmpty() else ""

    companion object {
        const val ACTION_OPEN_CONTACT = "com.txao.phone.action.OPEN_CONTACT"
        const val EXTRA_NUMBER = "number"
    }
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
    data object Settings : Screen
}

private val Tabs = listOf("Favorites" to Icons.Filled.Star, "Recents" to Icons.Filled.History, "Contacts" to Icons.Filled.Contacts, "Voicemail" to Icons.Filled.Voicemail)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(initialNumber: String, openNumber: String) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAll(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted = hasAll(ctx) }
    var isDefault by remember { mutableStateOf(DefaultDialer.isHeld(ctx)) }
    // Default-phone-app request with fallbacks: role dialog, then the telecom dialog, then the settings page.
    var attempt by remember { mutableIntStateOf(0) }
    var showHelp by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = DefaultDialer.isHeld(ctx)
        if (!isDefault) showHelp = true
    }
    val tryRequest: (Int) -> Unit = { from ->
        val intents = DefaultDialer.requestIntents(ctx)
        var i = from
        while (i < intents.size) {
            try { attempt = i; roleLauncher.launch(intents[i]); break } catch (_: Exception) { i++ }
        }
        if (i >= intents.size) showHelp = true
    }
    val requestDefault = { tryRequest(0) }
    val prefs = remember { ctx.getSharedPreferences("txao_phone", Context.MODE_PRIVATE) }

    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var recents by remember { mutableStateOf<List<CallEntry>>(emptyList()) }
    fun reload() {
        if (!hasAll(ctx)) return
        contacts = Data.contacts(ctx)
        recents = Data.recents(ctx)
        isDefault = DefaultDialer.isHeld(ctx)
    }
    LaunchedEffect(granted) { reload() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { reload() }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(PERMS + Manifest.permission.POST_NOTIFICATIONS) }
    // First launch: ask once to become the default phone app, so calls use our own call screen.
    LaunchedEffect(granted) {
        if (granted && !isDefault && !prefs.getBoolean("asked_role", false)) {
            prefs.edit().putBoolean("asked_role", true).apply()
            requestDefault()
        }
    }

    var tab by remember { mutableIntStateOf(1) }
    var number by remember(initialNumber) { mutableStateOf(initialNumber) }
    var screen by remember(initialNumber, openNumber) {
        mutableStateOf<Screen?>(
            when {
                openNumber.isNotEmpty() -> Screen.Detail(openNumber, PhoneUtil.pretty(openNumber))
                initialNumber.isNotEmpty() -> Screen.Keypad
                else -> null
            },
        )
    }
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
        Screen.Settings -> SettingsScreen({ screen = null }, { screen = Screen.Blocked }, isDefault, requestDefault)
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
                if (!isDefault && granted) {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().glass(20.dp).padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Calls still use the system call screen. Tap Set, choose Txao Phone as your phone app, and confirm.", Modifier.weight(1f), fontSize = 13.sp)
                        TextButton(requestDefault) { Text("Set") }
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
                            DropdownMenuItem({ Text("Settings") }, { menu = false; screen = Screen.Settings })
                            DropdownMenuItem({ Text("Blocked numbers") }, { menu = false; screen = Screen.Blocked })
                            if (!isDefault) {
                                DropdownMenuItem({ Text("Set as default phone app") }, { menu = false; requestDefault() })
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
    if (showHelp && !isDefault) {
        val methods = DefaultDialer.requestIntents(ctx).size
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text("Set Txao Phone as default") },
            text = {
                Text(
                    "Android did not switch your phone app yet. Open Settings > Apps > Default apps > Phone app and pick Txao Phone. " +
                        "On Xiaomi phones also allow \"Display pop-up windows while running in the background\" and \"Show on lock screen\" in the app's permissions, so incoming calls can appear.",
                )
            },
            confirmButton = { TextButton({ showHelp = false; try { ctx.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)) } catch (_: Exception) {} }) { Text("Open settings") } },
            dismissButton = {
                Row {
                    if (attempt + 1 < methods) TextButton({ showHelp = false; tryRequest(attempt + 1) }) { Text("Try another way") }
                    TextButton({ showHelp = false }) { Text("Close") }
                }
            },
        )
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
