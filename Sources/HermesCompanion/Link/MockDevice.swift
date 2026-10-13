import Foundation

/// A simulated gadget console: settings, status and the firmware's replies
/// (`App::console` in the SDK), including its validation messages.
struct MockDevice {
    var settings: [String: String]
    var deviceId: String
    var board: String
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

    /// Whether joining Wi-Fi brings the network up, and whether Hermes then answers.
    var wifiWorks = true
    var hermesWorks = true
    private var joinStarted: Date?

    static let pairingCode = "YGJEQNXU"

    /// Nox: the device behind the real status sample in the design brief.
    static func nox(_ scenario: MockScenario) -> MockDevice {
        var device = MockDevice(
            settings: [
                "name": "Nox", "server": "wss://hermes.example.com:8765/gadget", "token": "",
                "talk_mode": "hold", "volume": "70", "brightness": "80", "screen_timeout": "60",
                "battery_display": "icon", "wifi_ssid": "", "wifi_pass": "",
            ],
            deviceId: "hg-5a1c9e2f7b3d4086",
            board: "esp32s3-touch-amoled-1.75c"
        )
        switch scenario {
        case .online, .flaky:
            device.goOnline(approved: true)
        case .awaitingApproval:
            device.goOnline(approved: false)
        case .lowBattery:
            device.goOnline(approved: true)
            device.batteryMv = 3520
            device.batteryPercent = 9
            device.charging = false
            device.externalPower = false
        case .wifiFails:
            device.wifiWorks = false
        case .hermesUnreachable:
            device.hermesWorks = false
        default:
            break
        }
        return device
    }

    /// A second gadget nearby: a BOX-3 without a battery, already online.
    static func kitchen() -> MockDevice {
        var device = MockDevice(
            settings: [
                "name": "Kitchen", "server": "wss://hermes.example.com:8765/gadget", "token": "tok",
                "talk_mode": "tap", "volume": "55", "brightness": "60", "screen_timeout": "0",
                "battery_display": "off", "wifi_ssid": "Lisbon Loft", "wifi_pass": "secret",
            ],
            deviceId: "hg-0c41aa17d2e94b10",
            board: "esp32-s3-box-3"
        )
        device.hasBattery = false
        device.goOnline(approved: true)
        return device
    }

    mutating func goOnline(approved: Bool) {
        network = true
        phase = "online"
        self.approved = approved
        screen = approved ? "ready" : "pairing"
        if settings["wifi_ssid", default: ""].isEmpty {
            settings["wifi_ssid"] = "Lisbon Loft"
            settings["wifi_pass"] = "correct horse"
        }
        if settings["token", default: ""].isEmpty, approved { settings["token"] = "tok" }
    }

    /// Hermes approves the gadget (the debug menu's "Approve on Hermes").
    mutating func approve() {
        approved = true
        if phase == "online" { screen = "ready" }
    }

    /// Advances a Wi-Fi join started by `set wifi_ssid` / `set wifi_pass`.
    mutating func tick(now: Date) {
        guard let joinStarted else { return }
        let elapsed = now.timeIntervalSince(joinStarted)
        guard wifiWorks, !settings["wifi_ssid", default: ""].isEmpty else { return }
        if elapsed >= 3 { network = true; phase = "connecting"; screen = "connecting" }
        guard hermesWorks else { return }
        if elapsed >= 4.5 { phase = "handshake" }
        if elapsed >= 6 {
            phase = "online"
            screen = approved ? "ready" : "pairing"
            self.joinStarted = nil
        }
    }

