package com.txao.sms

import android.content.Context
import org.json.JSONArray

/** Small on-device settings store: blocked numbers, pinned chats, scheduled messages, switches. */
class Store(context: Context) {
    private val p = context.applicationContext.getSharedPreferences("txao_sms", Context.MODE_PRIVATE)

    var deliveryReports: Boolean
        get() = p.getBoolean("delivery_reports", false)
        set(v) = p.edit().putBoolean("delivery_reports", v).apply()

    var notificationPreview: Boolean
        get() = p.getBoolean("notification_preview", true)
        set(v) = p.edit().putBoolean("notification_preview", v).apply()

    var blocked: List<String>
        get() = readStrings("blocked")
        set(v) = writeStrings("blocked", v)

    var pinned: Set<Long>
        get() = readStrings("pinned").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = writeStrings("pinned", v.map { it.toString() })

    var archived: Set<Long>
        get() = readStrings("archived").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = writeStrings("archived", v.map { it.toString() })

    var muted: Set<Long>
        get() = readStrings("muted").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = writeStrings("muted", v.map { it.toString() })

    /** Ids of starred messages. */
    var starred: Set<Long>
        get() = readStrings("starred").mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = writeStrings("starred", v.map { it.toString() })

    var scheduled: List<Scheduled>
        get() = Scheduled.listFromJson(p.getString("scheduled", null)).sortedBy { it.at }
        set(v) = p.edit().putString("scheduled", Scheduled.listToJson(v)).apply()

    fun isBlocked(address: String) = blocked.any { Numbers.same(it, address) }

    fun block(address: String) {
        if (!isBlocked(address)) blocked = blocked + Numbers.clean(address)
    }

    fun unblock(address: String) {
        blocked = blocked.filterNot { Numbers.same(it, address) }
    }

    private fun readStrings(key: String): List<String> {
        val s = p.getString(key, null) ?: return emptyList()
        return runCatching { JSONArray(s).let { a -> (0 until a.length()).map { a.getString(it) } } }.getOrDefault(emptyList())
    }

    private fun writeStrings(key: String, v: List<String>) {
        p.edit().putString(key, JSONArray(v).toString()).apply()
    }
}
