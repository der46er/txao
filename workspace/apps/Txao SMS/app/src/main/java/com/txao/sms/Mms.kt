package com.txao.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import java.io.ByteArrayOutputStream
import java.util.UUID

/** Sending, receiving and storing picture/group messages (MMS) in the system provider. */
object Mms {
    private const val CT_RELATED = "application/vnd.wap.multipart.related"
    private const val ADDR_FROM = 137
    private const val ADDR_TO = 151

    /** Most carriers refuse messages above 1 MB; stay a little under. */
    const val MAX_BYTES = 950_000

    /** A picture/video/audio attachment that is ready to send. */
    class Outgoing(val contentType: String, val name: String, val data: ByteArray)

    // ---------- sending ----------

    /** Stores the message as "sending" and hands it to the platform. Returns false when it is too large. */
    fun send(context: Context, address: String, text: String, media: List<Outgoing>): Boolean {
        val parts = ArrayList<MmsPart>()
        val t = text.trim()
        if (t.isNotEmpty()) parts += MmsPart("text/plain", "text.txt", "text", t.toByteArray(Charsets.UTF_8))
        media.forEachIndexed { i, m -> parts += MmsPart(m.contentType, m.name, "m$i", m.data) }
        if (parts.sumOf { it.data.size } > MAX_BYTES) return false
        val repo = SmsRepository(context, Store(context))
        val threadId = repo.threadIdFor(address)
        val id = insert(context, threadId, Telephony.Mms.MESSAGE_BOX_OUTBOX, null, listOf(address), nowSeconds(), true, parts) ?: return false
        transmit(context, id, listOf(address), parts)
        return true
    }

    /** Sends a failed picture message again from what is stored. */
    fun resend(context: Context, mmsId: Long) {
        val to = addresses(context, mmsId, ADDR_TO)
        val parts = storedParts(context, mmsId).filter { !it.isSmil }
        if (to.isEmpty() || parts.isEmpty()) return
        setBox(context, mmsId, Telephony.Mms.MESSAGE_BOX_OUTBOX)
        transmit(context, mmsId, to, parts)
    }

