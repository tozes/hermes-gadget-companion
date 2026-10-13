import Foundation
import Testing
@testable import GadgetKit

/// The console behaviour the mock scenarios rely on, checked against the
/// firmware's replies (`App::console` and its tests in the SDK).
struct MockDeviceTests {
    let now = Date(timeIntervalSinceReferenceDate: 0)

    @Test func getReturnsTheValue() {
        var device = MockDevice.nox(.newGadget)
        #expect(device.handle("get volume", now: now) == #"@value {"key":"volume","value":"70"}"#)
        #expect(Reply.parse(device.handle("get name", now: now)) == .value(SettingValue(key: "name", value: "Nox")))
    }

    @Test func secretsAreMasked() {
        var kitchen = MockDevice.kitchen()
        #expect(kitchen.handle("get wifi_pass", now: now) == #"@value {"key":"wifi_pass","value":"<set>"}"#)
        #expect(kitchen.handle("get token", now: now) == #"@value {"key":"token","value":"<set>"}"#)
        // An empty secret reads back empty, not masked.
        var nox = MockDevice.nox(.newGadget)
        #expect(nox.handle("get wifi_pass", now: now) == #"@value {"key":"wifi_pass","value":""}"#)
    }

    @Test func setThenGet() {
        var device = MockDevice.nox(.online)
        #expect(device.handle("set volume 55", now: now) == "@ok volume")
        #expect(device.handle("get volume", now: now) == #"@value {"key":"volume","value":"55"}"#)
        #expect(device.handle("set name  Desk Lamp ", now: now) == "@ok name")
        #expect(device.handle("get name", now: now) == #"@value {"key":"name","value":"Desk Lamp"}"#)
    }

    @Test func valuesAreJSONEscaped() {
        var device = MockDevice.nox(.online)
        _ = device.handle(#"set name say "hi" \ bye"#, now: now)
        #expect(Reply.parse(device.handle("get name", now: now)) == .value(SettingValue(key: "name", value: #"say "hi" \ bye"#)))
    }

    @Test(arguments: ["get nonsense", "set nonsense 1", "get", "set"])
    func unknownKey(line: String) {
        var device = MockDevice.nox(.online)
        #expect(device.handle(line, now: now) == "@error unknown key")
    }

    @Test(arguments: ["-1", "3601", "12345", "00001", "abc", "1.5", "６０"])
    func screenTimeoutOutOfRange(value: String) {
        var device = MockDevice.nox(.online)
        #expect(device.handle("set screen_timeout \(value)", now: now) == "@error screen_timeout must be 0..3600 seconds")
        #expect(device.settings["screen_timeout"] == "60")
    }

    @Test(arguments: ["0", "30", "3600", "0030", ""])
    func screenTimeoutInRange(value: String) {
        var device = MockDevice.nox(.online)
        #expect(device.handle("set screen_timeout \(value)", now: now) == "@ok screen_timeout")
        #expect(device.settings["screen_timeout"] == value)
    }

    @Test func batteryDisplay() {
        var device = MockDevice.nox(.online)
        #expect(device.handle("set battery_display maybe", now: now) == "@error battery_display must be icon, percentage, both or off")
        #expect(device.settings["battery_display"] == "icon")
        for value in BatteryDisplay.allCases.map(\.rawValue) {
            #expect(device.handle("set battery_display \(value)", now: now) == "@ok battery_display")
        }
        #expect(device.settings["battery_display"] == "off")
    }

    @Test func phoneForget() {
        var device = MockDevice.nox(.online)
        device.phoneConnected = true
        #expect(device.handle("phone pair", now: now) == "@ok phone pairing open")
        #expect(device.handle("phone forget", now: now) == "@ok phone forget")
        #expect(Reply.parse(device.handle("phone", now: now)) == .phone(PhoneState(paired: 0, connected: false, pairing: false)))
        #expect(device.handle("phone bogus", now: now) == "@error usage: phone [pair [close] | forget]")
    }

    @Test func unknownCommand() {
        var device = MockDevice.nox(.online)
        #expect(device.handle("dance", now: now) == "@error unknown command (try: help)")
    }

    @Test func statusDecodes() {
        var device = MockDevice.nox(.newGadget)
        guard case .status(let status) = Reply.parse(device.handle("status", now: now)) else {
            Issue.record("status didn't decode")
            return
        }
        #expect(status.deviceId == "hg-5a1c9e2f7b3d4086")
        #expect(status.phase == .noNetwork)
        #expect(status.network == false)
        #expect(status.power?.batteryPercent == 84)
        #expect(status.pairingCode == nil)
    }

    @Test func awaitingApprovalShowsThePairingCode() {
        var device = MockDevice.nox(.awaitingApproval)
        guard case .status(let status) = Reply.parse(device.handle("status", now: now)) else {
            Issue.record("status didn't decode")
            return
        }
        #expect(status.phase == .online)
        #expect(status.paired == false)
        #expect(status.pairingCode == MockDevice.pairingCode)
        device.approve()
        guard case .status(let approved) = Reply.parse(device.handle("status", now: now)) else { return }
        #expect(approved.paired == true)
        #expect(approved.pairingCode == nil)
    }

    @Test func boardWithoutBattery() {
        var kitchen = MockDevice.kitchen()
        guard case .status(let status) = Reply.parse(kitchen.handle("status", now: now)) else {
            Issue.record("status didn't decode")
            return
        }
        #expect(status.power == GadgetStatus.Power(available: false))
    }

    /// Phase after `seconds` of a join started at `now`.
    private func phase(_ device: inout MockDevice, after seconds: TimeInterval) -> (network: Bool?, phase: GadgetStatus.Phase?) {
        guard case .status(let status) = Reply.parse(device.handle("status", now: now + seconds)) else { return (nil, nil) }
        return (status.network, status.phase)
    }

    @Test func wifiJoinComesOnline() {
        var device = MockDevice.nox(.newGadget)
        #expect(device.handle("set wifi_ssid Lisbon Loft", now: now) == "@ok wifi_ssid")
        #expect(phase(&device, after: 1) == (false, .noNetwork))
        #expect(phase(&device, after: 3) == (true, .connecting))
        #expect(phase(&device, after: 5) == (true, .handshake))
        #expect(phase(&device, after: 6) == (true, .online))
    }

    @Test func wifiThatFailsNeverJoins() {
        var device = MockDevice.nox(.wifiFails)
        _ = device.handle("set wifi_ssid Lisbon Loft", now: now)
        #expect(phase(&device, after: 60) == (false, .noNetwork))
    }

    @Test func hermesUnreachableStaysConnecting() {
        var device = MockDevice.nox(.hermesUnreachable)
        _ = device.handle("set wifi_ssid Lisbon Loft", now: now)
        #expect(phase(&device, after: 60) == (true, .connecting))
    }
}
