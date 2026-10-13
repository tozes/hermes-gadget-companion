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

    /// The saved Wi-Fi networks, first one tried first. The first is mirrored
    /// into `wifi_ssid` / `wifi_pass`, as the firmware does.
    struct SavedNetwork: Equatable {
        var ssid: String
        var password: String
        var tested: Bool
    }
    var networks: [SavedNetwork] = []
    /// The network the gadget is on (or last joined) while `network` is up.
    private var joinedSSID: String?
    private var joinedPassword: String?
    static let maxNetworks = 5

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
        case .demo:
            device.goOnline(approved: true)
            device.batteryMv = 3962
            device.batteryPercent = 76
            device.charging = false
            device.externalPower = false
            device.networks.append(SavedNetwork(ssid: "Pocket Hotspot", password: "hotspot-pass", tested: true))
        case .online:
            device.goOnline(approved: true)
            device.networks.append(SavedNetwork(ssid: "Pocket Hotspot", password: "hotspot-pass", tested: false))
        case .flaky:
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
        if networks.isEmpty {
            networks = [SavedNetwork(ssid: settings["wifi_ssid", default: ""], password: settings["wifi_pass", default: ""], tested: true)]
        }
        joinedSSID = networks.first?.ssid
        joinedPassword = networks.first?.password
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
        if elapsed >= 3, !network {
            network = true
            phase = "connecting"
            screen = "connecting"
            joinedSSID = settings["wifi_ssid"]
            joinedPassword = settings["wifi_pass"]
            if !networks.isEmpty { networks[0].tested = true }
        }
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
                + "cancel | new-session | settings [close] | wifi-setup [close] | wifi [list | add <ssid> [password] | join <ssid> | "
                + "forget <ssid>] | phone [pair [close] | forget] | yes | no | "
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
        case "wifi":
            return wifi(rest, now: now)
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
        switch key {
        case "wifi_ssid":
            // The first saved network: a new name goes first and keeps the others.
            if value.isEmpty {
                if !networks.isEmpty { networks.removeFirst() }
            } else {
                let password = settings["wifi_pass", default: ""]
                let same = networks.first { $0.ssid == value && $0.password == password }
                putFirst(SavedNetwork(ssid: value, password: password, tested: same?.tested ?? false))
            }
            mirror()
            rejoinIfNeeded(now: now)
        case "wifi_pass":
            if networks.isEmpty {
                settings[key] = value
            } else if networks[0].password != value {
                networks[0].password = value
                networks[0].tested = false
            }
            mirror()
            rejoinIfNeeded(now: now)
        default:
            settings[key] = value
        }
        return "@ok \(key)"
    }

    /// `wifi [list | add <ssid> [password] | join <ssid> | forget <ssid>]` (`App::wifi_command`).
    private mutating func wifi(_ rest: String, now: Date) -> String {
        let usage = "@error usage: wifi [list | add <ssid> [password] | join <ssid> | forget <ssid>]"
        let args = ConsoleArgument.split(rest)
        let sub = args.first ?? "list"
        if sub == "list" {
            return args.count > 1 ? usage : "@wifi " + wifiJSON()
        }
        guard args.count >= 2, args.count <= (sub == "add" ? 3 : 2) else { return usage }
        let ssid = args[1]
        let saved = networks.firstIndex { $0.ssid == ssid }
        switch sub {
        case "add":
            let password = args.count > 2 ? args[2] : ""
            if let error = Self.networkError(ssid, password) { return "@error " + error }
            if let saved {
                if networks[saved].password != password {
                    networks[saved].password = password
                    networks[saved].tested = false
                }
            } else {
                guard networks.count < Self.maxNetworks else { return "@error \(Self.maxNetworks) networks saved; forget one first" }
                networks.append(SavedNetwork(ssid: ssid, password: password, tested: false))
            }
        case "join", "forget":
            guard let saved else { return "@error no saved network \(ssid)" }
            let network = networks.remove(at: saved)
            if sub == "join" { networks.insert(network, at: 0) }
        default:
            return usage
        }
        mirror()
        rejoinIfNeeded(now: now)
        return "@ok wifi \(sub)"
    }

    /// The firmware's rules for a network it saves (`wifi_network_error`).
    static func networkError(_ ssid: String, _ password: String) -> String? {
        if ssid.isEmpty || ssid.utf8.count > 32 || ssid.contains("\0") { return "Network name must contain 1..32 bytes" }
        let hexKey = password.utf8.count == 64 && password.allSatisfy(\.isHexDigit)
        let printable = password.unicodeScalars.allSatisfy { $0.value >= 0x20 && $0.value <= 0x7e }
        if !printable || (!password.isEmpty && !hexKey && !(8...63).contains(password.utf8.count)) {
            return "Use an 8..63 character Wi-Fi password, a 64-digit hex key, or leave it empty for an open network"
        }
        return nil
    }

    private mutating func putFirst(_ saved: SavedNetwork) {
        networks.removeAll { $0.ssid == saved.ssid }
        if networks.count >= Self.maxNetworks { networks.removeLast() }
        networks.insert(saved, at: 0)
    }

    private mutating func mirror() {
        settings["wifi_ssid"] = networks.first?.ssid ?? ""
        settings["wifi_pass"] = networks.first?.password ?? ""
    }

    /// Like the firmware: stays on the network it's on if that is still first,
    /// else leaves it and joins the first saved network.
    private mutating func rejoinIfNeeded(now: Date) {
        if network, let first = networks.first, first.ssid == joinedSSID, first.password == joinedPassword {
            return
        }
        network = false
        phase = "no_network"
        screen = "offline"
        joinedSSID = nil
        joinedPassword = nil
        joinStarted = networks.isEmpty ? nil : now
    }

    func wifiJSON() -> String {
        let list = networks.map {
            "{\"ssid\":\(Self.json($0.ssid)),\"tested\":\($0.tested),\"open\":\($0.password.isEmpty)}"
        }
        var out = "{\"networks\":[" + list.joined(separator: ",") + "],\"max\":\(Self.maxNetworks)"
        if network, let joinedSSID { out += ",\"current\":" + Self.json(joinedSSID) }
        return out + "}"
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