    private fun transmit(context: Context, mmsId: Long, to: List<String>, parts: List<MmsPart>) {
        try {
            val pdu = MmsCodec.buildSendReq(to, null, parts, UUID.randomUUID().toString().take(12), nowSeconds())
            val name = "snd_" + UUID.randomUUID()
            MmsFileProvider.file(context, name).writeBytes(pdu)
            val uri = MmsFileProvider.uri(name)
            MmsFileProvider.grant(context, uri)
            val done = PendingIntent.getBroadcast(
                context, mmsId.toInt(),
                Intent(context, MmsSentReceiver::class.java).setData(Uri.parse("txaomms://sent/$mmsId?file=$name")),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(SmsManager::class.java).sendMultimediaMessage(context, uri, null, null, done)
        } catch (e: Exception) {
            setBox(context, mmsId, Telephony.Mms.MESSAGE_BOX_FAILED)
        }
    }

    fun onSent(context: Context, data: Uri, resultCode: Int) {
        val id = data.lastPathSegment?.toLongOrNull() ?: return
        setBox(context, id, if (resultCode == Activity.RESULT_OK) Telephony.Mms.MESSAGE_BOX_SENT else Telephony.Mms.MESSAGE_BOX_FAILED)
        data.getQueryParameter("file")?.let { MmsFileProvider.file(context, it).delete() }
    }

    // ---------- receiving ----------

    /** A WAP push arrived: it is normally a "come and get it" notification, so start the download. */
    fun onPush(context: Context, data: ByteArray) {
        val store = Store(context)
        val note = MmsCodec.parseNotification(data)
        if (note == null) {
            // Some networks push the whole message at once.
            MmsCodec.parseRetrieve(data)?.let { receive(context, it) }
            return
        }
        if (note.from != null && store.isBlocked(note.from)) return
        try {
            val name = "rcv_" + UUID.randomUUID()
            val uri = MmsFileProvider.uri(name)
            MmsFileProvider.grant(context, uri)
            val done = PendingIntent.getBroadcast(
                context, name.hashCode(), Intent(context, MmsDownloadReceiver::class.java).setData(uri),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            context.getSystemService(SmsManager::class.java).downloadMultimediaMessage(context, note.location, uri, null, done)
        } catch (_: Exception) {
        }
    }

    fun onDownloaded(context: Context, uri: Uri, resultCode: Int) {
        val name = uri.lastPathSegment ?: return
        val file = MmsFileProvider.file(context, name)
        try {
            if (resultCode == Activity.RESULT_OK && file.exists()) {
                MmsCodec.parseRetrieve(file.readBytes())?.let { receive(context, it) }
            }
        } finally {
            file.delete()
        }
    }

    private fun receive(context: Context, m: MmsMessage) {
        val store = Store(context)
        val repo = SmsRepository(context, store)
        val from = m.from?.takeIf { it.isNotBlank() } ?: "Unknown"
        if (store.isBlocked(from)) return
        val threadId = repo.threadIdFor(from)
        insert(context, threadId, Telephony.Mms.MESSAGE_BOX_INBOX, from, m.to, nowSeconds(), false, m.parts, m.subject) ?: return
        // A new message brings an archived chat back to the inbox.
        store.archived = store.archived - threadId
        if (threadId !in store.muted) Notifications.showIncoming(context, repo, store, threadId, from)
    }

    // ---------- the system provider ----------

    private fun nowSeconds() = System.currentTimeMillis() / 1000

    private fun insert(
        context: Context, threadId: Long, box: Int, from: String?, to: List<String>, date: Long, read: Boolean,
        parts: List<MmsPart>, subject: String? = null,
    ): Long? {
        val r = context.contentResolver
        val v = ContentValues().apply {
            put(Telephony.Mms.THREAD_ID, threadId)
            put(Telephony.Mms.DATE, date)
            put(Telephony.Mms.DATE_SENT, date)
            put(Telephony.Mms.MESSAGE_BOX, box)
            put(Telephony.Mms.READ, if (read) 1 else 0)
            put(Telephony.Mms.SEEN, if (read) 1 else 0)
            put(Telephony.Mms.MESSAGE_TYPE, if (box == Telephony.Mms.MESSAGE_BOX_INBOX) 132 else 128)
            put(Telephony.Mms.MMS_VERSION, 18)
            put(Telephony.Mms.CONTENT_TYPE, CT_RELATED)
            put(Telephony.Mms.MESSAGE_CLASS, "personal")
            put(Telephony.Mms.MESSAGE_SIZE, parts.sumOf { it.data.size })
            put(Telephony.Mms.PRIORITY, 129)
            put(Telephony.Mms.READ_REPORT, 129)
            put(Telephony.Mms.DELIVERY_REPORT, 129)
            put(Telephony.Mms.TEXT_ONLY, if (parts.none { it.isMedia }) 1 else 0)
            if (!subject.isNullOrBlank()) { put(Telephony.Mms.SUBJECT, subject); put(Telephony.Mms.SUBJECT_CHARSET, 106) }
        }
        val uri = r.insert(Telephony.Mms.CONTENT_URI, v) ?: return null
        val id = ContentUris.parseId(uri)
        val addrUri = Uri.parse("content://mms/$id/addr")
        fun addr(address: String, type: Int) {
            r.insert(addrUri, ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, type)
                put(Telephony.Mms.Addr.CHARSET, 106)
                put(Telephony.Mms.Addr.MSG_ID, id)
            })
        }
        addr(from ?: "insert-address-token", ADDR_FROM)
        to.forEach { addr(it, ADDR_TO) }
        val partUri = Uri.parse("content://mms/$id/part")
        for ((i, p) in parts.withIndex()) {
            val pv = ContentValues().apply {
                put(Telephony.Mms.Part.MSG_ID, id)
                put(Telephony.Mms.Part.SEQ, 0)
                put(Telephony.Mms.Part.CONTENT_TYPE, p.contentType)
                put(Telephony.Mms.Part.NAME, p.name ?: "part$i")
                put(Telephony.Mms.Part.CONTENT_ID, "<${p.contentId ?: p.name ?: "part$i"}>")
                put(Telephony.Mms.Part.CONTENT_LOCATION, p.name ?: "part$i")
                if (p.isText) { put(Telephony.Mms.Part.TEXT, p.text); put(Telephony.Mms.Part.CHARSET, 106) }
                if (p.isSmil) put(Telephony.Mms.Part.TEXT, p.text)
            }
            val saved = r.insert(partUri, pv) ?: continue
            if (!p.isText && !p.isSmil) r.openOutputStream(saved)?.use { it.write(p.data) }
        }
        return id
    }

