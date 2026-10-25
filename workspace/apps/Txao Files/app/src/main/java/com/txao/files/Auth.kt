package com.txao.files

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

private fun Context.fragmentActivity(): FragmentActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is FragmentActivity) return c
        c = c.baseContext
    }
    return null
}

/** Asks for fingerprint / face or the phone's PIN, pattern or password. Skips the prompt if the phone has no lock. */
fun authenticateWithPhone(context: Context, onOk: () -> Unit, onFail: (String) -> Unit) {
    val activity = context.fragmentActivity() ?: return onFail("Cannot show the lock prompt")
    val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    if (BiometricManager.from(context).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) {
        onOk()
        return
    }
    val prompt = BiometricPrompt(
        activity, ContextCompat.getMainExecutor(context),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
        },
    )
    prompt.authenticate(
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("Locked")
            .setSubtitle("Unlock your private files")
            .setAllowedAuthenticators(allowed)
            .build(),
    )
}
