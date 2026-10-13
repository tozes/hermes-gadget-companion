import Foundation

/// The gadget's setting keys (`get <key>` / `set <key> <value>`).
enum SettingKey: String, CaseIterable, Sendable {
    case name
    case server
    case token
    case talkMode = "talk_mode"
    case volume
    case brightness
    case screenTimeout = "screen_timeout"
    case batteryDisplay = "battery_display"
    case wifiSSID = "wifi_ssid"
    case wifiPassword = "wifi_pass"

    /// `get` masks these as `<set>`.
    var isSecret: Bool { self == .token || self == .wifiPassword }

    static let volumeRange = 0...100
    static let brightnessRange = 5...100
    static let screenTimeoutRange = 0...3600
}

enum TalkMode: String, CaseIterable, Sendable {
    case hold, tap

    var title: String {
        switch self {
        case .hold: "Hold"
        case .tap: "Tap"
        }
    }

    var explanation: String {
        switch self {
        case .hold: "Hold the screen while you talk."
        case .tap: "Tap once to start talking, again to stop."
        }
    }
}

enum BatteryDisplay: String, CaseIterable, Sendable {
    case icon, percentage, both, off

    var title: String {
        switch self {
        case .icon: "Icon"
        case .percentage: "%"
        case .both: "Both"
        case .off: "Off"
        }
    }

    var spokenTitle: String {
        switch self {
        case .icon: "Icon"
        case .percentage: "Percentage"
        case .both: "Icon and percentage"
        case .off: "Off"
        }
    }
}
