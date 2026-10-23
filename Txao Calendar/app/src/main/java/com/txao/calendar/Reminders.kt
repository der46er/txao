package com.txao.calendar

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Reminders {
    const val CHANNEL = "reminders"
    const val ACTION_ALARM = "com.txao.calendar.REMINDER"

    fun rescheduleAll(context: Context, events: List<Event>) {
        events.forEach { schedule(context, it) }
    }

    fun schedule(context: Context, event: Event) {
        cancel(context, event.id)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val next = ReminderPlanner.next(event, System.currentTimeMillis()) ?: return
        val pi = pending(context, event.id, create = true, occurrence = next.occurrenceStart) ?: return
        runCatching { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi) }
            .onFailure { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.at, pi) }
    }

    fun cancel(context: Context, eventId: String) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        pending(context, eventId, create = false)?.let { am.cancel(it); it.cancel() }
    }

    private fun pending(context: Context, eventId: String, create: Boolean, occurrence: Long = 0): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).setAction(ACTION_ALARM)
            .setData(android.net.Uri.parse("txao-calendar://event/$eventId"))
            .putExtra("id", eventId).putExtra("occ", occurrence)
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        return if (create) PendingIntent.getBroadcast(context, eventId.hashCode(), intent, flags)
        else PendingIntent.getBroadcast(context, eventId.hashCode(), intent, flags or PendingIntent.FLAG_NO_CREATE)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Event reminders", NotificationManager.IMPORTANCE_HIGH))
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val occ = intent.getLongExtra("occ", 0)
        val events = EventRepository(context).load()
        val event = events.firstOrNull { it.id == id } ?: return
        Reminders.ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, id.hashCode(), Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE,
        )
        val whenText = if (occ > 0) Format.whenText(event.copy(start = occ, end = occ + (event.end - event.start))) else Format.whenText(event)
        val n = NotificationCompat.Builder(context, Reminders.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_event)
            .setContentTitle(event.title.ifEmpty { "Event" })
            .setContentText(listOf(whenText, event.location).filter { it.isNotEmpty() }.joinToString(" · "))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true).setContentIntent(open).build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id.hashCode(), n) }
        Reminders.schedule(context, event)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.rescheduleAll(context, EventRepository(context).load())
        }
    }
}
