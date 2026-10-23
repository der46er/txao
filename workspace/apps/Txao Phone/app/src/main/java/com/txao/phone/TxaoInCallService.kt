package com.txao.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class TxaoInCallService : InCallService() {
    private var ringtone: Ringtone? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            CallManager.bump()
            if (state != Call.STATE_RINGING) stopRinging()
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = CallManager.bump()
    }

    override fun onCallAdded(call: Call) {
        CallManager.service = this
        call.registerCallback(callback)
        CallManager.add(call)
        if (call.state == Call.STATE_RINGING) {
            startRinging()
            notifyIncoming(call)
        } else {
            startActivity(callIntent())
        }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallManager.remove(call)
        stopRinging()
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
        if (CallManager.calls.value.isEmpty()) CallManager.service = null
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio.value = audioState
    }

    private fun callIntent() = Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startRinging() {
        ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        getSystemService(Vibrator::class.java)?.cancel()
    }

    private fun notifyIncoming(call: Call) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Incoming calls", NotificationManager.IMPORTANCE_HIGH))
        val number = CallManager.number(call)
        val who = Data.lookupName(this, number) ?: PhoneUtil.pretty(number).ifEmpty { "Unknown" }
        val pi = PendingIntent.getActivity(this, 0, callIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(who)
            .setContentText("Incoming call")
            .setCategory(Notification.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pi)
            .setFullScreenIntent(pi, true)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    private companion object {
        const val CHANNEL = "incoming_calls"
        const val NOTIF_ID = 1
    }
}
