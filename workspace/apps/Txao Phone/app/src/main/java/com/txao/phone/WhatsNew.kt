package com.txao.phone

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Release-notes dialog shown once, the first time the app opens after an update.
 * Self-contained so other Txao apps can copy it: only [notes] is app specific.
 *
 * It tracks the last versionCode it has shown in its own preferences file. A fresh install is
 * stored silently (nothing to announce); an update shows the notes for the new version.
 */
object WhatsNew {
    private const val PREFS = "txao_whats_new"
    private const val KEY = "last_seen_version_code"

    /** Release notes by versionCode. Add an entry per release; versions without one show nothing. */
    val notes: Map<Int, List<String>> = mapOf(
        10 to listOf(
            "Txao SMS can now open a contact's page here and show their background image in chats.",
        ),
        9 to listOf(
            "Custom background image per contact, shown on their page and on the call screen.",
            "New Settings screen: sync new contacts with your Google account, edit quick replies.",
        ),
        8 to listOf(
            "More reliable \"default phone app\" setup: if Android ignores the first request, Txao Phone tries other ways and tells you what to tap.",
        ),
        7 to listOf(
            "Add usernames for WhatsApp, Telegram, Signal, Messenger, Instagram, Discord, Snapchat, X and Skype to a contact; tap one to open the chat.",
        ),
        6 to listOf(
            "Dial pad tones: keys now beep like a classic phone (follows your system setting).",
            "This \"What's new\" note appears once after each update.",
        ),
        5 to listOf("Declined calls now show as Declined instead of Missed."),
        3 to listOf(
            "Contact photos everywhere, create and edit contacts inside Txao Phone.",
            "Richer contact page, and Txao Phone now works as a real default dialer.",
        ),
    )

    /** Returns the notes to show now (and marks them seen), or null if there is nothing new. */
    fun pending(ctx: Context): Pair<String, List<String>>? {
        val info = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0) } catch (_: PackageManager.NameNotFoundException) { return null }
        @Suppress("DEPRECATION") val current = info.versionCode
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getInt(KEY, 0)
        prefs.edit().putInt(KEY, current).apply()
        if (last == 0) {
            // No record yet: treat it as an update only if the package was updated after it was installed.
            if (info.lastUpdateTime - info.firstInstallTime < 60_000) return null
        } else if (last >= current) return null
        val since = if (last == 0) 0 else last
        val lines = notes.filterKeys { it in (since + 1)..current }.toSortedMap(compareByDescending<Int> { it }).values.flatten()
        return if (lines.isEmpty()) null else (info.versionName ?: "") to lines
    }
}

@Composable
fun WhatsNewDialog() {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf(WhatsNew.pending(ctx)) }
    val p = pending ?: return
    AlertDialog(
        onDismissRequest = { pending = null },
        title = { Text("What's new in ${p.first}") },
        text = { Column { p.second.forEach { Text("• $it", Modifier.padding(vertical = 3.dp)) } } },
        confirmButton = { TextButton({ pending = null }) { Text("Got it") } },
    )
}
