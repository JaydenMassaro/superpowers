package com.example.keepsmilewifi

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

@SuppressLint("MissingPermission", "UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {

    private lateinit var ssidField: EditText
    private lateinit var lightLabel: TextView
    private lateinit var onField: EditText
    private lateinit var offField: EditText
    private lateinit var offOnDisconnect: CheckBox
    private lateinit var enableSwitch: Switch
    private lateinit var status: TextView

    private var mac = ""
    private var deviceName = ""
    private var pending: (() -> Unit)? = null
    private var askedBackground = false
    private val main = Handler(Looper.getMainLooper())
    private val light by lazy { LightController(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        val cfg = Prefs.load(this)
        mac = cfg.mac
        deviceName = cfg.deviceName
        ssidField.setText(cfg.ssid)
        onField.setText(cfg.onHex)
        offField.setText(cfg.offHex)
        offOnDisconnect.isChecked = cfg.offOnDisconnect
        refreshLightLabel()
        enableSwitch.isChecked = cfg.enabled
        enableSwitch.setOnCheckedChangeListener { _, checked -> if (checked) enable() else disable() }
    }

    override fun onPause() {
        save()
        super.onPause()
    }

    // ---------- UI ----------

    private fun buildUi() {
        val pad = (16 * resources.displayMetrics.density).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 16f; setPadding(0, pad, 0, 0) }
        fun button(t: String, onClick: () -> Unit) = Button(this).apply { text = t; setOnClickListener { onClick() } }

        col.addView(label("1. Wi-Fi network that should trigger the lights"))
        ssidField = EditText(this).apply { hint = "Network name (SSID)"; setSingleLine() }
        col.addView(ssidField)
        col.addView(button("Use the Wi-Fi I'm connected to now") { useCurrentWifi() })

        col.addView(label("2. Your light"))
        lightLabel = TextView(this)
        col.addView(lightLabel)
        col.addView(button("Find light (scan Bluetooth)") { scanForLights() })

        col.addView(label("3. Commands (hex). Defaults suit most BLE LED strips."))
        onField = EditText(this).apply { hint = "ON command"; setSingleLine() }
        offField = EditText(this).apply { hint = "OFF command"; setSingleLine() }
        col.addView(onField)
        col.addView(offField)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(button("Test ON") { test(true) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(button("Test OFF") { test(false) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(row)

        offOnDisconnect = CheckBox(this).apply { text = "Also turn lights off when I leave that Wi-Fi" }
        col.addView(offOnDisconnect)

        enableSwitch = Switch(this).apply { text = "Turn lights on automatically"; textSize = 18f }
        col.addView(enableSwitch, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad })

        status = TextView(this).apply { setPadding(0, pad, 0, 0) }
        col.addView(status)

        setContentView(ScrollView(this).apply { addView(col) })
    }

    private fun refreshLightLabel() {
        lightLabel.text = if (mac.isEmpty()) "No light selected" else "${deviceName.ifEmpty { "Light" }} ($mac)"
    }

    private fun say(msg: String) {
        status.text = msg
    }

    private fun save() {
        Prefs.save(
            this,
            Config(
                ssid = ssidField.text.toString().trim(),
                mac = mac,
                deviceName = deviceName,
                onHex = onField.text.toString().trim(),
                offHex = offField.text.toString().trim(),
                offOnDisconnect = offOnDisconnect.isChecked,
                enabled = enableSwitch.isChecked,
            )
        )
    }

    // ---------- actions ----------

    private fun useCurrentWifi() = withPermissions {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val ssid = WifiUtil.current(this)
        when {
            ssid != null -> { ssidField.setText(ssid); say("Using \"$ssid\"") }
            !lm.isLocationEnabled -> say("Turn on Location in your phone's quick settings - Android hides the Wi-Fi name otherwise.")
            else -> say("Couldn't read the Wi-Fi name. Are you connected to Wi-Fi?")
        }
    }

    private fun scanForLights() = withPermissions {
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { say("Turn Bluetooth on first."); return@withPermissions }
        val scanner = adapter.bluetoothLeScanner ?: run { say("Bluetooth LE scanner unavailable."); return@withPermissions }
        val found = LinkedHashMap<String, Pair<String, Int>>() // mac -> (name, rssi)
        val cb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) {
                val name = r.scanRecord?.deviceName ?: r.device.name ?: return
                found[r.device.address] = name to r.rssi
            }
        }
        say("Scanning for 8 seconds - make sure the light is powered on and the Keepsmile app is closed...")
        scanner.startScan(cb)
        main.postDelayed({
            scanner.stopScan(cb)
            val items = found.entries.sortedByDescending { it.value.second }
            if (items.isEmpty()) { say("No named Bluetooth devices found. Close the Keepsmile app (it may hold the connection) and try again."); return@postDelayed }
            say("Pick your light (strongest signal first).")
            AlertDialog.Builder(this)
                .setTitle("Choose your light")
                .setItems(items.map { "${it.value.first}  (${it.key})  ${it.value.second} dBm" }.toTypedArray()) { _, i ->
                    mac = items[i].key
                    deviceName = items[i].value.first
                    refreshLightLabel()
                    save()
                }
                .show()
        }, 8000)
    }

    private fun test(on: Boolean) = withPermissions {
        save()
        val cfg = Prefs.load(this)
        val bytes = parseHex(if (on) cfg.onHex else cfg.offHex)
        when {
            cfg.mac.isEmpty() -> say("Pick a light first.")
            bytes == null -> say("That command isn't valid hex, e.g. 7E 00 04 F0 00 01 FF 00 EF")
            else -> {
                say("Sending...")
                light.send(cfg.mac, bytes) { ok, msg -> say(if (ok) "$msg. Did the light react?" else "Failed: $msg") }
            }
        }
    }

    private fun enable() {
        save()
        val cfg = Prefs.load(this)
        val problem = when {
            cfg.ssid.isEmpty() -> "Enter or capture a Wi-Fi name first."
            cfg.mac.isEmpty() -> "Pick a light first."
            parseHex(cfg.onHex) == null -> "The ON command isn't valid hex."
            else -> null
        }
        if (problem != null) {
            enableSwitch.setOnCheckedChangeListener(null)
            enableSwitch.isChecked = false
            enableSwitch.setOnCheckedChangeListener { _, c -> if (c) enable() else disable() }
            say(problem)
            return
        }
        withPermissions {
            Prefs.setEnabled(this, true)
            startForegroundService(Intent(this, WifiWatcherService::class.java))
            say("Watching for \"${cfg.ssid}\". Keep the notification; if lights don't fire in the background, set this app's battery to Unrestricted and Location to \"Allow all the time\".")
        }
    }

    private fun disable() {
        Prefs.setEnabled(this, false)
        stopService(Intent(this, WifiWatcherService::class.java))
        say("Stopped.")
    }

    // ---------- permissions ----------

    private fun neededPermissions(): List<String> = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
    }.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }

    private fun withPermissions(action: () -> Unit) {
        val need = neededPermissions()
        if (need.isNotEmpty()) {
            pending = action
            requestPermissions(need.toTypedArray(), REQ)
            return
        }
        // Android only reveals the SSID to background code with "all the time" location.
        if (Build.VERSION.SDK_INT >= 29 && !askedBackground &&
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            askedBackground = true
            pending = action
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ)
            return
        }
        action()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        val action = pending ?: return
        pending = null
        if (neededPermissions().isEmpty()) withPermissions(action)
        else {
            Toast.makeText(this, "Location, Bluetooth and notification permissions are needed.", Toast.LENGTH_LONG).show()
            if (enableSwitch.isChecked && !Prefs.load(this).enabled) enableSwitch.isChecked = false
        }
    }

    companion object {
        private const val REQ = 42
    }
}
