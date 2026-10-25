package com.txao.sms

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object Notifications {
    const val CHANNEL = "messages"
    const val EXTRA_THREAD = "thread_id"
    const val EXTRA_ADDRESS = "address"
    const val KEY_REPLY = "reply"

    fun createChannel(context: Context) {
        val ch = NotificationChannel(CHANNEL, "Messages", NotificationManager.IMPORTANCE_HIGH)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun showIncoming(context: Context, repo: SmsRepository, store: Store, threadId: Long, address: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        createChannel(context)
        val unread = repo.messages(threadId).filter { !it.outgoing }.takeLast(5)
        val title = repo.displayName(address) ?: address
        val text = if (store.notificationPreview) unread.lastOrNull()?.preview.orEmpty() else "New message"

        val open = PendingIntent.getActivity(
            context, threadId.toInt(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_THREAD, threadId).putExtra(EXTRA_ADDRESS, address)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val actionIntent = Intent(context, NotificationActionReceiver::class.java)
            .putExtra(EXTRA_THREAD, threadId).putExtra(EXTRA_ADDRESS, address)
        val markRead = PendingIntent.getBroadcast(
            context, threadId.toInt(), actionIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Inline reply needs a mutable PendingIntent so the system can attach the typed text.
        val reply = PendingIntent.getBroadcast(
            context, threadId.toInt() + 1_000_000, actionIntent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val replyAction = Notification.Action.Builder(null, "Reply", reply)
            .addRemoteInput(RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build())
            .setAllowGeneratedReplies(true)
            .build()
        val readAction = Notification.Action.Builder(null, "Mark as read", markRead).build()

        val n = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_sms)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                Notification.InboxStyle().also { s ->
                    if (store.notificationPreview) unread.forEach { s.addLine(it.preview) } else s.addLine(text)
                    s.setBigContentTitle(title)
                },
            )
            .setWhen(System.currentTimeMillis())
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(replyAction)
            .addAction(readAction)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(threadId.toInt(), n)
    }

    fun cancel(context: Context, threadId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(threadId.toInt())
    }
}
