package com.txao.calendar

import android.app.Application

class TxaoCalendarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Reminders.ensureChannel(this)
    }
}
