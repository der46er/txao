package com.txao.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager

/** Runs dial-pad codes: USSD / MMI requests through the telephony stack, IMEI query and hidden-code broadcasts. */
object DialCodeHandler {
    /** Sends a USSD request and reports the network's reply (or why it failed) on the main thread. */
    fun sendUssd(ctx: Context, code: String, onResult: (text: String, failed: Boolean) -> Unit) {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        try {
            tm.sendUssdRequest(code, object : TelephonyManager.UssdResponseCallback() {
                override fun onReceiveUssdResponse(telephonyManager: TelephonyManager, request: String, response: CharSequence) =
                    onResult(response.toString(), false)

                override fun onReceiveUssdResponseFailed(telephonyManager: TelephonyManager, request: String, failureCode: Int) =
                    onResult(
                        when (failureCode) {
                            TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL -> "The network service is not available right now."
                            else -> "The network rejected the request (code $failureCode)."
                        },
                        true,
                    )
            }, Handler(Looper.getMainLooper()))
        } catch (e: SecurityException) {
            onResult("Allow the phone permission for Txao Phone to send network codes.", true)
        } catch (e: Exception) {
            onResult("This phone could not send the code.", true)
        }
    }

    /** IMEI if Android allows this app to read it (it normally does not on recent versions), otherwise null. */
    fun imei(ctx: Context): String? = try {
        ctx.getSystemService(TelephonyManager::class.java).imei
    } catch (_: Exception) {
        null
    }

    fun sendSecretCode(ctx: Context, code: String) {
        ctx.sendBroadcast(Intent("android.provider.Telephony.SECRET_CODE", Uri.parse("android_secret_code://$code")))
    }
}
