package com.txao.phone

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat

/** Per-contact settings that live in the system contacts provider (ringtone, send to voicemail) plus sharing helpers. */
object ContactSettingsRepo {
    private fun uri(id: Long) = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, id.toString())

    private fun <T> read(ctx: Context, id: Long, col: String, get: (android.database.Cursor) -> T): T? = try {
        ctx.contentResolver.query(uri(id), arrayOf(col), null, null, null)?.use { if (it.moveToFirst()) get(it) else null }
    } catch (_: Exception) { null }

    fun ringtone(ctx: Context, id: Long): String? = read(ctx, id, ContactsContract.Contacts.CUSTOM_RINGTONE) { it.getString(0) }
    fun setRingtone(ctx: Context, id: Long, tone: Uri?): Boolean = update(ctx, id, ContactsContract.Contacts.CUSTOM_RINGTONE, tone?.toString())

    fun sendToVoicemail(ctx: Context, id: Long): Boolean = read(ctx, id, ContactsContract.Contacts.SEND_TO_VOICEMAIL) { it.getInt(0) == 1 } ?: false
    fun setSendToVoicemail(ctx: Context, id: Long, on: Boolean): Boolean = update(ctx, id, ContactsContract.Contacts.SEND_TO_VOICEMAIL, if (on) 1 else 0)

    fun lookupKey(ctx: Context, id: Long): String? = read(ctx, id, ContactsContract.Contacts.LOOKUP_KEY) { it.getString(0) }

    /** Name of the account that owns the contact (e.g. a Google address), or null for phone-only contacts. */
    fun accountName(ctx: Context, id: Long): String? = try {
        ctx.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0", arrayOf(id.toString()), null,
        )?.use { if (it.moveToFirst()) it.getString(0)?.takeIf { n -> n.isNotBlank() } else null }
    } catch (_: Exception) { null }

    private fun update(ctx: Context, id: Long, col: String, value: Any?): Boolean = try {
        val v = ContentValues()
        when (value) { null -> v.putNull(col); is Int -> v.put(col, value); else -> v.put(col, value.toString()) }
        ctx.contentResolver.update(uri(id), v, null, null) > 0
    } catch (_: Exception) { false }

    /** Sends the contact as a .vcf through the share sheet. */
    fun shareVcard(ctx: Context, id: Long): Boolean {
        val key = lookupKey(ctx, id) ?: return false
        val vcf = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, key)
        val send = Intent(Intent.ACTION_SEND).setType("text/x-vcard").putExtra(Intent.EXTRA_STREAM, vcf).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return try { ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (_: Exception) { false }
    }

    /** Pins a one-tap call shortcut for the contact on the launcher. */
    fun pinShortcut(ctx: Context, id: Long, name: String, number: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false
        val intent = Intent(Intent.ACTION_CALL, Uri.fromParts("tel", PhoneUtil.normalize(number), null))
        val info = ShortcutInfoCompat.Builder(ctx, "call_$id")
            .setShortLabel(name.ifBlank { number }).setIntent(intent)
            .setIcon(IconCompat.createWithResource(ctx, R.mipmap.ic_launcher)).build()
        return ShortcutManagerCompat.requestPinShortcut(ctx, info, null)
    }

    /** Opens the calendar's new-event screen as a call reminder. */
    fun remind(ctx: Context, name: String): Boolean = try {
        ctx.startActivity(
            Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.Events.TITLE, "Call $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) { false }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String = "", tint: androidx.compose.ui.graphics.Color = Muted, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, color = if (tint == Danger) Danger else androidx.compose.ui.graphics.Color.Unspecified)
            if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = Muted)
        }
        trailing()
    }
}

/** "Contact settings" list at the bottom of the contact page, like Google Phone. */
@Composable
fun ContactSettingsCard(
    contactId: Long, name: String, number: String, blocked: Boolean, emergency: Boolean,
    onBlock: () -> Unit, onEmergency: (Boolean) -> Unit, onDelete: () -> Unit,
) {
    val ctx = LocalContext.current
    var voicemail by remember(contactId) { mutableStateOf(ContactSettingsRepo.sendToVoicemail(ctx, contactId)) }
    var tone by remember(contactId) { mutableStateOf(ContactSettingsRepo.ringtone(ctx, contactId)) }
    val account = remember(contactId) { ContactSettingsRepo.accountName(ctx, contactId) }
    val toneName = remember(tone) { tone?.let { runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(it)).getTitle(ctx) }.getOrNull() } ?: "Default" }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (ContactSettingsRepo.setRingtone(ctx, contactId, u)) tone = u?.toString()
        }
    }
    SectionHeader("Contact settings")
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).glass(20.dp)) {
        SettingRow(Icons.Filled.MusicNote, "Ringtone", toneName, onClick = {
            val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, tone?.let(Uri::parse))
            try { pick.launch(i) } catch (_: Exception) {}
        })
        SettingRow(Icons.Filled.Alarm, "Reminders", "Add a call reminder to your calendar", onClick = { ContactSettingsRepo.remind(ctx, name) })
        SettingRow(Icons.Filled.Share, "Share contact", "Send as a .vcf file", onClick = { ContactSettingsRepo.shareVcard(ctx, contactId) })
        SettingRow(Icons.Filled.AddToHomeScreen, "Add to home screen", "One-tap call shortcut", onClick = {
            val ok = ContactSettingsRepo.pinShortcut(ctx, contactId, name, number)
            android.widget.Toast.makeText(ctx, if (ok) "Choose where to place the shortcut" else "Your launcher does not support shortcuts", android.widget.Toast.LENGTH_SHORT).show()
        })
        SettingRow(Icons.Filled.Voicemail, "Send to voicemail", "Calls from this person never ring") { Switch(voicemail, { if (ContactSettingsRepo.setSendToVoicemail(ctx, contactId, it)) voicemail = it }) }
        SettingRow(Icons.Filled.LocalHospital, "Emergency contact", "Shown on the lock screen emergency card after you add them there too") {
            Switch(emergency, onEmergency)
        }
        SettingRow(Icons.Filled.Block, if (blocked) "Unblock numbers" else "Block numbers", tint = Danger, onClick = onBlock)
        SettingRow(Icons.Filled.Delete, "Delete", tint = Danger, onClick = onDelete)
    }
    Text(
        if (account != null) "Saved to $account" else "Saved on this phone only",
        Modifier.padding(horizontal = 24.dp, vertical = 12.dp), fontSize = 12.sp, color = Muted,
    )
}
