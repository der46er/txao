package com.txao.phone

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager

/** Ways of making Txao Phone the default phone app, tried in order: role dialog, telecom dialog, settings page. */
object DefaultDialer {
    fun isHeld(ctx: Context): Boolean = ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)

    /** Available request intents, most direct first. Some OEM builds lack the first or ignore it, hence the fallbacks. */
    fun requestIntents(ctx: Context): List<Intent> {
        val rm = ctx.getSystemService(RoleManager::class.java)
        return buildList {
            if (rm.isRoleAvailable(RoleManager.ROLE_DIALER)) add(rm.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            @Suppress("DEPRECATION")
            add(Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, ctx.packageName))
            add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }
}
