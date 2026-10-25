package com.txao.sms

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * Scratch files for the platform's MMS service: it reads the message we want to send from here and
 * writes the downloaded message back here. Names are random and the files are deleted once handled.
 */
class MmsFileProvider : ContentProvider() {
    override fun onCreate() = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val ctx = context ?: return null
        val name = uri.lastPathSegment ?: return null
        return ParcelFileDescriptor.open(file(ctx, name), ParcelFileDescriptor.parseMode(mode))
    }

    override fun getType(uri: Uri): String? = "application/octet-stream"
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0

    companion object {
        const val AUTHORITY = "com.txao.sms.mms"

        fun uri(name: String): Uri = Uri.Builder().scheme("content").authority(AUTHORITY).appendPath(name).build()

        /** The file behind [name]; the name is reduced to a plain file name so it can never point outside the folder. */
        fun file(context: Context, name: String): File {
            val dir = File(context.cacheDir, "mms").also { it.mkdirs() }
            return File(dir, File(name).name)
        }

        /** The phone process reads and writes through the provider; the grant covers devices that insist on one. */
        fun grant(context: Context, uri: Uri) {
            for (pkg in listOf("com.android.phone", "com.android.mms.service")) {
                try {
                    context.grantUriPermission(pkg, uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (_: Exception) {
                }
            }
        }
    }
}
