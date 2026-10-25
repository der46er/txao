package com.txao.sms

import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Telephony

/** Incoming SMS while we are the default app. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val store = Store(context)
        val repo = SmsRepository(context, store)
        // Multi-part messages arrive as several PDUs from one sender: join them.
        val bySender = LinkedHashMap<String, StringBuilder>()
        var date = System.currentTimeMillis()
        for (p in parts) {
            bySender.getOrPut(p.originatingAddress ?: "Unknown") { StringBuilder() }.append(p.messageBody.orEmpty())
            date = p.timestampMillis.takeIf { it > 0 } ?: date
        }
        val pending = goAsync()
        Thread {
            try {
                for ((sender, body) in bySender) {
                    if (store.isBlocked(sender)) continue
                    val uri = repo.insert(sender, body.toString(), Telephony.Sms.MESSAGE_TYPE_INBOX, read = false, date = System.currentTimeMillis())
                    val threadId = repo.threadIdFor(sender)
                    // A new message brings an archived chat back to the inbox.
                    store.archived = store.archived - threadId
                    if (uri != null && threadId !in store.muted) Notifications.showIncoming(context, repo, store, threadId, sender)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Incoming picture message notification (WAP push): starts the download. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val data = intent.getByteArrayExtra("data") ?: return
        val pending = goAsync()
        Thread {
            try {
                Mms.onPush(context, data)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** "Reply with message" from the incoming-call screen. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val to = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!to.isNullOrBlank() && !text.isNullOrBlank()) Numbers.parseRecipients(to).forEach { Sender.send(this, it, text) }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

class SentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        intent.data?.let { Sender.onSent(context, it, resultCode) }
    }
}

class DeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data?.buildUpon()?.clearQuery()?.build() ?: return
        SmsRepository(context, Store(context)).setDelivered(uri)
    }
}

/** The Reply / Mark as read buttons on a notification. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val threadId = intent.getLongExtra(Notifications.EXTRA_THREAD, -1)
        val address = intent.getStringExtra(Notifications.EXTRA_ADDRESS) ?: return
        val store = Store(context)
        val repo = SmsRepository(context, store)
        val reply = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(Notifications.KEY_REPLY)?.toString()
        val pending = goAsync()
        Thread {
            try {
                if (!reply.isNullOrBlank()) Sender.send(context, address, reply)
                if (threadId >= 0) repo.markRead(threadId)
                Notifications.cancel(context, threadId)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Fires a scheduled message. */
class ScheduledSendReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        val store = Store(context)
        val item = store.scheduled.firstOrNull { it.id == id } ?: return
        store.scheduled = store.scheduled.filterNot { it.id == id }
        val pending = goAsync()
        Thread {
            try {
                Numbers.parseRecipients(item.address).forEach { Sender.send(context, it, item.body) }
            } finally {
                pending.finish()
            }
        }.start()
    }
}

/** Re-arms scheduled messages after a reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val now = System.currentTimeMillis()
        // Anything that came due while the phone was off goes out now.
        store.scheduled.forEach { Scheduler.arm(context, if (it.at < now) it.copy(at = now + 2_000) else it) }
    }
}
