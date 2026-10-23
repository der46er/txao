package com.txao.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class MainActivity : ComponentActivity() {
    private val vm: CalendarViewModel by viewModels()

    /** Set by an incoming intent; the UI opens the editor with it and then clears it. */
    private var pendingDraft by mutableStateOf<String?>(null)

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        handle(intent)
        setContent {
            TxaoTheme {
                GlassBackground {
                    CalendarApp(vm, pendingDraft, onDraftConsumed = { pendingDraft = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent?) {
        when (i?.action) {
            Intent.ACTION_SEND -> pendingDraft = i.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            "com.txao.calendar.NEW_EVENT" -> pendingDraft = ""
            Intent.ACTION_VIEW -> i.data?.let { uri ->
                val text = runCatching { contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
                val n = text?.let { vm.importIcs(it) } ?: 0
                Toast.makeText(this, if (n == 0) "No events found in that file" else "Imported $n event(s)", Toast.LENGTH_LONG).show()
            }
        }
    }
}
