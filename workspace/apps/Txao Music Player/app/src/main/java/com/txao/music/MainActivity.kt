package com.txao.music

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect

class MainActivity : ComponentActivity() {
    private val vm: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val audioPermission =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        setContent {
            TxaoTheme {
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { result -> if (result[audioPermission] == true) vm.refresh() }
                LaunchedEffect(Unit) {
                    val perms = buildList {
                        add(audioPermission)
                        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                    }.toTypedArray()
                    if (checkSelfPermission(audioPermission) == android.content.pm.PackageManager.PERMISSION_GRANTED) vm.refresh()
                    else launcher.launch(perms)
                }
                MusicApp(vm, onRetryPermission = {
                    launcher.launch(arrayOf(audioPermission))
                })
            }
        }
    }
}
