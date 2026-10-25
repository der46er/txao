package com.txao.phone

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds as K
import android.provider.ContactsContract.Data as D

/** A labelled value such as a phone number or email address. `type` is the ContactsContract type constant. */
data class Labeled(val type: Int, val customLabel: String?, val value: String, val primary: Boolean = false) {
    fun display(ctx: Context, phone: Boolean): String {
        val l = if (phone) K.Phone.getTypeLabel(ctx.resources, type, customLabel) else K.Email.getTypeLabel(ctx.resources, type, customLabel)
        return l.toString()
    }
}

/** Everything the contact page shows and the editor changes. */
data class ContactDetails(
    val contactId: Long = 0,
    val rawId: Long = 0,
    val name: String = "",
    val phones: List<Labeled> = emptyList(),
    val emails: List<Labeled> = emptyList(),
    val address: String = "",
    val company: String = "",
    val birthday: String = "",
    val ims: List<ImEntry> = emptyList(),
    val notes: String = "",
    val starred: Boolean = false,
    val photoUri: String? = null,
    val background: String? = null,
)

/** Result of editing: [photo] is new JPEG bytes, [removePhoto] clears the stored one. */
data class ContactEdit(val details: ContactDetails, val photo: ByteArray? = null, val removePhoto: Boolean = false)

object ContactsRepo {
    val PHONE_TYPES = listOf(K.Phone.TYPE_MOBILE, K.Phone.TYPE_HOME, K.Phone.TYPE_WORK, K.Phone.TYPE_MAIN, K.Phone.TYPE_OTHER)
    val EMAIL_TYPES = listOf(K.Email.TYPE_HOME, K.Email.TYPE_WORK, K.Email.TYPE_MOBILE, K.Email.TYPE_OTHER)

    fun typeName(type: Int, phone: Boolean): String = if (phone) when (type) {
        K.Phone.TYPE_MOBILE -> "Mobile"; K.Phone.TYPE_HOME -> "Home"; K.Phone.TYPE_WORK -> "Work"
        K.Phone.TYPE_MAIN -> "Main"; else -> "Other"
    } else when (type) {
        K.Email.TYPE_HOME -> "Home"; K.Email.TYPE_WORK -> "Work"; K.Email.TYPE_MOBILE -> "Mobile"; else -> "Other"
    }

