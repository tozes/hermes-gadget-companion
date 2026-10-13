package com.tozes.hermescompanion.core

/** A colour from the gadget's palette, named for its role; the app maps it to a colour. */
enum class Tone { TEXT, DIM, FAINT, ACCENT, ACCENT_INK, GREEN, RED, YELLOW }

/** The round indicator beside a headline, as the firmware draws it (`Ui::draw_indicator`). */
sealed interface Indicator {
    data class Spinner(val tone: Tone) : Indicator
    data class Ring(val tone: Tone) : Indicator
    data class Pulse(val tone: Tone) : Indicator
    data class Dot(val tone: Tone) : Indicator
    data object Alert : Indicator
    data object Question : Indicator
}

/** What the device home says about a status: the hero's headline, detail and indicator. */
data class StatusSummary(
    val indicator: Indicator,
    val headline: String,
    val headlineTone: Tone,
    val detail: String,
    val next: NextStep? = null,
) {
    sealed interface NextStep {
        data object SetUpWifi : NextStep
        data class ApproveOnHermes(val code: String?) : NextStep
    }

    companion object {
        fun of(status: GadgetStatus?): StatusSummary {
            val name = status?.name ?: "The gadget"
            if (status == null) {
                return StatusSummary(Indicator.Spinner(Tone.ACCENT), "Reading status", Tone.ACCENT, "Asking ${name.lowercase()} how it is.")
            }
            return when (val phase = status.phase) {
                GadgetStatus.Phase.Boot -> StatusSummary(Indicator.Spinner(Tone.ACCENT), "Starting up", Tone.ACCENT, "$name is booting.")
                GadgetStatus.Phase.NoNetwork, null -> StatusSummary(
                    Indicator.Alert, "No Wi-Fi", Tone.RED, "Set up Wi-Fi so $name can reach Hermes.", NextStep.SetUpWifi,
                )
                GadgetStatus.Phase.Connecting -> StatusSummary(
                    Indicator.Spinner(Tone.ACCENT), "Connecting", Tone.ACCENT, "On Wi-Fi. Reaching the Hermes server.",
                )
                GadgetStatus.Phase.Handshake -> StatusSummary(Indicator.Spinner(Tone.ACCENT), "Signing in", Tone.ACCENT, "Saying hello to Hermes.")
                GadgetStatus.Phase.Online -> if (status.paired == false) {
                    StatusSummary(
                        Indicator.Ring(Tone.ACCENT), "Waiting for approval", Tone.ACCENT, "Approve $name on the Hermes host.",
                        NextStep.ApproveOnHermes(status.pairingCode),
                    )
                } else {
                    StatusSummary(Indicator.Pulse(Tone.GREEN), "Online", Tone.TEXT, "Hermes is ready to talk.")
                }
                is GadgetStatus.Phase.Other -> StatusSummary(Indicator.Dot(Tone.DIM), phase.rawValue, Tone.TEXT, "$name reports phase \"${phase.rawValue}\".")
            }
        }

        /** The Bluetooth link dropped: the last status is stale, so it isn't shown. */
        fun connectionLost(name: String) = StatusSummary(Indicator.Alert, "Not connected", Tone.RED, "Reconnect to see how $name is doing.")
    }
}

/** The Wi-Fi row's one-line summary on the home screen. */
fun wifiSummary(state: GadgetState): String {
    val list = state.wifiNetworks
    if (list != null && list.networks.isNotEmpty()) {
        val saved = if (list.networks.size == 1) "1 network saved" else "${list.networks.size} networks saved"
        return list.current?.let { "On $it · $saved" } ?: "Not connected · $saved"
    }
    val ssid = state.settings[SettingKey.WIFI_SSID]
    if (!ssid.isNullOrEmpty()) {
        return if (state.status?.network == true) "On $ssid" else "$ssid, not connected"
    }
    return if (state.status?.network == true) "Connected" else "Not set up"
}

/** How the Advanced screen describes the gadget's link to Hermes. */
fun hermesDescription(status: GadgetStatus): String = when (val phase = status.phase) {
    GadgetStatus.Phase.Online -> if (status.paired == false) "Online, waiting for approval" else "Online and approved"
    GadgetStatus.Phase.Connecting -> "Connecting"
    GadgetStatus.Phase.Handshake -> "Signing in"
    GadgetStatus.Phase.Boot -> "Starting up"
    GadgetStatus.Phase.NoNetwork, null -> "Offline"
    is GadgetStatus.Phase.Other -> phase.rawValue
}

/** The battery line on the Advanced screen. */
fun batteryDescription(status: GadgetStatus): String {
    val power = status.power ?: return "Not reported by this board"
    val summary = power.summary ?: return "Not reported by this board"
    val voltage = power.voltage ?: return summary
    return "$summary ($voltage)"
}

/**
 * What the screen timeout setting means: "The screen sleeps after 1 minute,
 * 30 seconds without a touch.", or null for a value that isn't a number.
 */
fun screenTimeoutDescription(saved: String?): String? {
    val seconds = saved?.toIntOrNull() ?: return null
    if (seconds == 0) return "The screen stays on."
    val minutes = seconds / 60
    val rest = seconds % 60
    val parts = listOfNotNull(
        minutes.takeIf { it > 0 }?.let { if (it == 1) "1 minute" else "$it minutes" },
        rest.takeIf { it > 0 }?.let { if (it == 1) "1 second" else "$it seconds" },
    )
    return "The screen sleeps after ${parts.joinToString(", ")} without a touch."
}

/**
 * [value] with a zero-width space after each of [breaks], so a long value
 * (a server address) wraps there first rather than inside its port number.
 */
fun wrappable(value: String, breaks: Set<Char>): String = buildString {
    for (ch in value) {
        append(ch)
        if (ch in breaks) append('​')
    }
}
