package com.txao.sms

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract

/** The bridge to Txao Phone: contact page, call, and the contact background image it shares. */
object PhoneLink {
    const val PACKAGE = "com.txao.phone"
    private const val ACTION_OPEN_CONTACT = "com.txao.phone.action.OPEN_CONTACT"
    private const val AUTHORITY = "com.txao.phone.share"

    /** Read-only image of the contact's background in Txao Phone; opening it fails when there is none. */
    fun backgroundUri(number: String): Uri =
        Uri.Builder().scheme("content").authority(AUTHORITY).appendPath("background").appendQueryParameter("number", Numbers.clean(number)).build()

    /** Opens the contact page in Txao Phone; without it, the system contacts app (or "add contact" for unknown numbers). */
    fun openContact(context: Context, number: String, contactId: Long?) {
        val clean = Numbers.clean(number)
        val phone = Intent(ACTION_OPEN_CONTACT).setPackage(PACKAGE).putExtra("number", clean).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(phone)
            return
        } catch (_: ActivityNotFoundException) {
        }
        val fallback = if (contactId != null && contactId > 0) {
            Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_URI, contactId.toString()))
        } else {
            Intent(Intent.ACTION_INSERT_OR_EDIT).setType(ContactsContract.Contacts.CONTENT_ITEM_TYPE).putExtra(ContactsContract.Intents.Insert.PHONE, clean)
        }
        try {
            context.startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Hands the number to the dialer (Txao Phone when it is the default phone app); no call permission needed. */
    fun call(context: Context, number: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(Numbers.clean(number)))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
        }
    }

    /** Opens a username's chat or profile; false when the platform has no link or no app can open it. */
    fun openIm(context: Context, im: ImEntry): Boolean {
        val link = im.known.link(im.handle) ?: return false
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: Exception) {
            false
        }
    }
}
