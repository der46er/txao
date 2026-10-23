package com.txao.phone

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import kotlinx.coroutines.flow.MutableStateFlow

/** Holds the live calls handed to us by the system so the call screen can observe them. */
object CallManager {
    val calls = MutableStateFlow<List<Call>>(emptyList())
    val tick = MutableStateFlow(0)
    val audio = MutableStateFlow<CallAudioState?>(null)
    var service: InCallService? = null

    fun add(call: Call) { calls.value = calls.value + call; bump() }
    fun remove(call: Call) { calls.value = calls.value - call; bump() }
    fun bump() { tick.value = tick.value + 1 }

    fun number(call: Call): String = call.details?.handle?.schemeSpecificPart.orEmpty()

    fun primary(): Call? = calls.value.firstOrNull { it.state == Call.STATE_RINGING }
        ?: calls.value.firstOrNull()

    fun toggleMute(muted: Boolean) { service?.setMuted(muted) }
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
    }
}
