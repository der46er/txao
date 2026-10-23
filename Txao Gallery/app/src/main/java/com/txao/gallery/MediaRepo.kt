package com.txao.gallery

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.app.RecoverableSecurityException
import androidx.annotation.RequiresApi

/** Reads photos and videos from MediaStore and performs delete / trash. */
class MediaRepo(private val context: Context) {
    private val cr: ContentResolver get() = context.contentResolver
    private val files: Uri = MediaStore.Files.getContentUri("external")

    private val columns: Array<String> = buildList {
        add(MediaStore.MediaColumns._ID)
        add(MediaStore.MediaColumns.DISPLAY_NAME)
        add(MediaStore.MediaColumns.MIME_TYPE)
        add(MediaStore.Files.FileColumns.MEDIA_TYPE)
        add(MediaStore.MediaColumns.DATE_MODIFIED)
        add(MediaStore.MediaColumns.SIZE)
        add(MediaStore.MediaColumns.WIDTH)
        add(MediaStore.MediaColumns.HEIGHT)
        add(MediaStore.MediaColumns.BUCKET_ID)
        add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
        if (Build.VERSION.SDK_INT >= 29) {
            add(MediaStore.MediaColumns.DATE_TAKEN)
            add(MediaStore.MediaColumns.DURATION)
        }
    }.toTypedArray()

    private val selection =
        "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})"

    fun load(trashed: Boolean = false): List<MediaItem> {
        if (trashed && Build.VERSION.SDK_INT < 30) return emptyList()
        val cursor = if (Build.VERSION.SDK_INT >= 30) {
            val args = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putInt(
                    MediaStore.QUERY_ARG_MATCH_TRASHED,
                    if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE,
                )
            }
            cr.query(files, columns, args, null)
        } else {
            cr.query(files, columns, selection, null, null)
        }
        val out = ArrayList<MediaItem>()
        cursor?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val iMime = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
            val iType = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
            val iMod = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
            val iSize = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val iW = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
            val iBucket = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val iBucketName = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val iTaken = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN) else -1
            val iDur = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.MediaColumns.DURATION) else -1
            while (c.moveToNext()) {
                val video = c.getInt(iType) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val id = c.getLong(iId)
                val base = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val taken = if (iTaken >= 0) c.getLong(iTaken) else 0L
                out.add(
                    MediaItem(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        name = c.getString(iName) ?: "",
                        mime = c.getString(iMime) ?: if (video) "video/*" else "image/*",
                        isVideo = video,
                        dateMs = if (taken > 0) taken else c.getLong(iMod) * 1000,
                        size = c.getLong(iSize),
                        width = c.getInt(iW),
                        height = c.getInt(iH),
                        durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        albumId = c.getLong(iBucket),
                        albumName = c.getString(iBucketName) ?: "Unknown",
                    )
                )
            }
        }
        return out
    }

    /** Builds an item for a content/file Uri opened from another app. */
    fun fromUri(uri: Uri, mimeHint: String?): MediaItem {
        var name = uri.lastPathSegment ?: "media"
        var size = 0L
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use {
                if (it.moveToFirst()) {
                    it.getString(0)?.let { n -> name = n }
                    size = it.getLong(1)
                }
            }
        }
        val mime = mimeHint ?: cr.getType(uri) ?: "image/*"
        return MediaItem(
            id = -1, uri = uri, name = name, mime = mime, isVideo = mime.startsWith("video"),
            dateMs = System.currentTimeMillis(), size = size, width = 0, height = 0,
            durationMs = 0, albumId = -1, albumName = "",
        )
    }

    /** Android 11+: one system confirmation moves the items to (or out of) the trash. */
    @RequiresApi(30)
    fun trashRequest(uris: Collection<Uri>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(cr, uris, trash).intentSender

    /** Android 11+: permanent delete, confirmed by the system. */
    @RequiresApi(30)
    fun deleteRequest(uris: Collection<Uri>): IntentSender =
        MediaStore.createDeleteRequest(cr, uris).intentSender

    /**
     * Android 10 and older: deletes directly. Returns an IntentSender when the system asks
     * the user for access first (Android 10 scoped storage); retry the call after it is granted.
     */
    fun legacyDelete(uris: Collection<Uri>): IntentSender? {
        for (u in uris) {
            try {
                cr.delete(u, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) {
                    return e.userAction.actionIntent.intentSender
                }
                throw e
            }
        }
        return null
    }
}
