package com.tozes.hermescompanion.core

/**
 * The states the mock link can act out, picked in the debug menu (long-press
 * any screen's title). Without one, the app uses real Bluetooth.
 */
enum class MockScenario(val rawValue: String) {
    /** What "Try the demo" shows: a set-up gadget to explore, not saved across launches. */
    DEMO("demo"),
    NEW_GADGET("newGadget"),
    ONLINE("online"),
    AWAITING_APPROVAL("awaitingApproval"),
    LOW_BATTERY("lowBattery"),
    NOTHING_NEARBY("nothingNearby"),
    BLUETOOTH_OFF("bluetoothOff"),
    BLUETOOTH_DENIED("bluetoothDenied"),
    PAIRING_REJECTED("pairingRejected"),
    PAIRING_TIMES_OUT("pairingTimesOut"),
    WIFI_FAILS("wifiFails"),
    HERMES_UNREACHABLE("hermesUnreachable"),
    FLAKY("flaky");

    val title: String
        get() = when (this) {
            DEMO -> "Demo"
            NEW_GADGET -> "New gadget, no Wi-Fi"
            ONLINE -> "Online and approved"
            AWAITING_APPROVAL -> "Waiting for Hermes approval"
            LOW_BATTERY -> "Low battery"
            NOTHING_NEARBY -> "No gadgets nearby"
            BLUETOOTH_OFF -> "Bluetooth off"
            BLUETOOTH_DENIED -> "Bluetooth not allowed"
            PAIRING_REJECTED -> "Pairing fails"
            PAIRING_TIMES_OUT -> "Pairing times out"
            WIFI_FAILS -> "Wi-Fi join fails"
            HERMES_UNREACHABLE -> "Wi-Fi works, Hermes doesn't"
            FLAKY -> "Busy replies and dropped links"
        }

    val detail: String
        get() = when (this) {
            DEMO -> "What Try the demo shows: set up, approved, on battery, with two saved networks."
            NEW_GADGET -> "The status from a real device: no network, not approved. Wi-Fi setup succeeds."
            ONLINE -> "Set up, approved, charging over USB."
            AWAITING_APPROVAL -> "Online, showing a pairing code for the Hermes host."
            LOW_BATTERY -> "Online, on battery at 9%."
            NOTHING_NEARBY -> "The scan finds nothing and ends after 20 seconds."
            BLUETOOTH_OFF -> "The phone's Bluetooth is off."
            BLUETOOTH_DENIED -> "The app isn't allowed to use Bluetooth."
            PAIRING_REJECTED -> "Android reports a wrong code."
            PAIRING_TIMES_OUT -> "The pairing window closed before the code was entered."
            WIFI_FAILS -> "Joining Wi-Fi never brings the network up."
            HERMES_UNREACHABLE -> "Wi-Fi joins, but the server never answers."
            FLAKY -> "Some commands get \"@error device busy\"; the link drops after a minute."
        }

    val radio: RadioState
        get() = when (this) {
            BLUETOOTH_OFF -> RadioState.POWERED_OFF
            BLUETOOTH_DENIED -> RadioState.UNAUTHORIZED
            else -> RadioState.POWERED_ON
        }

    companion object {
        /** Where the app keeps the debug menu's choice. */
        const val PREFERENCE_KEY = "mockScenario"

        fun of(raw: String?): MockScenario? = entries.firstOrNull { it.rawValue == raw }
    }
}
