package com.txao.phone

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor

/**
 * Read-only window for other Txao apps. `content://com.txao.phone.share/background?number=<phone number>`
 * opens that contact's background image. No queries, no writes, nothing else is exposed.
 */
class ShareProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r" || uri.lastPathSegment != "background") return null
        val ctx = context ?: return null
        val number = uri.getQueryParameter("number").orEmpty()
        val file = Backgrounds.fileForNumber(ctx, number) ?: return null
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String? = if (uri.lastPathSegment == "background") "image/jpeg" else null
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
