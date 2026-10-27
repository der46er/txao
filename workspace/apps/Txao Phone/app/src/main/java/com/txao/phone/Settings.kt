package com.txao.phone

import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.txao.core.SyncController
import com.txao.core.SyncSection
import com.txao.core.TxaoPrefs
import java.io.File

/** Small preference store for the Settings screen. */
object AppSettings {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("txao_phone", Context.MODE_PRIVATE)

    fun syncGoogle(ctx: Context) = prefs(ctx).getBoolean("sync_google", false)
    fun setSyncGoogle(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("sync_google", on).apply()
    fun syncAccount(ctx: Context): String? = prefs(ctx).getString("sync_account", null)
    fun setSyncAccount(ctx: Context, name: String?) = prefs(ctx).edit().putString("sync_account", name).apply()

    /** Google account new contacts go to, or null to save on the phone only. */
    fun activeGoogleAccount(ctx: Context): String? = if (syncGoogle(ctx)) syncAccount(ctx) else null

    fun setQuickReplies(ctx: Context, lines: List<String>) =
        prefs(ctx).edit().putString("quick_replies", lines.filter { it.isNotBlank() }.joinToString("\n")).apply()
}

/** Per-contact background images, stored privately in the app and found again by contact id or phone number. */
object Backgrounds {
    private fun dir(ctx: Context) = File(ctx.filesDir, "backgrounds").also { it.mkdirs() }
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("txao_phone_bg", Context.MODE_PRIVATE)
    private fun file(ctx: Context, contactId: Long) = File(dir(ctx), "bg_$contactId.jpg")

    /** `file:` URI (with a version query so a changed image reloads), or null when the contact has none. */
    fun uriForContact(ctx: Context, contactId: Long): String? =
        file(ctx, contactId).takeIf { it.exists() }?.let { Uri.fromFile(it).buildUpon().appendQueryParameter("v", it.lastModified().toString()).build().toString() }

    /** The stored background image for a number, for [ShareProvider]. */
    fun fileForNumber(ctx: Context, number: String): File? {
        val id = prefs(ctx).getLong("n_" + PhoneUtil.key(number), 0L)
        return if (id == 0L || PhoneUtil.key(number).isEmpty()) null else file(ctx, id).takeIf { it.exists() }
    }

    fun uriForNumber(ctx: Context, number: String): String? {
        val id = prefs(ctx).getLong("n_" + PhoneUtil.key(number), 0L)
        return if (id == 0L || PhoneUtil.key(number).isEmpty()) null else uriForContact(ctx, id)
    }

    fun save(ctx: Context, contactId: Long, numbers: List<String>, jpeg: ByteArray) {
        file(ctx, contactId).writeBytes(jpeg)
        index(ctx, contactId, numbers)
    }

    /** Remember which numbers belong to a contact's background (call this after any number change too). */
    fun index(ctx: Context, contactId: Long, numbers: List<String>) {
        if (!file(ctx, contactId).exists()) return
        val e = prefs(ctx).edit()
        numbers.map(PhoneUtil::key).filter { it.isNotEmpty() }.forEach { e.putLong("n_$it", contactId) }
        e.apply()
    }

