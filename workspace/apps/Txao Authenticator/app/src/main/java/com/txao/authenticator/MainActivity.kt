package com.txao.authenticator

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val vm: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Codes are secrets: keep them out of screenshots, screen recording and the recents thumbnail.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (savedInstanceState == null) handle(intent)
        setContent {
            TxaoTheme {
                TxaoBackground(Modifier.fillMaxSize()) {
                    if (vm.locked && vm.settings.appLock) {
                        LockScreen(onUnlocked = { vm.locked = false }, onPrompt = { vm.skipNextLock = true })
                    } else {
                        AuthScreen(vm)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.skipNextLock = false
    }

    override fun onStop() {
        super.onStop()
        if (vm.settings.appLock && !vm.skipNextLock && !isChangingConfigurations) vm.locked = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val text = when (intent?.action) {
            Intent.ACTION_VIEW -> intent.dataString
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
        if (text != null && text.trim().startsWith("otpauth://", ignoreCase = true)) vm.incoming = text.trim()
    }
}
