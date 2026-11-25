package com.example.keepsmilewifi

import android.content.Context
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build

object WifiUtil {
    fun clean(raw: String?): String? {
        val s = raw?.removeSurrounding("\"")?.trim()
        return if (s.isNullOrEmpty() || s == "<unknown ssid>") null else s
    }

    /** SSID from a network's capabilities if Android exposes it, else from the WifiManager. */
    fun ssidFrom(ctx: Context, caps: NetworkCapabilities?): String? {
        if (Build.VERSION.SDK_INT >= 29) {
            val info = caps?.transportInfo as? WifiInfo
            clean(info?.ssid)?.let { return it }
        }
        return current(ctx)
    }

    @Suppress("DEPRECATION")
    fun current(ctx: Context): String? {
        val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return clean(wm?.connectionInfo?.ssid)
    }
}
