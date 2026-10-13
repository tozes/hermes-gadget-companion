import Foundation

/// The JSON of `@status`. Every field is optional and unknown fields are
/// ignored, so older and newer firmware both decode.
struct GadgetStatus: Codable, Equatable, Sendable {
    var deviceId: String?
    var name: String?
    var board: String?
    var firmware: String?
    var phase: Phase?
    var screen: String?
    /// Approved by Hermes (not the phone pairing).
    var paired: Bool?
    /// Shown while the device waits for approval.
    var pairingCode: String?
    var server: String?
    var network: Bool?
    var displaySleeping: Bool?
    var power: Power?
    var phone: PhoneState?

    enum Phase: Equatable, Sendable, Codable {
        case boot, noNetwork, connecting, handshake, online
        case other(String)

        init(from decoder: Decoder) throws {
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

        func encode(to encoder: Encoder) throws {
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

    struct Power: Codable, Equatable, Sendable {
        var available: Bool?
        var batteryPresent: Bool?
        var batteryMv: Int?
        var batteryPercent: Int?
        var charging: Bool?
        var externalPower: Bool?
    }
}

/// The phone side of the gadget's Bluetooth: `@phone {...}` and `status.phone`.
struct PhoneState: Codable, Equatable, Sendable {
    /// How many phones are paired (up to three).
    var paired: Int?
    var connected: Bool?
    /// Whether Phone app pairing is open on the gadget.
    var pairing: Bool?
}

/// `@value {"key": ..., "value": ...}`. Secrets come back as `<set>`.
struct SettingValue: Codable, Equatable, Sendable {
    var key: String
    var value: String

    static let maskedSecret = "<set>"
}
