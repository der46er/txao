package com.txao.files

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity

class MainActivity : FragmentActivity() {
    /** Bumped on every resume so the app re-checks the all-files permission after the settings screen. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        val prefs = Prefs(this)
        setContent {
            TxaoTheme {
                FilesApp(prefs, resumeTick, ::requestAllFilesAccess)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    private fun requestAllFilesAccess() {
        val uri = Uri.parse("package:$packageName")
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    companion object {
        fun hasAccess() = Environment.isExternalStorageManager()
    }
}