    /** Contact id for a phone number, or null when it is not in the address book. */
    fun idForNumber(ctx: Context, number: String): Long? {
        if (number.isEmpty()) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.CONTACT_ID), null, null, null)?.use {
                if (it.moveToFirst()) it.getLong(0) else null
            }
        } catch (_: SecurityException) { null }
    }

    fun load(ctx: Context, contactId: Long): ContactDetails? {
        val cr = ctx.contentResolver
        var d = ContactDetails(contactId = contactId)
        var found = false
        try {
            cr.query(
                ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                arrayOf(ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.STARRED, ContactsContract.Contacts.PHOTO_URI), null, null, null,
            )?.use {
                if (it.moveToFirst()) { found = true; d = d.copy(name = it.getString(0).orEmpty(), starred = it.getInt(1) == 1, photoUri = it.getString(2), background = Backgrounds.uriForContact(ctx, contactId)) }
            }
            if (!found) return null
            val phones = ArrayList<Labeled>(); val emails = ArrayList<Labeled>(); val ims = ArrayList<ImEntry>()
            cr.query(
                D.CONTENT_URI,
                arrayOf(D.RAW_CONTACT_ID, D.MIMETYPE, D.DATA1, D.DATA2, D.DATA3, D.IS_PRIMARY, D.DATA4, D.DATA5, D.DATA6),
                "${D.CONTACT_ID}=?", arrayOf(contactId.toString()), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val raw = c.getLong(0)
                    if (d.rawId == 0L) d = d.copy(rawId = raw)
                    val v = c.getString(2).orEmpty()
                    val type = c.getInt(3); val label = c.getString(4); val primary = c.getInt(5) != 0
                    when (c.getString(1)) {
                        K.Phone.CONTENT_ITEM_TYPE -> if (v.isNotBlank()) phones += Labeled(type, label, v, primary)
                        K.Email.CONTENT_ITEM_TYPE -> if (v.isNotBlank()) emails += Labeled(type, label, v, primary)
                        K.StructuredPostal.CONTENT_ITEM_TYPE -> if (d.address.isEmpty()) d = d.copy(address = v)
                        K.Organization.CONTENT_ITEM_TYPE -> if (d.company.isEmpty()) d = d.copy(company = listOf(v, c.getString(6).orEmpty()).filter { it.isNotBlank() }.joinToString(" · "))
                        K.Im.CONTENT_ITEM_TYPE -> if (v.isNotBlank()) {
                            val name = if (c.getInt(7) == K.Im.PROTOCOL_CUSTOM) c.getString(8).orEmpty() else imProtocolName(c.getInt(7))
                            ims += ImEntry(name, v)
                        }
                        K.Note.CONTENT_ITEM_TYPE -> if (d.notes.isEmpty()) d = d.copy(notes = v)
                        K.Event.CONTENT_ITEM_TYPE -> if (type == K.Event.TYPE_BIRTHDAY && d.birthday.isEmpty()) d = d.copy(birthday = v)
                    }
                }
            }
            return d.copy(phones = phones.distinctBy { PhoneUtil.key(it.value) }, emails = emails, ims = ims)
        } catch (_: SecurityException) { return null }
    }

    fun setStarred(ctx: Context, contactId: Long, starred: Boolean) {
        ctx.contentResolver.update(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
            ContentValues().apply { put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0) }, null, null,
        )
    }

    fun delete(ctx: Context, contactId: Long): Boolean = try {
        Backgrounds.remove(ctx, contactId)
        ctx.contentResolver.delete(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId), null, null) > 0
    } catch (_: Exception) { false }

    private val MANAGED = listOf(
        K.StructuredName.CONTENT_ITEM_TYPE, K.Phone.CONTENT_ITEM_TYPE, K.Email.CONTENT_ITEM_TYPE, K.StructuredPostal.CONTENT_ITEM_TYPE,
        K.Organization.CONTENT_ITEM_TYPE, K.Note.CONTENT_ITEM_TYPE, K.Event.CONTENT_ITEM_TYPE, K.Im.CONTENT_ITEM_TYPE,
    )

    private fun imProtocolName(p: Int) = when (p) {
        K.Im.PROTOCOL_SKYPE -> "Skype"; K.Im.PROTOCOL_AIM -> "AIM"; K.Im.PROTOCOL_MSN -> "MSN"; K.Im.PROTOCOL_YAHOO -> "Yahoo"
        K.Im.PROTOCOL_QQ -> "QQ"; K.Im.PROTOCOL_GOOGLE_TALK -> "Google Talk"; K.Im.PROTOCOL_ICQ -> "ICQ"; K.Im.PROTOCOL_JABBER -> "Jabber"
        else -> ""
    }

    /** Creates a new device contact (or rewrites the managed fields of an existing one). Returns the contact id. */
    fun save(ctx: Context, edit: ContactEdit): Long? {
        val d = edit.details
        val ops = ArrayList<ContentProviderOperation>()
        val creating = d.rawId == 0L
        if (creating) {
            ops += ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, AppSettings.activeGoogleAccount(ctx)?.let { "com.google" })
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, AppSettings.activeGoogleAccount(ctx)).build()
        } else {
            ops += ContentProviderOperation.newDelete(D.CONTENT_URI)
                .withSelection("${D.RAW_CONTACT_ID}=? AND ${D.MIMETYPE} IN (${MANAGED.joinToString(",") { "?" }})", arrayOf(d.rawId.toString()) + MANAGED).build()
            if (edit.photo != null || edit.removePhoto) {
                ops += ContentProviderOperation.newDelete(D.CONTENT_URI)
                    .withSelection("${D.RAW_CONTACT_ID}=? AND ${D.MIMETYPE}=?", arrayOf(d.rawId.toString(), K.Photo.CONTENT_ITEM_TYPE)).build()
            }
        }
        fun row(mime: String): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(D.CONTENT_URI).withValue(D.MIMETYPE, mime)
            return if (creating) b.withValueBackReference(D.RAW_CONTACT_ID, 0) else b.withValue(D.RAW_CONTACT_ID, d.rawId)
        }
        if (d.name.isNotBlank()) ops += row(K.StructuredName.CONTENT_ITEM_TYPE).withValue(K.StructuredName.DISPLAY_NAME, d.name.trim()).build()
        for (p in d.phones.filter { it.value.isNotBlank() }) {
            ops += row(K.Phone.CONTENT_ITEM_TYPE).withValue(K.Phone.NUMBER, p.value.trim()).withValue(K.Phone.TYPE, p.type)
                .withValue(K.Phone.LABEL, p.customLabel).build()
        }
        for (e in d.emails.filter { it.value.isNotBlank() }) {
            ops += row(K.Email.CONTENT_ITEM_TYPE).withValue(K.Email.ADDRESS, e.value.trim()).withValue(K.Email.TYPE, e.type)
                .withValue(K.Email.LABEL, e.customLabel).build()
        }
        if (d.address.isNotBlank()) ops += row(K.StructuredPostal.CONTENT_ITEM_TYPE).withValue(K.StructuredPostal.FORMATTED_ADDRESS, d.address.trim()).build()
        if (d.company.isNotBlank()) ops += row(K.Organization.CONTENT_ITEM_TYPE).withValue(K.Organization.COMPANY, d.company.trim()).build()
        for (im in d.ims.filter { it.handle.isNotBlank() }) {
            ops += row(K.Im.CONTENT_ITEM_TYPE).withValue(K.Im.DATA, im.handle.trim()).withValue(K.Im.TYPE, K.Im.TYPE_OTHER)
                .withValue(K.Im.PROTOCOL, K.Im.PROTOCOL_CUSTOM).withValue(K.Im.CUSTOM_PROTOCOL, im.platform).build()
        }
        if (d.notes.isNotBlank()) ops += row(K.Note.CONTENT_ITEM_TYPE).withValue(K.Note.NOTE, d.notes.trim()).build()
        if (d.birthday.isNotBlank()) ops += row(K.Event.CONTENT_ITEM_TYPE).withValue(K.Event.START_DATE, d.birthday.trim()).withValue(K.Event.TYPE, K.Event.TYPE_BIRTHDAY).build()
        if (edit.photo != null) ops += row(K.Photo.CONTENT_ITEM_TYPE).withValue(K.Photo.PHOTO, edit.photo).build()
        return try {
            val res = ctx.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawId = if (creating) ContentUris.parseId(res[0].uri!!) else d.rawId
            if (creating && d.starred) {
                ctx.contentResolver.query(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId), arrayOf(ContactsContract.RawContacts.CONTACT_ID), null, null, null)
                    ?.use { if (it.moveToFirst()) setStarred(ctx, it.getLong(0), true) }
            }
            ctx.contentResolver.query(ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId), arrayOf(ContactsContract.RawContacts.CONTACT_ID), null, null, null)
                ?.use { if (it.moveToFirst()) it.getLong(0) else null }
        } catch (_: Exception) { null }
    }
}
