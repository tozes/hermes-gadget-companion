package com.tozes.hermescompanion.core.ble

/**
 * What a [GattSession] asks of the phone's Bluetooth stack, for one gadget.
 * The app implements it over `BluetoothGatt`; tests use a fake. Each call
 * starts one operation and returns whether it started; the outcome comes back
 * through the session's `on…` callbacks, on the main thread.
 */
interface GattPort {
    /** `connectGatt(autoConnect = false, TRANSPORT_LE)`; ends in `onConnectionStateChange`. */
    fun connect(address: String): Boolean

    /** Ends in `onMtuChanged`. */
    fun requestMtu(mtu: Int): Boolean

    /** Ends in `onServicesDiscovered`. */
    fun discoverServices(): Boolean

    /** Whether the discovered services include the gadget's, with its command and reply characteristics. */
    fun hasGadgetService(): Boolean

    /**
     * Turns on reply notifications locally and writes the reply characteristic's
     * Client Characteristic Configuration descriptor; ends in `onDescriptorWrite`.
     */
    fun enableReplyNotifications(): Boolean

    /** Writes one console line to the command characteristic, with response; ends in `onCharacteristicWrite`. */
    fun writeCommand(bytes: ByteArray): Boolean

    fun isBonded(address: String): Boolean

    /** Starts pairing; progress arrives as `onBondStateChanged`. */
    fun createBond(address: String): Boolean

    /** Disconnects and closes the GATT client. Safe to call when nothing is open. */
    fun close()
}

/** A device's bond with the phone, from `ACTION_BOND_STATE_CHANGED`. */
enum class BondState { NONE, BONDING, BONDED }

/** Status codes from `BluetoothGatt` and the HCI disconnect reasons Android passes through. */
object GattStatus {
    const val SUCCESS = 0

    /** `GATT_INSUFFICIENT_AUTHENTICATION`; as a disconnect reason, HCI "authentication failure". */
    const val INSUFFICIENT_AUTHENTICATION = 0x05

    /** HCI "PIN or key missing": the gadget no longer has this phone's keys. */
    const val PIN_OR_KEY_MISSING = 0x06
    const val INSUFFICIENT_AUTHORIZATION = 0x08
    const val INSUFFICIENT_ENCRYPTION_KEY_SIZE = 0x0c
    const val INSUFFICIENT_ENCRYPTION = 0x0f

    /** HCI "connection terminated due to MIC failure": encryption with stale keys. */
    const val MIC_FAILURE = 0x3d

    /** `GATT_ERROR`, the catch-all Android reports for a connect that didn't come up. */
    const val ERROR = 133

    /** `GATT_AUTH_FAIL`: pairing failed during an operation. */
    const val AUTH_FAIL = 137

    /** A write refused because the link isn't paired, or not with keys the gadget has. */
    val writeNeedsPairing = setOf(INSUFFICIENT_AUTHENTICATION, INSUFFICIENT_AUTHORIZATION, INSUFFICIENT_ENCRYPTION_KEY_SIZE, INSUFFICIENT_ENCRYPTION, AUTH_FAIL)

    /** A disconnect caused by keys that don't match. */
    val disconnectForKeys = setOf(INSUFFICIENT_AUTHENTICATION, PIN_OR_KEY_MISSING, MIC_FAILURE)
}
