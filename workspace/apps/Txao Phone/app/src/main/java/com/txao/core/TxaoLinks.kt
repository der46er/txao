package com.txao.core

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Cross-app contract (see workspace/SYNC-SPEC.md, "Cross-app links").
 *
 * No app depends on another being installed: every call goes through [launch], which returns false
 * when the target app is absent so the caller can hide or disable the feature.
 */
object TxaoApps {
    const val NOTES = "com.txao.notes"
    const val CALCULATOR = "com.txao.calculator"
    const val GALLERY = "com.txao.gallery"
    const val CALENDAR = "com.txao.calendar"
    const val AUTHENTICATOR = "com.txao.authenticator"
    const val SMS = "com.txao.sms"
    const val PHONE = "com.txao.phone"
    const val MUSIC = "com.txao.music"
}

object TxaoLinks {
    /** Opens txao://<app>/<kind>/<id>, e.g. txao://notes/note/ab12..., txao://calendar/event/ab12... */
    fun uri(app: String, kind: String, id: String): Uri = Uri.parse("txao://$app/$kind/$id")

    /** Share text into Txao Notes as a new note (Notes already handles ACTION_SEND text/plain). */
    fun sendToNotes(ctx: Context, text: String, title: String? = null): Boolean =
        launch(ctx, Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(TxaoApps.NOTES)
            .putExtra(Intent.EXTRA_TEXT, text).apply { if (title != null) putExtra(Intent.EXTRA_SUBJECT, title) })

    /** Ask Txao Calendar to open its "new event" sheet prefilled. Times in epoch millis. */
    fun addToCalendar(ctx: Context, title: String, startMillis: Long, notes: String? = null): Boolean =
        launch(ctx, Intent("com.txao.action.ADD_EVENT").setPackage(TxaoApps.CALENDAR)
            .putExtra("title", title).putExtra("start", startMillis).apply { if (notes != null) putExtra("notes", notes) })

    fun open(ctx: Context, app: String, kind: String, id: String): Boolean =
        launch(ctx, Intent(Intent.ACTION_VIEW, uri(app, kind, id)).setPackage(app))

    fun launch(ctx: Context, intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Exception) { false }
}
