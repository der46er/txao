package com.txao.sms

import android.app.Application

class TxaoSmsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.createChannel(this)
    }
}
