package com.txao.authenticator

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Non-secret preferences (secrets live in [Vault]). */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** Show dots instead of codes until a card is tapped. */
    var hideCodes by mutableStateOf(prefs.getBoolean("hideCodes", false))
        private set
    /** Show the upcoming code under the current one for the last seconds of a period. */
    var showNext by mutableStateOf(prefs.getBoolean("showNext", true))
        private set
    /** Split codes into two groups ("123 456"). */
    var groupDigits by mutableStateOf(prefs.getBoolean("groupDigits", true))
        private set
    /** Require the device PIN, pattern or biometrics to open the app. */
    var appLock by mutableStateOf(prefs.getBoolean("appLock", false))
        private set
    /** Wipe a copied code from the clipboard after 30 seconds. */
    var clearClipboard by mutableStateOf(prefs.getBoolean("clearClipboard", true))
        private set

    @JvmName("updateHideCodes") fun setHideCodes(v: Boolean) { hideCodes = v; prefs.edit().putBoolean("hideCodes", v).apply() }
    @JvmName("updateShowNext") fun setShowNext(v: Boolean) { showNext = v; prefs.edit().putBoolean("showNext", v).apply() }
    @JvmName("updateGroupDigits") fun setGroupDigits(v: Boolean) { groupDigits = v; prefs.edit().putBoolean("groupDigits", v).apply() }
    @JvmName("updateAppLock") fun setAppLock(v: Boolean) { appLock = v; prefs.edit().putBoolean("appLock", v).apply() }
    @JvmName("updateClearClipboard") fun setClearClipboard(v: Boolean) { clearClipboard = v; prefs.edit().putBoolean("clearClipboard", v).apply() }
}