    fun remove(ctx: Context, contactId: Long) {
        file(ctx, contactId).delete()
        val e = prefs(ctx).edit()
        prefs(ctx).all.filter { it.value == contactId }.keys.forEach { e.remove(it) }
        e.apply()
    }
}

data class GoogleAccount(val name: String)

/** Google accounts on this phone: those that already own contacts, plus any the account manager will show us. */
fun googleAccounts(ctx: Context): List<GoogleAccount> {
    val names = LinkedHashSet<String>()
    try {
        ctx.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.ACCOUNT_TYPE}=?", arrayOf("com.google"), null,
        )?.use { while (it.moveToNext()) it.getString(0)?.takeIf { n -> n.isNotBlank() }?.let(names::add) }
    } catch (_: Exception) {
    }
    try {
        AccountManager.get(ctx).getAccountsByType("com.google").forEach { names += it.name }
    } catch (_: Exception) {
    }
    return names.map(::GoogleAccount)
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit, onBlocked: () -> Unit, isDefault: Boolean, onSetDefault: () -> Unit,
    contacts: List<Contact>, prefs: TxaoPrefs, sync: SyncController,
) {
    val ctx = LocalContext.current
    var syncOn by remember { mutableStateOf(AppSettings.syncGoogle(ctx)) }
    var account by remember { mutableStateOf(AppSettings.syncAccount(ctx)) }
    val accounts = remember { googleAccounts(ctx) }
    var replies by remember { mutableStateOf(quickReplies(ctx).joinToString("\n")) }
    var emergency by remember { mutableStateOf(EmergencyStore.list(ctx)) }
    var picking by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ AppSettings.setQuickReplies(ctx, replies.lines()); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Contacts")
            Column(Modifier.fillMaxWidth().glass(20.dp).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sync new contacts with Google")
                        Text("New contacts are saved to your Google account so they sync to your other devices.", fontSize = 13.sp, color = Muted)
                    }
                    Switch(syncOn, {
                        syncOn = it; AppSettings.setSyncGoogle(ctx, it)
                        if (it && account == null) accounts.firstOrNull()?.let { a -> account = a.name; AppSettings.setSyncAccount(ctx, a.name) }
                    })
                }
                if (syncOn) {
                    Spacer(Modifier.height(8.dp))
                    if (accounts.isEmpty()) {
                        Text("No Google account found on this phone. New contacts are saved on the phone only.", fontSize = 13.sp, color = Danger)
                    } else {
                        Text("Account", fontSize = 13.sp, color = Lavender)
                        accounts.forEach { a ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(account == a.name, { account = a.name; AppSettings.setSyncAccount(ctx, a.name) })
                                Text(a.name)
                            }
                        }
                    }
                }
            }
            SectionHeader("Emergency contacts")
            Column(Modifier.fillMaxWidth().glass(20.dp).padding(16.dp)) {
                Text("People to reach in an emergency. The list syncs with your own server, and the button below opens your phone's Emergency information so you can add them there too.", fontSize = 13.sp, color = Muted)
                emergency.forEach { e ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(e.name.ifBlank { PhoneUtil.pretty(e.number) })
                            Text(PhoneUtil.pretty(e.number), fontSize = 13.sp, color = Muted)
                        }
                        TextButton({ EmergencyStore.set(ctx, e.name, e.number, false); emergency = EmergencyStore.list(ctx); sync.requestSync() }) { Text("Remove") }
                    }
                }
                Row {
                    TextButton({ picking = true }) { Text("Add contact") }
                    TextButton({
                        if (!EmergencyStore.openSystemEditor(ctx)) android.widget.Toast.makeText(ctx, "Could not open Emergency information", android.widget.Toast.LENGTH_SHORT).show()
                    }) { Text("Open Emergency info") }
                }
                Text("Android does not let apps write to the system Emergency information, so the two lists stay separate.", fontSize = 12.sp, color = Muted)
            }
            SyncSection(prefs, sync, "contacts")
            SectionHeader("Calls")
            Column(Modifier.fillMaxWidth().glass(20.dp).padding(16.dp)) {
                Text("Quick replies for declining a call (one per line)", fontSize = 13.sp, color = Muted)
                OutlinedTextField(replies, { replies = it }, Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 3)
                TextButton({ replies = DefaultQuickReplies.joinToString("\n") }) { Text("Reset to defaults") }
            }
            Column(Modifier.fillMaxWidth().glass(20.dp).padding(16.dp)) {
                Text("Default phone app")
                Text(if (isDefault) "Txao Phone is your default phone app." else "Not set yet, so calls use the system call screen.", fontSize = 13.sp, color = Muted)
                if (!isDefault) TextButton(onSetDefault) { Text("Set as default") }
            }
            TextButton(onBlocked) { Text("Blocked numbers") }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (picking) {
        var q by remember { mutableStateOf("") }
        val list = remember(q, contacts) { contacts.filter { q.isBlank() || it.name.contains(q, true) }.take(60) }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("Add emergency contact") },
            text = {
                Column {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search") })
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(list.size) { i ->
                            val c = list[i]
                            PersonRow(c.name, PhoneUtil.pretty(c.number), {
                                EmergencyStore.set(ctx, c.name, c.number, true); emergency = EmergencyStore.list(ctx); picking = false; sync.requestSync()
                            }, number = c.number, photoUri = c.photoUri)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ picking = false }) { Text("Close") } },
        )
    }
}
