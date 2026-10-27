package com.txao.phone

import android.telecom.Call
import android.telecom.CallScreeningService

/** Rejects incoming calls from numbers on the system block list, without ringing. */
class TxaoScreeningService : CallScreeningService() {
    override fun onScreenCall(callDetails: Call.Details) {
        val number = callDetails.handle?.schemeSpecificPart.orEmpty()
        val incoming = callDetails.callDirection == Call.Details.DIRECTION_INCOMING
        // Blocked numbers and contacts set to "send to voicemail" never ring.
        val blocked = incoming && (Data.isBlocked(this, number) || Data.sentToVoicemail(this, number))
        respondToCall(
            callDetails,
            CallResponse.Builder()
                .setDisallowCall(blocked).setRejectCall(blocked)
                .setSkipCallLog(false).setSkipNotification(blocked)
                .build(),
        )
    }
}
