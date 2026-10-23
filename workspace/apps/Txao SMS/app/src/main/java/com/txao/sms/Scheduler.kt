package com.txao.sms

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Scheduled ("send later") messages, backed by exact alarms. */
object Scheduler {
    fun schedule(context: Context, address: String, body: String, at: Long) {
        val store = Store(context)
        val item = Scheduled(System.currentTimeMillis(), address, body.trim(), at)
        store.scheduled = store.scheduled + item
        arm(context, item)
    }

    fun cancel(context: Context, item: Scheduled) {
        val store = Store(context)
        store.scheduled = store.scheduled.filterNot { it.id == item.id }
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, item.id))
    }

    fun arm(context: Context, item: Scheduled) {
        context.getSystemService(AlarmManager::class.java)
            .setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.at, pending(context, item.id))
    }

    private fun pending(context: Context, id: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context, id.toInt(), Intent(context, ScheduledSendReceiver::class.java).putExtra("id", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
