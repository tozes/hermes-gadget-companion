package com.tozes.hermescompanion.link

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.edit
import com.tozes.hermescompanion.core.DiscoveredGadget
import com.tozes.hermescompanion.core.GadgetLink
import com.tozes.hermescompanion.core.LinkEvent
import com.tozes.hermescompanion.core.LinkFailure
import com.tozes.hermescompanion.core.RadioState
import com.tozes.hermescompanion.core.ble.BondState
import com.tozes.hermescompanion.core.ble.GattSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel

/** The runtime permissions the link needs, by Android version. */
object BluetoothPermissions {
    /** Android 12+: nearby devices; Android 10 and 11: location, which BLE scanning needs there. */
    val required: Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(context: Context): Boolean =
        required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    /** Android 10 and 11 find no Bluetooth LE devices while Location is off. */
    fun locationServicesOff(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return false
        val location = context.getSystemService(LocationManager::class.java) ?: return false
        return !location.isLocationEnabled
    }
}

/**
 * The real link: Android's Bluetooth LE to the gadget's console service, the
 * same contract as the iOS app's Core Bluetooth `BluetoothGadgetLink`.
 *
 * This class does what needs the radio: permissions, the adapter's state,
 * scanning, and the bond broadcasts. The connection itself (the GATT
 * operation queue, pairing, replies and how failures map to a [LinkFailure])
 * is [GattSession], which is tested on the JVM. Nothing in this file has run
 * against a gadget yet: see the README's list of what a phone must check.
 *
 * Every call and event is on the main thread.
 */
@SuppressLint("MissingPermission") // Every Bluetooth call is behind hasPermissions().
class BluetoothGadgetLink(context: Context) : GadgetLink {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? = this.context.getSystemService(BluetoothManager::class.java)?.adapter
    private val scope: CoroutineScope = MainScope()
    private val port = adapter?.let { AndroidGattPort(this.context, it) }
    private val session = port?.let { port ->
        GattSession(port, scope, { event -> report(event) }).also { port.session = it }
    }

    override var onEvent: ((LinkEvent) -> Unit)? = null
    override var radio: RadioState = RadioState.UNKNOWN
        private set

    /** The user turned down the permission request; until then, a missing permission reads as "starting". */
    private var permissionDenied = false
    private var wantsScan = false
    private var scanning = false

    /** Addresses seen advertising the service, or paired with before, so bonded gadgets can be listed. */
    private val gadgets = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
    private var connectedAddress: String? = null

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> refresh()
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.device() ?: return
                    if (device.address != connectedAddress) return
                    val state = when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)) {
                        BluetoothDevice.BOND_BONDED -> BondState.BONDED
                        BluetoothDevice.BOND_BONDING -> BondState.BONDING
                        else -> BondState.NONE
                    }
                    session?.onBondStateChanged(state)
                }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            discovered(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::discovered)
        }

        override fun onScanFailed(errorCode: Int) {
            // Usually SCAN_FAILED_APPLICATION_REGISTRATION_FAILED, or too many
            // scans started within 30 s: the store's own timer ends the scan.
            scanning = false
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            this.context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            this.context.registerReceiver(receiver, filter)
        }
        radio = currentRadio()
    }

    /** Stops listening; the link can't be used afterwards. */
    fun close() {
        disconnect()
        stopScan()
        runCatching { context.unregisterReceiver(receiver) }
        scope.cancel()
    }

    // Permissions and the radio

    fun hasPermissions(): Boolean = BluetoothPermissions.granted(context)

    /** The Find screen asked for the permissions; [granted] is the answer. */
    fun permissionResult(granted: Boolean) {
        permissionDenied = !granted
        refresh()
    }

    /** Re-reads the radio, e.g. when the app comes back from Settings. */
    fun refresh() {
        val state = currentRadio()
        if (state == radio) return
        radio = state
        if (state != RadioState.POWERED_ON) {
            scanning = false
            // The stack drops connections without a callback here.
            session?.radioLost()
        }
        onEvent?.invoke(LinkEvent.Radio(state))
        if (state == RadioState.POWERED_ON && wantsScan) startScan()
    }

    private fun currentRadio(): RadioState {
        val adapter = adapter ?: return RadioState.UNSUPPORTED
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) return RadioState.UNSUPPORTED
        if (!hasPermissions()) return if (permissionDenied) RadioState.UNAUTHORIZED else RadioState.UNKNOWN
        return when (adapter.state) {
            BluetoothAdapter.STATE_ON -> RadioState.POWERED_ON
            BluetoothAdapter.STATE_TURNING_ON -> RadioState.UNKNOWN
            else -> RadioState.POWERED_OFF
        }
    }

    // GadgetLink

    override fun startScan() {
        wantsScan = true
        val scanner = adapter?.bluetoothLeScanner
        if (radio != RadioState.POWERED_ON || scanner == null || !hasPermissions()) return
        // Gadgets this phone is paired with: one connected to the phone stops
        // advertising, so list them too, as iOS lists connected peripherals.
        for (device in adapter.bondedDevices.orEmpty()) {
            if (isGadget(device)) onEvent?.invoke(LinkEvent.Discovered(DiscoveredGadget(device.address, device.name ?: DEFAULT_NAME, BONDED_RSSI)))
        }
        if (scanning) return
        val filters = listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(AndroidGattPort.SERVICE)).build())
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(filters, settings, scanCallback)
        scanning = true
    }

    override fun stopScan() {
        wantsScan = false
        if (!scanning) return
        scanning = false
        if (radio == RadioState.POWERED_ON && hasPermissions()) adapter?.bluetoothLeScanner?.stopScan(scanCallback)
    }

    override fun connect(gadget: DiscoveredGadget) {
        val session = session
        if (session == null || radio != RadioState.POWERED_ON || !hasPermissions()) {
            onEvent?.invoke(LinkEvent.Failed(LinkFailure.UNREACHABLE))
            return
        }
        connectedAddress = gadget.id
        session.connect(gadget.id)
    }

    override fun disconnect() {
        connectedAddress = null
        session?.disconnect()
    }

    override suspend fun send(line: String): String =
        session?.send(line) ?: throw com.tozes.hermescompanion.core.SendException.NotConnected()

    // Private

    private fun report(event: LinkEvent) {
        if (event == LinkEvent.Connected) connectedAddress?.let(::remember)
        onEvent?.invoke(event)
    }

    private fun discovered(result: ScanResult) {
        if (!scanning) return
        val device = result.device
        remember(device.address)
        val name = result.scanRecord?.deviceName ?: device.name ?: DEFAULT_NAME
        onEvent?.invoke(LinkEvent.Discovered(DiscoveredGadget(device.address, name, result.rssi)))
    }

    private fun remember(address: String) {
        if (!gadgets.contains(address)) gadgets.edit { putBoolean(address, true) }
    }

    private fun isGadget(device: BluetoothDevice): Boolean =
        gadgets.contains(device.address) || device.uuids.orEmpty().any { it.uuid == AndroidGattPort.SERVICE }

    private fun Intent.device(): BluetoothDevice? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }

    private companion object {
        const val PREFERENCES = "gadgets"
        const val DEFAULT_NAME = "Hermes Gadget"

        /** A listed bonded gadget has no reading; this shows as "good", like iOS's connected peripherals. */
        const val BONDED_RSSI = -60
    }
}
