package com.txao.authenticator

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shown instead of the codes while the app lock is on; asks for the phone's own screen lock. */
@Composable
fun LockScreen(onUnlocked: () -> Unit, onPrompt: () -> Unit) {
    val context = LocalContext.current
    val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) onUnlocked()
    }
    fun prompt() {
        // Null when the phone has no screen lock any more: nothing to ask, so let the user in.
        val intent = keyguard.createConfirmDeviceCredentialIntent("Txao Authenticator", "Unlock to see your codes")
        if (intent == null) onUnlocked() else {
            onPrompt()
            launcher.launch(intent)
        }
    }
    LaunchedEffect(Unit) { prompt() }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Authenticator", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 24.dp))
        Button(onClick = ::prompt) { Text("Unlock") }
    }
}
