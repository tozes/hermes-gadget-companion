import Foundation

/// The JSON of `@status`. Every field is optional and unknown fields are
/// ignored, so older and newer firmware both decode.
public struct GadgetStatus: Codable, Equatable, Sendable {
    public var deviceId: String?
    public var name: String?
    public var board: String?
    public var firmware: String?
    public var phase: Phase?
    var screen: String?
    /// Approved by Hermes (not the phone pairing).
    public var paired: Bool?
    /// Shown while the device waits for approval.
    public var pairingCode: String?
    public var server: String?
    public var network: Bool?
    public var displaySleeping: Bool?
    public var power: Power?
    public var phone: PhoneState?

    public enum Phase: Equatable, Sendable, Codable {
        case boot, noNetwork, connecting, handshake, online
        case other(String)

        public init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            switch raw {
            case "boot": self = .boot
            case "no_network": self = .noNetwork
            case "connecting": self = .connecting
            case "handshake": self = .handshake
            case "online": self = .online
            default: self = .other(raw)
            }
        }

        public func encode(to encoder: Encoder) throws {
            var container = encoder.singleValueContainer()
            try container.encode(rawValue)
        }

        var rawValue: String {
            switch self {
            case .boot: "boot"
            case .noNetwork: "no_network"
            case .connecting: "connecting"
            case .handshake: "handshake"
            case .online: "online"
            case .other(let raw): raw
            }
        }
    }

    public struct Power: Codable, Equatable, Sendable {
        var available: Bool?
        var batteryPresent: Bool?
        var batteryMv: Int?
        var batteryPercent: Int?
        var charging: Bool?
        var externalPower: Bool?
    }
}

/// The phone side of the gadget's Bluetooth: `@phone {...}` and `status.phone`.
public struct PhoneState: Codable, Equatable, Sendable {
    /// How many phones are paired (up to three).
    public var paired: Int?
    var connected: Bool?
    /// Whether Phone app pairing is open on the gadget.
    var pairing: Bool?
}

/// `@value {"key": ..., "value": ...}`. Secrets come back as `<set>`.
public struct SettingValue: Codable, Equatable, Sendable {
    var key: String
    var value: String

    public static let maskedSecret = "<set>"
}
