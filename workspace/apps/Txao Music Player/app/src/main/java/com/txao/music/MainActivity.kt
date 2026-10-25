package com.txao.music

import android.Manifest
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.provider.MediaStore
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()
    private lateinit var prefs: TxaoPrefs
    private var afterGrant: (() -> Unit)? = null
    private var allFiles by mutableStateOf(false)

    private val grantLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == RESULT_OK) afterGrant?.invoke()
        afterGrant = null
    }
    private val legacyWrite = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) afterGrant?.invoke()
        afterGrant = null
    }

    /** Asks for write access to [uris] (Android 11+ shows one system prompt), then runs [then]. */
    private fun requestWrite(uris: List<Uri>, then: () -> Unit) {
        afterGrant = then
        when {
            MediaEditor.hasAllFilesAccess() -> { afterGrant = null; then() }
            Build.VERSION.SDK_INT >= 30 ->
                grantLauncher.launch(IntentSenderRequest.Builder(MediaStore.createWriteRequest(contentResolver, uris).intentSender).build())
            Build.VERSION.SDK_INT >= 29 -> { afterGrant = null; then() } // a per-file prompt appears if the write is refused
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> { afterGrant = null; then() }
            else -> legacyWrite.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    override fun onResume() {
        super.onResume()
        allFiles = MediaEditor.hasAllFilesAccess()
    }

    private fun openAllFilesSettings() {
        val pkg = Uri.parse("package:$packageName")
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg)) }
            .onFailure { startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        prefs = TxaoPrefs(this)
        vm.grantHandler = { sender: IntentSender, retry: () -> Unit ->
            afterGrant = retry
            grantLauncher.launch(IntentSenderRequest.Builder(sender).build())
        }
        val audioPermission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        val askRead = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            if (result[audioPermission] == true) vm.refresh()
        }
        if (checkSelfPermission(audioPermission) == PackageManager.PERMISSION_GRANTED) vm.refresh()
        else askRead.launch(
            if (Build.VERSION.SDK_INT >= 33) arrayOf(audioPermission, Manifest.permission.POST_NOTIFICATIONS) else arrayOf(audioPermission),
        )
        setContent {
            TxaoTheme(prefs) {
                GlassBackground {
                    val alpha = remember { Animatable(0f) }
                    LaunchedEffect(Unit) { alpha.animateTo(1f, tween(TxaoMotion.AppearMs)) }
                    Box(Modifier.graphicsLayer { this.alpha = alpha.value }) {
                        MusicApp(vm, prefs, onRequestPermission = { askRead.launch(arrayOf(audioPermission)) }, onRequestWrite = ::requestWrite,
                            allFilesAccess = allFiles, onGrantAllFiles = ::openAllFilesSettings)
                    }
                }
            }
        }
    }
}
