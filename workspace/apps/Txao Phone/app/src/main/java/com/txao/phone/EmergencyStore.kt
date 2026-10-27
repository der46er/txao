package com.txao.phone

import android.content.Context
import android.content.Intent
import android.provider.Settings

data class EmergencyContact(val key: String, val name: String, val number: String)

/**
 * Emergency contacts chosen inside Txao Phone, kept by phone-number key so they survive contact edits.
 *
 * Android has no public API for writing the system "Emergency information" card (the data lives in the
 * platform emergency app, outside any app's reach), so [openSystemEditor] hands the person to that screen
 * with the list ready to copy. The list itself syncs with the user's own server (see ContactSync).
 */
object EmergencyStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("txao_phone_emergency", Context.MODE_PRIVATE)

    fun list(ctx: Context): List<EmergencyContact> =
        prefs(ctx).all.filterKeys { it.startsWith("e_") }.mapNotNull { (k, v) ->
            val parts = (v as? String)?.split('\n', limit = 2) ?: return@mapNotNull null
            EmergencyContact(k.removePrefix("e_"), parts[0], parts.getOrElse(1) { "" })
        }.sortedBy { it.name.lowercase() }

    fun isEmergency(ctx: Context, number: String): Boolean = prefs(ctx).contains("e_" + PhoneUtil.key(number))

    fun set(ctx: Context, name: String, number: String, on: Boolean) {
        val key = PhoneUtil.key(number)
        if (key.isEmpty()) return
        val e = prefs(ctx).edit()
        if (on) e.putString("e_$key", name + "\n" + number) else e.remove("e_$key")
        e.putLong("changed", System.currentTimeMillis()).apply()
    }

    /** Opens the system's emergency information editor, or the closest settings page. */
    fun openSystemEditor(ctx: Context): Boolean {
        val tries = listOf(
            Intent("android.settings.EDIT_EMERGENCY_INFO"),
            Intent(Settings.ACTION_SETTINGS),
        )
        for (i in tries) {
            try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return true } catch (_: Exception) {}
        }
        return false
    }
}
