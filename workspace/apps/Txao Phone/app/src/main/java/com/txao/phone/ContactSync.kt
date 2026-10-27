package com.txao.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.txao.core.DirtyItem
import com.txao.core.SyncStore
import com.txao.core.lastWriterWins
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Syncs Txao Phone's contacts with the user's own Txao server (SYNC-SPEC.md: items `c_<id>`, `e_<key>`; blobs `cp_`/`cb_`).
 *
 * Scope, on purpose: contacts that live only on this phone (no Google/Exchange/other account) and the emergency-contact list.
 * Contacts owned by an account already sync through that account, and writing them to a second place would create duplicates.
 *
 * Changes are found by hashing each contact's content against the hash recorded at the last sync, so no change observer is
 * needed. Items are never written with a clock newer than "now", and the newest write wins.
 */
class ContactSyncStore(private val ctx: Context) : SyncStore {
    private val p = ctx.getSharedPreferences("txao_phone_sync", Context.MODE_PRIVATE)

    override var serverSeq: Long
        get() = p.getLong("seq", 0L)
        set(v) { p.edit().putLong("seq", v).apply() }

    private class Entry(val body: JSONObject, val photoBytes: (() -> ByteArray?)? = null, val bgFile: File? = null)

    // ---- mapping between local contact ids and sync ids ----

    private fun syncIdFor(contactId: Long): String {
        p.getString("c2s_$contactId", null)?.let { return it }
        val id = UUID.randomUUID().toString().replace("-", "").take(20)
        p.edit().putString("c2s_$contactId", id).putLong("s2c_$id", contactId).apply()
        return id
    }

    private fun contactFor(syncId: String): Long? = p.getLong("s2c_$syncId", 0L).takeIf { it != 0L }

    private fun link(syncId: String, contactId: Long) { p.edit().putString("c2s_$contactId", syncId).putLong("s2c_$syncId", contactId).apply() }

    // ---- reading what is on the phone now ----

