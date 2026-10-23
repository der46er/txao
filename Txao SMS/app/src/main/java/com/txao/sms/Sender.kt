package com.txao.sms

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager

/** Stores an outgoing message in the provider and hands it to the radio. */
object Sender {
    fun send(context: Context, address: String, body: String) {
        val text = body.trim()
        if (text.isEmpty() || address.isBlank()) return
        val repo = SmsRepository(context, Store(context))
        val uri = repo.insert(address, text, Telephony.Sms.MESSAGE_TYPE_OUTBOX, read = true) ?: return
        transmit(context, repo, uri, address, text)
    }

    /** Sends a stored message again (after a failure). */
    fun resend(context: Context, msg: Msg) {
        val repo = SmsRepository(context, Store(context))
        val uri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, msg.id.toString())
        repo.setType(uri, Telephony.Sms.MESSAGE_TYPE_OUTBOX)
        transmit(context, repo, uri, msg.address, msg.body)
    }

    private fun transmit(context: Context, repo: SmsRepository, uri: Uri, address: String, text: String) {
        try {
            val sms = context.getSystemService(SmsManager::class.java)
            val parts = sms.divideMessage(text)
            val reports = Store(context).deliveryReports
            val sent = ArrayList<PendingIntent>()
            val delivered = ArrayList<PendingIntent?>()
            for (i in parts.indices) {
                sent += receiverIntent(context, SentReceiver::class.java, uri, i)
                delivered += if (reports) receiverIntent(context, DeliveredReceiver::class.java, uri, i) else null
            }
            if (parts.size == 1) sms.sendTextMessage(address, null, text, sent[0], delivered[0])
            else sms.sendMultipartTextMessage(address, null, parts, sent, if (reports) ArrayList(delivered) else null)
        } catch (e: Exception) {
            repo.setType(uri, Telephony.Sms.MESSAGE_TYPE_FAILED)
        }
    }

    private fun receiverIntent(context: Context, cls: Class<*>, uri: Uri, part: Int): PendingIntent {
        // The data uri makes every message (and part) its own PendingIntent.
        val intent = Intent(context, cls).setData(uri.buildUpon().appendQueryParameter("part", part.toString()).build())
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** Result of the radio sending one part. A failure of any part marks the message failed. */
    fun onSent(context: Context, uri: Uri, resultCode: Int) {
        val clean = uri.buildUpon().clearQuery().build()
        val repo = SmsRepository(context, Store(context))
        if (resultCode != Activity.RESULT_OK) {
            repo.setType(clean, Telephony.Sms.MESSAGE_TYPE_FAILED)
        } else if (repo.type(clean) == Telephony.Sms.MESSAGE_TYPE_OUTBOX) {
            repo.setType(clean, Telephony.Sms.MESSAGE_TYPE_SENT)
        }
    }
}