    mutating func handle(_ raw: String, now: Date) -> String {
        tick(now: now)
        let line = raw.trimmingCharacters(in: .whitespaces)
        let space = line.firstIndex(of: " ") ?? line.endIndex
        let command = String(line[..<space])
        let rest = line[space...].trimmingCharacters(in: .whitespaces)

        switch command {
        case "help":
            return "@help commands: status | diag [log] | get <key> | set <key> <value> | say <text> | talk | release | "
                + "cancel | new-session | settings [close] | wifi-setup [close] | phone [pair [close] | forget] | yes | no | "
                + "reconnect | forget-key | factory-reset   keys: name server token talk_mode volume brightness "
                + "screen_timeout battery_display wifi_ssid wifi_pass"
        case "status":
            return "@status " + statusJSON()
        case "phone":
            switch rest {
            case "": return "@phone " + phoneJSON()
            case "pair": phonePairing = true; return "@ok phone pairing open"
            case "pair close": phonePairing = false; return "@ok phone pairing closed"
            case "forget": phonePairing = false; phonesPaired = 0; phoneConnected = false; return "@ok phone forget"
            default: return "@error usage: phone [pair [close] | forget]"
            }
        case "get", "set":
            return setting(command, rest, now: now)
        case "diag":
            if rest == "log" {
                return "I (1203) hg: boot\nI (1450) hg: phone app ready\nI (2210) hg: phase \(phase)\n@log end"
            }
            return "@diag {\"build\":\"\(firmware)\",\"reset_reason\":\"poweron\",\"heap_free\":182344,"
                + "\"psram_free\":7864320,\"wifi\":{\"connected\":\(network),\"rssi\":\(network ? -58 : 0)}}"
        case "say": return "@ok say"
        case "talk", "release", "cancel": return "@ok \(command)"
        case "new-session": return phase == "online" && approved ? "@ok new-session" : "@error not connected"
        case "reconnect": return "@ok reconnect"
        case "settings": return rest == "close" ? "@ok settings closed" : "@ok settings"
        case "wifi-setup":
            return rest == "close" ? "@ok Wi-Fi setup closed" : "@ok Join Hermes-Setup-4086 (password hg-setup-91), then open http://192.168.4.1"
        case "yes", "no": return "@error no question to answer"
        case "forget-key", "factory-reset": return "@ok \(command) (restart the device to apply)"
        default: return "@error unknown command (try: help)"
        }
    }

    private mutating func setting(_ command: String, _ rest: String, now: Date) -> String {
        let space = rest.firstIndex(of: " ") ?? rest.endIndex
        let key = String(rest[..<space])
        guard SettingKey(rawValue: key) != nil else { return "@error unknown key" }
        if command == "get" {
            let value = settings[key, default: ""]
            let shown = SettingKey(rawValue: key)!.isSecret && !value.isEmpty ? SettingValue.maskedSecret : value
            return "@value {\"key\":\(Self.json(key)),\"value\":\(Self.json(shown))}"
        }
        let value = rest[space...].trimmingCharacters(in: .whitespaces)
        if key == "screen_timeout", !value.isEmpty,
           value.count > 4 || value.contains(where: { !$0.isASCII || !$0.isNumber }) || (Int(value) ?? 0) > 3600 {
            return "@error screen_timeout must be 0..3600 seconds"
        }
        if key == "battery_display", !value.isEmpty, !["icon", "percentage", "both", "off"].contains(value) {
            return "@error battery_display must be icon, percentage, both or off"
        }
        settings[key] = value
        if key == "wifi_ssid" || key == "wifi_pass" {
            network = false
            phase = "no_network"
            screen = "offline"
            joinStarted = now
        }
        return "@ok \(key)"
    }

    func statusJSON() -> String {
        var fields = [
            "\"device_id\":\(Self.json(deviceId))",
            "\"name\":\(Self.json(settings["name", default: ""]))",
            "\"board\":\(Self.json(board))",
            "\"firmware\":\(Self.json(firmware))",
            "\"phase\":\(Self.json(phase))",
            "\"screen\":\(Self.json(screen))",
        ]
        if screen == "pairing" { fields.append("\"pairing_code\":\(Self.json(Self.pairingCode))") }
        fields += [
            "\"paired\":\(approved)",
            "\"server\":\(Self.json(settings["server", default: ""]))",
            "\"network\":\(network)",
            "\"display_sleeping\":\(displaySleeping)",
        ]
        if hasBattery {
            fields.append(
                "\"power\":{\"available\":true,\"battery_present\":true,\"battery_mv\":\(batteryMv),"
                    + "\"battery_percent\":\(batteryPercent),\"charging\":\(charging),\"external_power\":\(externalPower)}"
            )
        } else {
            fields.append("\"power\":{\"available\":false}")
        }
        fields.append("\"phone\":" + phoneJSON())
        return "{" + fields.joined(separator: ",") + "}"
    }

    private func phoneJSON() -> String {
        "{\"paired\":\(phonesPaired),\"connected\":\(phoneConnected),\"pairing\":\(phonePairing)}"
    }

    private static func json(_ string: String) -> String {
        var out = "\""
        for scalar in string.unicodeScalars {
            switch scalar {
            case "\"": out += "\\\""
            case "\\": out += "\\\\"
            case "\n": out += "\\n"
            case _ where scalar.value < 0x20: out += String(format: "\\u%04x", scalar.value)
            default: out.unicodeScalars.append(scalar)
            }
        }
        return out + "\""
    }
}
