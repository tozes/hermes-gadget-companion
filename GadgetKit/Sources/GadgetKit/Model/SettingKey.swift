import Foundation

/// The gadget's setting keys (`get <key>` / `set <key> <value>`).
public enum SettingKey: String, CaseIterable, Sendable {
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

    public static let volumeRange = 0...100
    public static let brightnessRange = 5...100
    static let screenTimeoutRange = 0...3600
}

public enum TalkMode: String, CaseIterable, Sendable {
    case hold, tap

    public var title: String {
        switch self {
        case .hold: "Hold"
        case .tap: "Tap"
        }
    }

    public var explanation: String {
        switch self {
        case .hold: "Hold the screen while you talk."
        case .tap: "Tap once to start talking, again to stop."
        }
    }
}

public enum BatteryDisplay: String, CaseIterable, Sendable {
    case icon, percentage, both, off

    public var title: String {
        switch self {
        case .icon: "Icon"
        case .percentage: "%"
        case .both: "Both"
        case .off: "Off"
        }
    }

    public var spokenTitle: String {
        switch self {
        case .icon: "Icon"
        case .percentage: "Percentage"
        case .both: "Icon and percentage"
        case .off: "Off"
        }
    }
}
