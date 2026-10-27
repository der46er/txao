package com.txao.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import org.json.JSONObject

/** Notification buttons: answer / decline / hang up without opening the call screen. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val call = CallManager.primary() ?: return
        when (intent.action) {
            CallNotifier.ACTION_ANSWER -> call.answer(VideoProfile.STATE_AUDIO_ONLY)
            CallNotifier.ACTION_DECLINE -> if (call.state == Call.STATE_RINGING) call.reject(false, null) else call.disconnect()
        }
    }
}

/**
 * The incoming / ongoing call notification. On Xiaomi HyperOS it also carries the "focus notification"
 * extras, which is what puts the call into the Hyper Island at the top of the screen; every other phone
 * just gets the normal call notification.
 */
object CallNotifier {
    const val ACTION_ANSWER = "com.txao.phone.action.ANSWER"
    const val ACTION_DECLINE = "com.txao.phone.action.DECLINE"
    private const val CHANNEL_INCOMING = "incoming_calls"
    private const val CHANNEL_ONGOING = "ongoing_calls"
    const val NOTIF_ID = 1

    /** Xiaomi, Redmi and POCO phones run MIUI/HyperOS and understand the focus-notification extras. */
    fun isXiaomi(): Boolean {
        val m = Build.MANUFACTURER.lowercase()
        return m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") || Build.BRAND.lowercase().let { it == "xiaomi" || it == "redmi" || it == "poco" }
    }

    fun update(ctx: Context, call: Call) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW))
        val ringing = call.state == Call.STATE_RINGING
        val number = CallManager.number(call)
        val who = Data.lookupName(ctx, number) ?: PhoneUtil.pretty(number).ifEmpty { "Unknown" }
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        fun action(a: String, req: Int) = PendingIntent.getBroadcast(
            ctx, req, Intent(a).setClass(ctx, CallActionReceiver::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = when {
            ringing -> "Incoming call"
            call.state == Call.STATE_HOLDING -> "On hold"
            call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING -> "Calling…"
            else -> "Ongoing call"
        }
        val b = Notification.Builder(ctx, if (ringing) CHANNEL_INCOMING else CHANNEL_ONGOING)
            .setSmallIcon(if (ringing) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
            .setContentTitle(who).setContentText(text)
            .setCategory(Notification.CATEGORY_CALL).setOngoing(true).setContentIntent(open)
        val connected = call.details?.connectTimeMillis ?: 0L
        if (!ringing && connected > 0) b.setUsesChronometer(true).setWhen(connected)
        if (ringing) {
            b.setFullScreenIntent(open, true)
            b.addAction(Notification.Action.Builder(null, "Decline", action(ACTION_DECLINE, 1)).build())
            b.addAction(Notification.Action.Builder(null, "Answer", action(ACTION_ANSWER, 2)).build())
        } else {
            b.addAction(Notification.Action.Builder(null, "Hang up", action(ACTION_DECLINE, 3)).build())
        }
        if (isXiaomi()) {
            val extras = focusExtras(ctx, who, text, ringing, connected)
            b.addExtras(extras)
        }
        nm.notify(NOTIF_ID, b.build())
    }

    fun cancel(ctx: Context) = ctx.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)

    /**
     * Xiaomi focus-notification payload ("miui.focus.param" JSON plus a bundle of pictures). Island layout:
     * a small phone glyph on the left and the name / status on the right when expanded.
     * This follows Xiaomi's published format but has not been verified on a device from here.
     */
    private fun focusExtras(ctx: Context, who: String, text: String, ringing: Boolean, connected: Long): Bundle {
        val picKey = "miui.focus.pic_call"
        val icon = Icon.createWithResource(ctx, if (ringing) android.R.drawable.sym_call_incoming else android.R.drawable.sym_action_call)
        val timer = if (!ringing && connected > 0) JSONObject().put("timerType", 1).put("timerWhen", connected).put("timerSystemCurrent", System.currentTimeMillis()) else null
        val island = JSONObject()
            .put("islandProperty", 1)
            .put("bigIslandArea", JSONObject()
                .put("imageTextInfoLeft", JSONObject().put("type", 1).put("picInfo", JSONObject().put("type", 1).put("pic", picKey)))
                .put("textInfo", JSONObject().put("title", who).put("content", text)))
            .put("smallIslandArea", JSONObject().put("picInfo", JSONObject().put("type", 1).put("pic", picKey)))
        val base = JSONObject().put("title", who).put("content", text).put("type", 2)
        if (timer != null) base.put("timerInfo", timer)
        val param = JSONObject().put(
            "param_v2",
            JSONObject()
                .put("protocol", 1).put("business", "call").put("islandFirstFloat", ringing).put("enableFloat", ringing)
                .put("updatable", true).put("ticker", "$who · $text").put("aodTitle", who)
                .put("baseInfo", base).put("param_island", island),
        )
        return Bundle().apply {
            putString("miui.focus.param", param.toString())
            putBundle("miui.focus.pics", Bundle().apply { putParcelable(picKey, icon) })
        }
    }
}