    private fun canRead() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Contact ids whose raw contacts are all stored on the phone itself. Null when the provider could not be read. */
    private fun localContactIds(): Set<Long>? {
        if (!canRead()) return null
        val local = LinkedHashSet<Long>(); val remote = HashSet<Long>()
        try {
            ctx.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
                "${ContactsContract.RawContacts.DELETED}=0", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getString(1)
                    if (type == null || type in LOCAL_TYPES) local += c.getLong(0) else remote += c.getLong(0)
                }
            } ?: return null
        } catch (_: Exception) {
            return null
        }
        return local - remote
    }

    private fun photoFileId(contactId: Long): Long = try {
        ctx.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()),
            arrayOf(ContactsContract.Contacts.PHOTO_FILE_ID), null, null, null,
        )?.use { if (it.moveToFirst() && !it.isNull(0)) it.getLong(0) else 0L } ?: 0L
    } catch (_: Exception) { 0L }

    private fun readPhoto(contactId: Long): ByteArray? = try {
        ContactsContract.Contacts.openContactPhotoInputStream(
            ctx.contentResolver, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()), true,
        )?.use { it.readBytes() }
    } catch (_: Exception) { null }

    /** Content hash of the contact's photo, re-read only when the provider's photo file id changed. */
    private fun photoHash(contactId: Long): String? {
        val fileId = photoFileId(contactId)
        if (fileId == 0L) return null
        val cached = p.getString("pc_$contactId", null)
        if (cached != null && cached.startsWith("$fileId|")) return cached.substringAfter('|')
        val bytes = readPhoto(contactId) ?: return null
        return ContactJson.hashBytes(bytes).also { p.edit().putString("pc_$contactId", "$fileId|$it").apply() }
    }

    private fun bgFile(contactId: Long): File? {
        val f = File(File(ctx.filesDir, "backgrounds"), "bg_$contactId.jpg")
        return f.takeIf { it.exists() }
    }

    private fun bgHash(f: File): String {
        val cached = p.getString("bc_${f.name}", null)
        val stamp = f.lastModified()
        if (cached != null && cached.startsWith("$stamp|")) return cached.substringAfter('|')
        return ContactJson.hashBytes(f.readBytes()).also { p.edit().putString("bc_${f.name}", "$stamp|$it").apply() }
    }

    /** Everything that should exist on the server right now, by item id; null if the phone's data could not be read safely. */
    private fun current(): Map<String, Entry>? {
        val ids = localContactIds() ?: return null
        val out = LinkedHashMap<String, Entry>()
        for (cid in ids) {
            val d = ContactsRepo.load(ctx, cid) ?: continue
            if (d.name.isBlank() && d.phones.isEmpty()) continue
            val sid = syncIdFor(cid)
            val photoHash = photoHash(cid)
            val bg = bgFile(cid)
            val photoId = photoHash?.let { "cp_${sid}_$it" }
            val bgId = bg?.let { "cb_${sid}_${bgHash(it)}" }
            out["c_$sid"] = Entry(ContactJson.body(d, photoId, bgId), photoBytes = { readPhoto(cid) }, bgFile = bg)
        }
        for (e in EmergencyStore.list(ctx)) out["e_${e.key}"] = Entry(ContactJson.emergencyBody(e.name, e.number))
        return out
    }

    // ---- SyncStore ----

    override fun dirtyItems(): List<DirtyItem> {
        val now = System.currentTimeMillis()
        val cur = current() ?: return emptyList()
        val out = ArrayList<DirtyItem>()
        val edit = p.edit()
        for ((id, entry) in cur) {
            val h = ContactJson.hash(entry.body)
            if (h == p.getString("h_$id", null)) continue
            val at = p.getLong("pu_$id", 0L).takeIf { it != 0L && p.getString("ph_$id", null) == h } ?: now
            edit.putLong("pu_$id", at).putString("ph_$id", h)
            out += DirtyItem(id, at, JSONObject(entry.body.toString()).put("id", id).put("updatedAt", at))
        }
        // Items we synced before that are gone from the phone become tombstones.
        for (key in p.all.keys.filter { it.startsWith("h_") }) {
            val id = key.removePrefix("h_")
            if (id in cur) continue
            val at = p.getLong("pu_$id", 0L).takeIf { it != 0L && p.getBoolean("pd_$id", false) } ?: now
            edit.putLong("pu_$id", at).putBoolean("pd_$id", true)
            out += DirtyItem(id, at, JSONObject().put("t", "del").put("id", id).put("updatedAt", at))
        }
        edit.apply()
        return out
    }

    override fun markClean(id: String, updatedAt: Long) {
        val e = p.edit()
        if (p.getBoolean("pd_$id", false)) {
            e.remove("h_$id").remove("u_$id")
        } else {
            e.putString("h_$id", p.getString("ph_$id", null)).putLong("u_$id", updatedAt)
        }
        e.remove("pu_$id").remove("ph_$id").remove("pd_$id").apply()
    }

    override fun applyRemote(id: String, updatedAt: Long, json: JSONObject): Boolean {
        val known = maxOf(p.getLong("u_$id", 0L), p.getLong("pu_$id", 0L)).takeIf { it != 0L }
        if (!lastWriterWins(known, updatedAt)) return false
        return when {
            json.optString("t") == "del" -> applyDelete(id, updatedAt)
            id.startsWith("e_") -> applyEmergency(id, updatedAt, json)
            id.startsWith("c_") -> applyContact(id, updatedAt, json)
            else -> false
        }
    }

    private fun settle(id: String, updatedAt: Long, body: JSONObject?) {
        val e = p.edit().putLong("u_$id", updatedAt).remove("pu_$id").remove("ph_$id").remove("pd_$id")
        if (body != null) e.putString("h_$id", ContactJson.hash(body)) else e.remove("h_$id")
        e.apply()
    }

    private fun applyDelete(id: String, updatedAt: Long): Boolean {
        var changed = false
        if (id.startsWith("e_")) {
            val local = EmergencyStore.list(ctx).firstOrNull { it.key == id.removePrefix("e_") }
            if (local != null) { EmergencyStore.set(ctx, local.name, local.number, false); changed = true }
        } else if (id.startsWith("c_")) {
            contactFor(id.removePrefix("c_"))?.let { if (ContactsRepo.delete(ctx, it)) changed = true }
        }
        settle(id, updatedAt, null)
        p.edit().remove("h_$id").apply()
        return changed
    }

    private fun applyEmergency(id: String, updatedAt: Long, json: JSONObject): Boolean {
        val name = json.optString("name"); val number = json.optString("number")
        EmergencyStore.set(ctx, name, number, true)
        settle(id, updatedAt, ContactJson.emergencyBody(name, number))
        return true
    }

    private fun applyContact(id: String, updatedAt: Long, json: JSONObject): Boolean {
        val sid = id.removePrefix("c_")
        val incoming = ContactJson.details(json)
        val existing = contactFor(sid)?.let { ContactsRepo.load(ctx, it) }
        val d = if (existing != null) incoming.copy(contactId = existing.contactId, rawId = existing.rawId) else incoming
        val contactId = ContactsRepo.save(ctx, ContactEdit(d), localOnly = true) ?: return false
        link(sid, contactId)
        if (incoming.starred != (existing?.starred ?: false)) ContactsRepo.setStarred(ctx, contactId, incoming.starred)
        // Pictures arrive as blobs; remember which ones we still need.
        val e = p.edit()
        json.optString("photo").takeIf { it.isNotEmpty() && it != photoHash(contactId)?.let { h -> "cp_${sid}_$h" } }?.let { e.putString("need_$it", sid) }
        json.optString("bg").takeIf { it.isNotEmpty() && it != bgFile(contactId)?.let { f -> "cb_${sid}_${bgHash(f)}" } }?.let { e.putString("need_$it", sid) }
        e.apply()
        // Record what the phone now holds, so reading it back does not count as a local edit.
        val after = ContactsRepo.load(ctx, contactId)
        val body = after?.let { ContactJson.body(it, photoHash(contactId)?.let { h -> "cp_${sid}_$h" }, bgFile(contactId)?.let { f -> "cb_${sid}_${bgHash(f)}" }) }
        settle(id, updatedAt, body)
        return true
    }

    // ---- pictures ----

    override fun blobsToUpload(): List<String> {
        val cur = current() ?: return emptyList()
        val out = ArrayList<String>()
        for ((_, e) in cur) {
            listOf(e.body.optString("photo"), e.body.optString("bg")).filter { it.isNotEmpty() && !p.getBoolean("bu_$it", false) }.forEach { out += it }
        }
        return out
    }

    override fun readBlob(id: String): ByteArray? {
        val sid = id.substringAfter('_').substringBeforeLast('_')
        val cid = contactFor(sid) ?: return null
        return if (id.startsWith("cp_")) readPhoto(cid) else bgFile(cid)?.readBytes()
    }

    override fun markBlobUploaded(id: String) { p.edit().putBoolean("bu_$id", true).apply() }

    override fun blobsMissing(): List<String> = p.all.keys.filter { it.startsWith("need_") }.map { it.removePrefix("need_") }

    override fun writeBlob(id: String, bytes: ByteArray) {
        val sid = id.substringAfter('_').substringBeforeLast('_')
        val cid = contactFor(sid) ?: run { p.edit().remove("need_$id").apply(); return }
        if (id.startsWith("cp_")) {
            ContactsRepo.load(ctx, cid)?.let { ContactsRepo.save(ctx, ContactEdit(it, photo = bytes), localOnly = true) }
            p.edit().remove("pc_$cid").apply()
        } else {
            ContactsRepo.load(ctx, cid)?.let { d -> Backgrounds.save(ctx, cid, d.phones.map { it.value }, bytes) }
        }
        p.edit().remove("need_$id").putBoolean("bu_$id", true).apply()
        // The picture is now part of the phone's copy: refresh the recorded hash so it is not pushed back as a change.
        val item = "c_$sid"
        ContactsRepo.load(ctx, cid)?.let { d ->
            val body = ContactJson.body(d, photoHash(cid)?.let { h -> "cp_${sid}_$h" }, bgFile(cid)?.let { f -> "cb_${sid}_${bgHash(f)}" })
            p.edit().putString("h_$item", ContactJson.hash(body)).apply()
        }
    }

    private companion object {
        /** Account types that mean "stored on the phone only". */
        val LOCAL_TYPES = setOf("com.android.localphone", "vnd.sec.contact.phone", "com.xiaomi.local", "com.oppo.contacts.device", "com.oplus.contacts.device")
    }
}
