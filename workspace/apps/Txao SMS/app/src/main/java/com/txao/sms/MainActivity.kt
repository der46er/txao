package com.txao.sms

import android.Manifest
import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/** Where the user currently is. A chat is identified by its address; the thread id is looked up on demand. */
sealed interface Screen {
    data class Chats(val archived: Boolean = false) : Screen
    data class New(val draft: String = "") : Screen
    data object Search : Screen
    data object Starred : Screen
    data object Blocked : Screen
    data object Settings : Screen
    data class Chat(val address: String, val threadId: Long?, val draft: String = "") : Screen
}

class MainActivity : ComponentActivity() {
    private val vm: SmsViewModel by viewModels()
    private var incoming by mutableStateOf<Screen?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Dark bars with light icons, matching the dark canvas.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        incoming = screenFor(intent)
        setContent {
            TxaoTheme {
              CompositionLocalProvider(LocalRepo provides vm.repo) {
                var isDefault by remember { mutableStateOf(vm.isDefault()) }
                val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
                    isDefault = vm.isDefault()
                    if (r.resultCode == Activity.RESULT_OK || isDefault) vm.refresh()
                }
                val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.loadContacts(); vm.refresh() }
                LaunchedEffect(isDefault) {
                    if (isDefault) {
                        permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.POST_NOTIFICATIONS))
                        vm.refresh()
                    }
                }
                if (!isDefault) {
                    OnboardingScreen {
                        val rm = getSystemService(RoleManager::class.java)
                        roleLauncher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_SMS))
                    }
                } else {
                    Root()
                }
              }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incoming = screenFor(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    @Composable
    private fun Root() {
        var screen by remember { mutableStateOf<Screen>(Screen.Chats()) }
        // An intent from outside (notification, "text this number", share) replaces the current screen.
        val target = incoming
        LaunchedEffect(target) {
            if (target != null) {
                screen = target
                incoming = null
            }
        }
        val s = screen
        BackHandler(enabled = s != Screen.Chats()) {
            screen = Screen.Chats()
        }
        val modifier = Modifier.fillMaxSize().statusBarsPadding()
        val home = { screen = Screen.Chats() }
        when (s) {
            is Screen.Chats -> ChatsScreen(vm, s.archived, modifier, onOpen = { screen = Screen.Chat(it.address, it.threadId) },
                onNew = { screen = Screen.New() }, onSearch = { screen = Screen.Search }, onNavigate = { screen = it })
            is Screen.New -> NewChatScreen(vm, s.draft, modifier, onBack = home,
                onPick = { screen = Screen.Chat(it, vm.repo.existingThreadId(it), s.draft) })
            Screen.Search -> SearchScreen(vm, modifier, onBack = home, onOpen = { m -> screen = Screen.Chat(m.address, m.threadId) },
                onOpenChat = { c -> screen = Screen.Chat(c.address, c.threadId) })
            Screen.Starred -> StarredScreen(vm, modifier, onBack = home, onOpen = { m -> screen = Screen.Chat(m.address, m.threadId) })
            Screen.Blocked -> BlockedScreen(vm, modifier, onBack = home)
            Screen.Settings -> SettingsScreen(vm, modifier, onBack = home)
            is Screen.Chat -> ChatScreen(vm, s, modifier, onBack = home, onForward = { screen = Screen.New(it) })
        }
    }

    private fun screenFor(i: Intent?): Screen? {
        i ?: return null
        val thread = i.getLongExtra(Notifications.EXTRA_THREAD, -1)
        val addr = i.getStringExtra(Notifications.EXTRA_ADDRESS)
        if (thread >= 0 && addr != null) return Screen.Chat(addr, thread)
        return when (i.action) {
            Intent.ACTION_SENDTO, Intent.ACTION_VIEW -> {
                val data: Uri = i.data ?: return null
                val number = Numbers.clean(Uri.decode(data.schemeSpecificPart.substringBefore('?')))
                val body = i.getStringExtra("sms_body") ?: i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
                if (number.isEmpty()) Screen.New() else Screen.Chat(number, null, body)
            }
            Intent.ACTION_SEND -> Screen.Chat("", null, i.getStringExtra(Intent.EXTRA_TEXT).orEmpty())
            else -> null
        }
    }
}
