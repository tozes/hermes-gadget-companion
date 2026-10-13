package com.tozes.hermescompanion.core

import kotlin.time.Duration

/**
 * A simulated gadget console: settings, status and the firmware's replies
 * (`App::console` in the SDK), including its validation messages. Times are
 * [Duration]s since any fixed origin.
 */
class MockDevice(
    val settings: MutableMap<String, String>,
    val deviceId: String,
    val board: String,
) {
    var firmware = "0.2.0"
    var phase = "no_network"
    var screen = "offline"
    var approved = false
    var network = false
    var displaySleeping = false
    var hasBattery = true
    var batteryMv = 4126
    var batteryPercent = 84
    var charging = true
    var externalPower = true
    var phonesPaired = 1
    var phonePairing = false
    var phoneConnected = false

    /** Whether joining Wi-Fi brings the network up, and whether Hermes then answers. */
    var wifiWorks = true
    var hermesWorks = true
    private var joinStarted: Duration? = null

    /**
     * A saved Wi-Fi network. The first is mirrored into `wifi_ssid` /
     * `wifi_pass`, as the firmware does.
     */
    data class SavedNetwork(var ssid: String, var password: String, var tested: Boolean)

    /** The saved Wi-Fi networks, first one tried first. */
    val networks = mutableListOf<SavedNetwork>()

    /** The network the gadget is on (or last joined) while [network] is up. */
    private var joinedSsid: String? = null
    private var joinedPassword: String? = null

    fun goOnline(approved: Boolean) {
        network = true
        phase = "online"
        this.approved = approved
        screen = if (approved) "ready" else "pairing"
        if (settings["wifi_ssid"].orEmpty().isEmpty()) {
            settings["wifi_ssid"] = "Lisbon Loft"
            settings["wifi_pass"] = "correct horse"
        }
        if (networks.isEmpty()) {
            networks += SavedNetwork(settings["wifi_ssid"].orEmpty(), settings["wifi_pass"].orEmpty(), tested = true)
        }
        joinedSsid = networks.firstOrNull()?.ssid
        joinedPassword = networks.firstOrNull()?.password
        if (settings["token"].orEmpty().isEmpty() && approved) settings["token"] = "tok"
    }

    /** Hermes approves the gadget (the debug menu's "Approve on Hermes"). */
    fun approve() {
        approved = true
        if (phase == "online") screen = "ready"
    }

    /** Advances a Wi-Fi join started by `set wifi_ssid` / `set wifi_pass`. */
    fun tick(now: Duration) {
        val started = joinStarted ?: return
        val elapsed = (now - started).inWholeMilliseconds / 1000.0
        if (!wifiWorks || settings["wifi_ssid"].orEmpty().isEmpty()) return
        if (elapsed >= 3 && !network) {
            network = true
            phase = "connecting"
            screen = "connecting"
            joinedSsid = settings["wifi_ssid"]
            joinedPassword = settings["wifi_pass"]
            if (networks.isNotEmpty()) networks[0].tested = true
        }
        if (!hermesWorks) return
        if (elapsed >= 4.5) phase = "handshake"
        if (elapsed >= 6) {
            phase = "online"
            screen = if (approved) "ready" else "pairing"
            joinStarted = null
        }
    }

    fun handle(raw: String, now: Duration): String {
        tick(now)
        val line = raw.trimSpaces()
        val space = line.indexOf(' ').let { if (it < 0) line.length else it }
        val command = line.substring(0, space)
        val rest = line.substring(space).trimSpaces()

        return when (command) {
            "help" -> "@help commands: status | diag [log] | get <key> | set <key> <value> | say <text> | talk | release | " +
                "cancel | new-session | settings [close] | wifi-setup [close] | wifi [list | add <ssid> [password] | join <ssid> | " +
                "forget <ssid>] | phone [pair [close] | forget] | yes | no | " +
                "reconnect | forget-key | factory-reset   keys: name server token talk_mode volume brightness " +
                "screen_timeout battery_display wifi_ssid wifi_pass"
            "status" -> "@status " + statusJson()
            "phone" -> when (rest) {
                "" -> "@phone " + phoneJson()
                "pair" -> {
                    phonePairing = true
                    "@ok phone pairing open"
                }
                "pair close" -> {
                    phonePairing = false
                    "@ok phone pairing closed"
                }
                "forget" -> {
                    phonePairing = false
                    phonesPaired = 0
                    phoneConnected = false
                    "@ok phone forget"
                }
                else -> "@error usage: phone [pair [close] | forget]"
            }
            "get", "set" -> setting(command, rest, now)
            "wifi" -> wifi(rest, now)
            "diag" -> if (rest == "log") {
                "I (1203) hg: boot\nI (1450) hg: phone app ready\nI (2210) hg: phase $phase\n@log end"
            } else {
                "@diag {\"build\":\"$firmware\",\"reset_reason\":\"poweron\",\"heap_free\":182344," +
                    "\"psram_free\":7864320,\"wifi\":{\"connected\":$network,\"rssi\":${if (network) -58 else 0}}}"
            }
            "say" -> "@ok say"
            "talk", "release", "cancel" -> "@ok $command"
            "new-session" -> if (phase == "online" && approved) "@ok new-session" else "@error not connected"
            "reconnect" -> "@ok reconnect"
            "settings" -> if (rest == "close") "@ok settings closed" else "@ok settings"
            "wifi-setup" -> if (rest == "close") {
                "@ok Wi-Fi setup closed"
            } else {
                "@ok Join Hermes-Setup-4086 (password hg-setup-91), then open http://192.168.4.1"
            }
            "yes", "no" -> "@error no question to answer"
            "forget-key", "factory-reset" -> "@ok $command (restart the device to apply)"
            else -> "@error unknown command (try: help)"
        }
    }

    private fun setting(command: String, rest: String, now: Duration): String {
        val space = rest.indexOf(' ').let { if (it < 0) rest.length else it }
        val key = rest.substring(0, space)
        val settingKey = SettingKey.of(key) ?: return "@error unknown key"
        if (command == "get") {
            val value = settings[key].orEmpty()
            val shown = if (settingKey.isSecret && value.isNotEmpty()) SettingValue.MASKED_SECRET else value
            return "@value {\"key\":${json(key)},\"value\":${json(shown)}}"
        }
        val value = rest.substring(space).trimSpaces()
        if (key == "screen_timeout" && value.isNotEmpty() &&
            (value.length > 4 || value.any { it !in '0'..'9' } || (value.toIntOrNull() ?: 0) > 3600)
        ) {
            return "@error screen_timeout must be 0..3600 seconds"
        }
        if (key == "battery_display" && value.isNotEmpty() && value !in listOf("icon", "percentage", "both", "off")) {
            return "@error battery_display must be icon, percentage, both or off"
        }
        when (key) {
            "wifi_ssid" -> {
                // The first saved network: a new name goes first and keeps the others.
                if (value.isEmpty()) {
                    if (networks.isNotEmpty()) networks.removeAt(0)
                } else {
                    val password = settings["wifi_pass"].orEmpty()
                    val same = networks.firstOrNull { it.ssid == value && it.password == password }
                    putFirst(SavedNetwork(value, password, tested = same?.tested ?: false))
                }
                mirror()
                rejoinIfNeeded(now)
            }
            "wifi_pass" -> {
                if (networks.isEmpty()) {
                    settings[key] = value
                } else if (networks[0].password != value) {
                    networks[0].password = value
                    networks[0].tested = false
                }
                mirror()
                rejoinIfNeeded(now)
            }
            else -> settings[key] = value
        }
        return "@ok $key"
    }

    /** `wifi [list | add <ssid> [password] | join <ssid> | forget <ssid>]` (`App::wifi_command`). */
    private fun wifi(rest: String, now: Duration): String {
        val usage = "@error usage: wifi [list | add <ssid> [password] | join <ssid> | forget <ssid>]"
        val args = ConsoleArgument.split(rest)
        val sub = args.firstOrNull() ?: "list"
        if (sub == "list") {
            return if (args.size > 1) usage else "@wifi " + wifiJson()
        }
        if (args.size < 2 || args.size > (if (sub == "add") 3 else 2)) return usage
        val ssid = args[1]
        val saved = networks.indexOfFirst { it.ssid == ssid }.takeIf { it >= 0 }
        when (sub) {
            "add" -> {
                val password = if (args.size > 2) args[2] else ""
                networkError(ssid, password)?.let { return "@error $it" }
                if (saved != null) {
                    if (networks[saved].password != password) {
                        networks[saved].password = password
                        networks[saved].tested = false
                    }
                } else {
                    if (networks.size >= MAX_NETWORKS) return "@error $MAX_NETWORKS networks saved; forget one first"
                    networks += SavedNetwork(ssid, password, tested = false)
                }
            }
            "join", "forget" -> {
                if (saved == null) return "@error no saved network $ssid"
                val network = networks.removeAt(saved)
                if (sub == "join") networks.add(0, network)
            }
            else -> return usage
        }
        mirror()
        rejoinIfNeeded(now)
        return "@ok wifi $sub"
    }

    private fun putFirst(saved: SavedNetwork) {
        networks.removeAll { it.ssid == saved.ssid }
        if (networks.size >= MAX_NETWORKS) networks.removeAt(networks.lastIndex)
        networks.add(0, saved)
    }

    private fun mirror() {
        settings["wifi_ssid"] = networks.firstOrNull()?.ssid.orEmpty()
        settings["wifi_pass"] = networks.firstOrNull()?.password.orEmpty()
    }

    /**
     * Like the firmware: stays on the network it's on if that is still first,
     * else leaves it and joins the first saved network.
     */
    private fun rejoinIfNeeded(now: Duration) {
        val first = networks.firstOrNull()
        if (network && first != null && first.ssid == joinedSsid && first.password == joinedPassword) return
        network = false
        phase = "no_network"
        screen = "offline"
        joinedSsid = null
        joinedPassword = null
        joinStarted = if (networks.isEmpty()) null else now
    }

    fun wifiJson(): String {
        val list = networks.joinToString(",") {
            "{\"ssid\":${json(it.ssid)},\"tested\":${it.tested},\"open\":${it.password.isEmpty()}}"
        }
        var out = "{\"networks\":[$list],\"max\":$MAX_NETWORKS"
        val joined = joinedSsid
        if (network && joined != null) out += ",\"current\":" + json(joined)
        return "$out}"
    }

    fun statusJson(): String {
        val fields = mutableListOf(
            "\"device_id\":${json(deviceId)}",
            "\"name\":${json(settings["name"].orEmpty())}",
            "\"board\":${json(board)}",
            "\"firmware\":${json(firmware)}",
            "\"phase\":${json(phase)}",
            "\"screen\":${json(screen)}",
        )
        if (screen == "pairing") fields += "\"pairing_code\":${json(PAIRING_CODE)}"
        fields += listOf(
            "\"paired\":$approved",
            "\"server\":${json(settings["server"].orEmpty())}",
            "\"network\":$network",
            "\"display_sleeping\":$displaySleeping",
        )
        fields += if (hasBattery) {
            "\"power\":{\"available\":true,\"battery_present\":true,\"battery_mv\":$batteryMv," +
                "\"battery_percent\":$batteryPercent,\"charging\":$charging,\"external_power\":$externalPower}"
        } else {
            "\"power\":{\"available\":false}"
        }
        fields += "\"phone\":" + phoneJson()
        return "{" + fields.joinToString(",") + "}"
    }

    private fun phoneJson(): String = "{\"paired\":$phonesPaired,\"connected\":$phoneConnected,\"pairing\":$phonePairing}"

    companion object {
        const val MAX_NETWORKS = 5
        const val PAIRING_CODE = "YGJEQNXU"

        /** Nox: the device behind the real status sample in the design brief. */
        fun nox(scenario: MockScenario): MockDevice {
            val device = MockDevice(
                settings = mutableMapOf(
                    "name" to "Nox", "server" to "wss://hermes.example.com:8765/gadget", "token" to "",
                    "talk_mode" to "hold", "volume" to "70", "brightness" to "80", "screen_timeout" to "60",
                    "battery_display" to "icon", "wifi_ssid" to "", "wifi_pass" to "",
                ),
                deviceId = "hg-5a1c9e2f7b3d4086",
                board = "esp32s3-touch-amoled-1.75c",
            )
            when (scenario) {
                MockScenario.DEMO -> {
                    device.goOnline(approved = true)
                    device.batteryMv = 3962
                    device.batteryPercent = 76
                    device.charging = false
                    device.externalPower = false
                    device.networks += SavedNetwork("Pocket Hotspot", "hotspot-pass", tested = true)
                }
                MockScenario.ONLINE -> {
                    device.goOnline(approved = true)
                    device.networks += SavedNetwork("Pocket Hotspot", "hotspot-pass", tested = false)
                }
                MockScenario.FLAKY -> device.goOnline(approved = true)
                MockScenario.AWAITING_APPROVAL -> device.goOnline(approved = false)
                MockScenario.LOW_BATTERY -> {
                    device.goOnline(approved = true)
                    device.batteryMv = 3520
                    device.batteryPercent = 9
                    device.charging = false
                    device.externalPower = false
                }
                MockScenario.WIFI_FAILS -> device.wifiWorks = false
                MockScenario.HERMES_UNREACHABLE -> device.hermesWorks = false
                else -> Unit
            }
            return device
        }

        /** A second gadget nearby: a BOX-3 without a battery, already online. */
        fun kitchen(): MockDevice {
            val device = MockDevice(
                settings = mutableMapOf(
                    "name" to "Kitchen", "server" to "wss://hermes.example.com:8765/gadget", "token" to "tok",
                    "talk_mode" to "tap", "volume" to "55", "brightness" to "60", "screen_timeout" to "0",
                    "battery_display" to "off", "wifi_ssid" to "Lisbon Loft", "wifi_pass" to "secret",
                ),
                deviceId = "hg-0c41aa17d2e94b10",
                board = "esp32-s3-box-3",
            )
            device.hasBattery = false
            device.goOnline(approved = true)
            return device
        }

        /** The firmware's rules for a network it saves (`wifi_network_error`). */
        fun networkError(ssid: String, password: String): String? {
            if (ssid.isEmpty() || ssid.encodeToByteArray().size > 32 || ssid.contains('\u0000')) {
                return "Network name must contain 1..32 bytes"
            }
            val hexKey = password.length == 64 && password.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            val printable = password.all { it in ' '..'~' }
            if (!printable || (password.isNotEmpty() && !hexKey && password.length !in 8..63)) {
                return "Use an 8..63 character Wi-Fi password, a 64-digit hex key, or leave it empty for an open network"
            }
            return null
        }

        private fun json(string: String): String = buildString {
            append('"')
            for (ch in string) {
                when {
                    ch == '"' -> append("\\\"")
                    ch == '\\' -> append("\\\\")
                    ch == '\n' -> append("\\n")
                    ch.code < 0x20 -> append(String.format(java.util.Locale.ROOT, "\\u%04x", ch.code))
                    else -> append(ch)
                }
            }
            append('"')
        }
    }
}
