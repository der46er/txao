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
            if (state != Call.STATE_DISCONNECTED) CallNotifier.update(this@TxaoInCallService, call)
        }
        override fun onDetailsChanged(call: Call, details: Call.Details) = CallManager.bump()
    }

    override fun onCallAdded(call: Call) {
        CallManager.service = this
        call.registerCallback(callback)
        CallManager.add(call)
        if (call.state == Call.STATE_RINGING) {
            startRinging(call)
            CallNotifier.update(this, call)
        } else {
            startActivity(callIntent())
            CallNotifier.update(this, call)
        }
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        CallManager.remove(call)
        stopRinging()
        val next = CallManager.primary()
        if (next == null) CallNotifier.cancel(this) else CallNotifier.update(this, next)
        if (CallManager.calls.value.isEmpty()) CallManager.service = null
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio.value = audioState
    }

    private fun callIntent() = Intent(this, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun startRinging(call: Call) {
        // A ringtone picked on the contact's page wins over the phone's default.
        val custom = Data.customRingtone(this, CallManager.number(call))?.let(android.net.Uri::parse)
        ringtone = RingtoneManager.getRingtone(this, custom ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.also { it.play() }
        getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
        getSystemService(Vibrator::class.java)?.cancel()
    }
}
