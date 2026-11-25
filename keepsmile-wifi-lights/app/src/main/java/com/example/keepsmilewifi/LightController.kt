package com.example.keepsmilewifi

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Connects to the light over BLE, writes one command frame, disconnects.
 * Results are always delivered on the main thread.
 */
@SuppressLint("MissingPermission") // callers check permissions before using this
class LightController(context: Context) {
    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    fun send(mac: String, payload: ByteArray, retries: Int = 2, onResult: (Boolean, String) -> Unit) {
        Session(mac, payload) { ok, msg ->
            if (!ok && retries > 0) {
                // Android BLE is flaky (status 133 etc.) - a fresh attempt usually works.
                main.postDelayed({ send(mac, payload, retries - 1, onResult) }, 1000)
            } else {
                onResult(ok, msg)
            }
        }.start()
    }

    private inner class Session(
        private val mac: String,
        private val payload: ByteArray,
        private val done: (Boolean, String) -> Unit,
    ) : BluetoothGattCallback() {
        private val finished = AtomicBoolean(false)
        private var gatt: BluetoothGatt? = null
        private val timeout = Runnable { finish(false, "Timed out talking to the light") }

        fun start() {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            if (adapter == null || !adapter.isEnabled) {
                finish(false, "Bluetooth is off")
                return
            }
            val device = try {
                adapter.getRemoteDevice(mac)
            } catch (e: IllegalArgumentException) {
                finish(false, "Invalid light address: $mac")
                return
            }
            main.postDelayed(timeout, 12_000)
            gatt = device.connectGatt(ctx, false, this, BluetoothDevice.TRANSPORT_LE)
        }

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when {
                newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS ->
                    g.discoverServices()
                newState == BluetoothProfile.STATE_DISCONNECTED ->
                    finish(false, "Disconnected before the command was sent (status $status)")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = pickCharacteristic(g)
            if (status != BluetoothGatt.GATT_SUCCESS || ch == null) {
                finish(false, "No writable characteristic found on the light")
                return
            }
            val type = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            val started = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, payload, type) == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run {
                    ch.writeType = type
                    ch.value = payload
                    g.writeCharacteristic(ch)
                }
            }
            if (!started) finish(false, "Could not start the write")
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, ch: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) finish(true, "Command sent") else finish(false, "Write failed (status $status)")
        }

        private fun finish(ok: Boolean, msg: String) {
            if (!finished.compareAndSet(false, true)) return
            main.removeCallbacks(timeout)
            val g = gatt
            // Small delay so the last write is flushed before we tear the link down.
            main.postDelayed({
                try { g?.disconnect() } catch (_: Exception) {}
                try { g?.close() } catch (_: Exception) {}
            }, 300)
            main.post { done(ok, msg) }
        }
    }

    companion object {
        private val FFF0 = uuid16(0xFFF0)
        private val FFF3 = uuid16(0xFFF3)
        private val FFE1 = uuid16(0xFFE1)

        private fun uuid16(v: Int) = UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", v))

        private fun writable(c: BluetoothGattCharacteristic) =
            (c.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) != 0

        /** Known LED-strip characteristics first, then anything writable outside the generic GATT services. */
        fun pickCharacteristic(g: BluetoothGatt): BluetoothGattCharacteristic? {
            val pairs = g.services.flatMap { s -> s.characteristics.map { s.uuid to it } }
            return pairs.firstOrNull { (_, c) -> c.uuid == FFF3 && writable(c) }?.second
                ?: pairs.firstOrNull { (_, c) -> c.uuid == FFE1 && writable(c) }?.second
                ?: pairs.firstOrNull { (s, c) -> s == FFF0 && writable(c) }?.second
                ?: pairs.firstOrNull { (s, c) ->
                    !s.toString().startsWith("00001800") && !s.toString().startsWith("00001801") && writable(c)
                }?.second
        }
    }
}
