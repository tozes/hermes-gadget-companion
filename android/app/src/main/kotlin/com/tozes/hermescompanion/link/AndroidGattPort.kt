package com.tozes.hermescompanion.link

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.tozes.hermescompanion.core.GadgetService
import com.tozes.hermescompanion.core.ble.GattPort
import com.tozes.hermescompanion.core.ble.GattSession
import java.util.UUID

/**
 * [GattPort] over Android's `BluetoothGatt`, for the [GattSession] that
 * drives it. GATT callbacks arrive on a Binder thread and are posted to the
 * main thread, where the session runs.
 *
 * Only verifiable on a phone with a gadget: everything here talks to the
 * Bluetooth stack. The session's logic is tested on the JVM.
 *
 * The caller holds `BLUETOOTH_CONNECT` (Android 12+) before connecting;
 * [BluetoothGadgetLink] checks it.
 */
@SuppressLint("MissingPermission")
class AndroidGattPort(private val context: Context, private val adapter: BluetoothAdapter) : GattPort {
    lateinit var session: GattSession
    private val main = Handler(Looper.getMainLooper())
    private var gatt: BluetoothGatt? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            onMain(gatt) { session.onConnectionStateChange(status, newState == BluetoothProfile.STATE_CONNECTED) }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            onMain(gatt) { session.onMtuChanged(status) }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            onMain(gatt) { session.onServicesDiscovered(status) }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            onMain(gatt) { session.onDescriptorWrite(status) }
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (characteristic.uuid != COMMAND) return
            onMain(gatt) { session.onCharacteristicWrite(status) }
        }

        // Android 13 and later.
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid != REPLY) return
            val bytes = value.copyOf()
            onMain(gatt) { session.onNotification(bytes) }
        }

        // Android 12 and earlier; Android 13 calls the method above instead.
        @Deprecated("Deprecated in Android 13")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU || characteristic.uuid != REPLY) return
            @Suppress("DEPRECATION")
            val bytes = characteristic.value?.copyOf() ?: return
            onMain(gatt) { session.onNotification(bytes) }
        }
    }

    /** Runs a callback for the current client only: a closed one's late callbacks are dropped. */
    private fun onMain(from: BluetoothGatt, block: () -> Unit) {
        val run = { if (from === gatt) block() }
        if (Looper.myLooper() == Looper.getMainLooper()) run() else main.post(run)
    }

    override fun connect(address: String): Boolean {
        close()
        val device = remote(address) ?: return false
        // Deprecated in API 37 in favour of connection settings that older
        // versions don't have; this app runs from Android 10 (API 29).
        @Suppress("DEPRECATION")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
        return gatt != null
    }

    override fun requestMtu(mtu: Int): Boolean = gatt?.requestMtu(mtu) == true

    override fun discoverServices(): Boolean = gatt?.discoverServices() == true

    private fun characteristic(uuid: UUID): BluetoothGattCharacteristic? = gatt?.getService(SERVICE)?.getCharacteristic(uuid)

    override fun hasGadgetService(): Boolean = characteristic(COMMAND) != null && characteristic(REPLY) != null

    override fun enableReplyNotifications(): Boolean {
        val gatt = gatt ?: return false
        val reply = characteristic(REPLY) ?: return false
        val config = reply.getDescriptor(CLIENT_CONFIG) ?: return false
        if (!gatt.setCharacteristicNotification(reply, true)) return false
        val value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(config, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            config.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(config)
        }
    }

    override fun writeCommand(bytes: ByteArray): Boolean {
        val gatt = gatt ?: return false
        val command = characteristic(COMMAND) ?: return false
        val type = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(command, bytes, type) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            command.writeType = type
            @Suppress("DEPRECATION")
            command.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(command)
        }
    }

    override fun isBonded(address: String): Boolean = remote(address)?.bondState == BluetoothDevice.BOND_BONDED

    override fun createBond(address: String): Boolean = remote(address)?.createBond() == true

    override fun close() {
        val open = gatt ?: return
        gatt = null
        open.disconnect()
        open.close()
    }

    private fun remote(address: String): BluetoothDevice? =
        if (BluetoothAdapter.checkBluetoothAddress(address)) adapter.getRemoteDevice(address) else null

    companion object {
        val SERVICE: UUID = UUID.fromString(GadgetService.SERVICE)
        val COMMAND: UUID = UUID.fromString(GadgetService.COMMAND)
        val REPLY: UUID = UUID.fromString(GadgetService.REPLY)
        val CLIENT_CONFIG: UUID = UUID.fromString(GadgetService.CLIENT_CONFIG)
    }
}
