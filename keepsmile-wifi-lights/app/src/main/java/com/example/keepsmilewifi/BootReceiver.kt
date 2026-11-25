package com.example.keepsmilewifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Prefs.load(context).enabled) {
            context.startForegroundService(Intent(context, WifiWatcherService::class.java))
        }
    }
}
