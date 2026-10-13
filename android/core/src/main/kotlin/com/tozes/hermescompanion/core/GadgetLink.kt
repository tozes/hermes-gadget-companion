package com.tozes.hermescompanion.core

/** The phone's Bluetooth radio. */
enum class RadioState {
    UNKNOWN,
    POWERED_ON,
    POWERED_OFF,

    /** The user hasn't allowed Bluetooth for this app. */
    UNAUTHORIZED,
    UNSUPPORTED,
}

/**
 * A gadget seen while scanning: it advertises the Hermes service while
 * Phone app pairing is open, and about once a second while a phone is paired.
 * [id] is the Bluetooth address on Android (a UUID for the mock gadgets).
 */
data class DiscoveredGadget(val id: String, val name: String, val rssi: Int) {
    /** 1 (weak) to 4 (strong). */
    val signalLevel: Int
        get() = when {
            rssi >= -55 -> 4
            rssi >= -67 -> 3
            rssi >= -80 -> 2
            else -> 1
        }

    val signalDescription: String get() = listOf("weak", "fair", "good", "strong")[signalLevel - 1]
}

/** Why a connection didn't come up or went away. */
enum class LinkFailure {
    /** Android reported that pairing failed: a wrong code, or the user cancelled the dialog. */
    PAIRING_REJECTED,

    /** No pairing happened in time: usually the gadget's pairing window had closed. */
    PAIRING_TIMED_OUT,

    /**
     * The phone still has keys the gadget has dropped (after `phone forget` or a
     * factory reset): the phone must forget the gadget before pairing again.
     */
    PAIRING_REMOVED,

    /** The gadget couldn't be reached. */
    UNREACHABLE,

    /** An established link dropped. */
    CONNECTION_LOST,
}

/** What a link reports to its owner. All events arrive on the main thread. */
sealed interface LinkEvent {
    data class Radio(val state: RadioState) : LinkEvent
    data class Discovered(val gadget: DiscoveredGadget) : LinkEvent
    data object Connecting : LinkEvent

    /**
     * Connected and waiting on pairing, which makes Android show its pairing
     * dialog. The passkey is on the gadget's screen; the app never sees it.
     */
    data object AwaitingPasskey : LinkEvent

    /** Paired, subscribed to replies, and ready for commands. */
    data object Connected : LinkEvent
    data class Failed(val failure: LinkFailure) : LinkEvent

    /** The link closed: a null [failure] when the app asked for it. */
    data class Disconnected(val failure: LinkFailure?) : LinkEvent
}

/** Why [GadgetLink.send] didn't return a reply. */
sealed class SendException(message: String) : Exception(message) {
    class NotConnected : SendException("not connected")
    class TimedOut : SendException("no reply")
}

/**
 * A Bluetooth LE link to a gadget's console (see the SDK's protocol guide,
 * "Phone app over Bluetooth"). The phone writes one console line and gets
 * one `@` reply. `BluetoothGadgetLink` in the app is the real one;
 * [MockGadgetLink] acts out the debug menu's scenarios.
 *
 * Every call is made on the main thread, and events are delivered on it.
 */
interface GadgetLink {
    /** Receives every event; the store sets it once. */
    var onEvent: ((LinkEvent) -> Unit)?
    val radio: RadioState

    fun startScan()
    fun stopScan()

    /** Connects and pairs; progress and the outcome arrive as events. */
    fun connect(gadget: DiscoveredGadget)
    fun disconnect()

    /**
     * Writes one console line (UTF-8, at most 256 bytes) and returns the
     * reply text without its terminating NUL. Callers send one line at a time.
     *
     * @throws SendException when the link is down or no reply arrives.
     */
    suspend fun send(line: String): String
}
