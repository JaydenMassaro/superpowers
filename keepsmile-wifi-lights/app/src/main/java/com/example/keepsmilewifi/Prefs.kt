package com.example.keepsmilewifi

import android.content.Context

data class Config(
    val ssid: String,
    val mac: String,
    val deviceName: String,
    val onHex: String,
    val offHex: String,
    val offOnDisconnect: Boolean,
    val enabled: Boolean,
)

object Prefs {
    // ELK-BLEDOM style "power on" / "power off" frames. Many cheap BLE LED strips
    // (often sold under the Keepsmile name) use these; if yours doesn't, edit them in the app.
    const val DEFAULT_ON = "7E 00 04 F0 00 01 FF 00 EF"
    const val DEFAULT_OFF = "7E 00 04 00 00 00 FF 00 EF"

    private fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)

    fun load(c: Context): Config = sp(c).let {
        Config(
            ssid = it.getString("ssid", "") ?: "",
            mac = it.getString("mac", "") ?: "",
            deviceName = it.getString("deviceName", "") ?: "",
            onHex = it.getString("onHex", DEFAULT_ON) ?: DEFAULT_ON,
            offHex = it.getString("offHex", DEFAULT_OFF) ?: DEFAULT_OFF,
            offOnDisconnect = it.getBoolean("offOnDisconnect", false),
            enabled = it.getBoolean("enabled", false),
        )
    }

    fun save(c: Context, cfg: Config) {
        sp(c).edit()
            .putString("ssid", cfg.ssid)
            .putString("mac", cfg.mac)
            .putString("deviceName", cfg.deviceName)
            .putString("onHex", cfg.onHex)
            .putString("offHex", cfg.offHex)
            .putBoolean("offOnDisconnect", cfg.offOnDisconnect)
            .putBoolean("enabled", cfg.enabled)
            .apply()
    }

    fun setEnabled(c: Context, enabled: Boolean) {
        sp(c).edit().putBoolean("enabled", enabled).apply()
    }
}

/** Parses "7E 00 04 ..." / "0x7e,0x00" / "7e0004" into bytes. Returns null if invalid. */
fun parseHex(s: String): ByteArray? {
    val clean = s.replace("0x", "", ignoreCase = true).filter { it.isLetterOrDigit() }
    if (clean.isEmpty() || clean.length % 2 != 0) return null
    if (!clean.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
    return ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