    fun setBox(context: Context, mmsId: Long, box: Int) {
        context.contentResolver.update(
            ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, mmsId), ContentValues().apply { put(Telephony.Mms.MESSAGE_BOX, box) }, null, null,
        )
    }

    fun addresses(context: Context, mmsId: Long, type: Int): List<String> {
        val out = ArrayList<String>()
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS), "${Telephony.Mms.Addr.TYPE} = ?", arrayOf(type.toString()), null,
        )?.use { c -> while (c.moveToNext()) c.getString(0)?.takeIf { it.isNotBlank() && it != "insert-address-token" }?.let(out::add) }
        return out
    }

    /** Address to show for a message: who sent it, or whom it went to. */
    fun peer(context: Context, mmsId: Long, incoming: Boolean): String =
        (if (incoming) addresses(context, mmsId, ADDR_FROM) else addresses(context, mmsId, ADDR_TO)).firstOrNull().orEmpty()

    private fun storedParts(context: Context, mmsId: Long): List<MmsPart> {
        val out = ArrayList<MmsPart>()
        context.contentResolver.query(
            Uri.parse("content://mms/$mmsId/part"),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.TEXT), null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(1).orEmpty()
                val data = if (type.startsWith("text/")) c.getString(3).orEmpty().toByteArray(Charsets.UTF_8)
                else context.contentResolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(0)}"))?.use { it.readBytes() } ?: ByteArray(0)
                out += MmsPart(type, c.getString(2), c.getString(2)?.substringBeforeLast('.'), data)
            }
        }
        return out
    }

    // ---------- picking attachments ----------

    /** Reads a picked file; pictures are scaled down and re-encoded so they fit the carrier's size limit. Null if too large. */
    fun prepare(context: Context, uri: Uri, index: Int): Outgoing? = try {
        val type = context.contentResolver.getType(uri) ?: "application/octet-stream"
        if (type.startsWith("image/") && type != "image/gif") {
            compress(context, uri)?.let { Outgoing("image/jpeg", "image_${index + 1}.jpg", it) }
        } else {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val ext = type.substringAfter('/').substringBefore(';').ifEmpty { "bin" }
            bytes?.takeIf { it.size <= MAX_BYTES }?.let { Outgoing(type, "file_${index + 1}.$ext", it) }
        }
    } catch (_: Exception) {
        null
    }

    private fun compress(context: Context, uri: Uri, maxSide: Int = 1280): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide || bounds.outHeight / (sample * 2) >= maxSide) sample *= 2
        var bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = context.contentResolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, maxSide.toFloat() / maxOf(bmp.width, bmp.height))
        if (rotation != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rotation) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        // Aim well below the limit so a few pictures fit into one message.
        for (quality in intArrayOf(85, 70, 55, 40)) {
            val out = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
            if (out.size() <= 300_000 || quality == 40) return out.toByteArray().takeIf { it.size <= MAX_BYTES }
        }
        return null
    }
}

/** Result of sending a picture message. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.data?.let { Mms.onSent(context, it, resultCode) }
    }
}

/** The platform finished downloading an incoming picture message into our scratch file. */
class MmsDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val code = resultCode
        val pending = goAsync()
        Thread {
            try {
                Mms.onDownloaded(context, uri, code)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
