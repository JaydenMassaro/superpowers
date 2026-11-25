package com.example.keepsmilewifi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder

/**
 * Foreground service that watches Wi-Fi and turns the light on when the phone joins the chosen network.
 */
class WifiWatcherService : Service() {

    private lateinit var cm: ConnectivityManager
    private var callback: ConnectivityManager.NetworkCallback? = null
    private var onTarget = false
    private val light by lazy { LightController(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED &&
            Build.VERSION.SDK_INT >= 31
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        val cfg = Prefs.load(this)
        val note = notification("Watching for Wi-Fi \"${cfg.ssid}\"")
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIF_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, note)
        }
        register()
        return START_STICKY
    }

    override fun onDestroy() {
        callback?.let { runCatching { cm.unregisterNetworkCallback(it) } }
        callback = null
        super.onDestroy()
    }

    private fun register() {
        if (callback != null) return
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        val cb: ConnectivityManager.NetworkCallback =
            if (Build.VERSION.SDK_INT >= 31) CallbackWithLocation(::onCaps, ::onLost) else PlainCallback(::onCaps, ::onLost)
        callback = cb
        cm.registerNetworkCallback(request, cb)
    }

    private fun onCaps(caps: NetworkCapabilities) {
        val cfg = Prefs.load(this)
        val ssid = WifiUtil.ssidFrom(this, caps) ?: return // can't tell (yet) - leave state alone
        if (ssid == cfg.ssid) {
            if (!onTarget) {
                onTarget = true
                trigger(cfg, on = true)
            }
        } else {
            onTarget = false
        }
    }

    private fun onLost() {
        if (!onTarget) return
        onTarget = false
        val cfg = Prefs.load(this)
        if (cfg.offOnDisconnect) trigger(cfg, on = false)
    }

    private fun trigger(cfg: Config, on: Boolean) {
        val bytes = parseHex(if (on) cfg.onHex else cfg.offHex)
        if (bytes == null || cfg.mac.isEmpty()) {
            update("Not configured: pick a light and a valid command")
            return
        }
        val word = if (on) "on" else "off"
        light.send(cfg.mac, bytes) { ok, msg ->
            update(if (ok) "Turned lights $word (${now()})" else "Failed to turn $word: $msg")
        }
    }

    private fun now() = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())

    private fun update(text: String) {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, notification(text))
    }

    private fun notification(text: String): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Wi-Fi light trigger", NotificationManager.IMPORTANCE_LOW)
        )
        val open = android.app.PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), android.app.PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Keepsmile Wi-Fi Lights")
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    // Two thin callback classes: the FLAG_INCLUDE_LOCATION_INFO constructor only exists on API 31+,
    // and it is what lets the callback see the real SSID there.
    private class PlainCallback(
        private val caps: (NetworkCapabilities) -> Unit,
        private val lost: () -> Unit,
    ) : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) = caps(c)
        override fun onLost(network: Network) = lost()
    }

    @android.annotation.TargetApi(31)
    private class CallbackWithLocation(
        private val caps: (NetworkCapabilities) -> Unit,
        private val lost: () -> Unit,
    ) : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
        override fun onCapabilitiesChanged(network: Network, c: NetworkCapabilities) = caps(c)
        override fun onLost(network: Network) = lost()
    }

    companion object {
        private const val CHANNEL = "wifi_light"
        private const val NOTIF_ID = 1
    }
}
