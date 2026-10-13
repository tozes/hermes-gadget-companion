import Foundation

/// The gadget's saved Wi-Fi networks, from `wifi list` (`@wifi`). The first
/// one is the network that last worked, and the gadget tries it first.
public struct WifiNetworkList: Decodable, Equatable, Sendable {
    public struct Network: Decodable, Equatable, Sendable, Identifiable {
        public let ssid: String
        /// The gadget has joined it with its saved password. A network saved
        /// with `wifi add` stays untested until the gadget first joins it.
        public let tested: Bool
        /// Saved without a password.
        public let open: Bool

        public var id: String { ssid }

        public init(ssid: String, tested: Bool, open: Bool) {
            self.ssid = ssid
            self.tested = tested
            self.open = open
        }
    }

    public let networks: [Network]
    /// How many networks the gadget keeps.
    public let max: Int
    /// The network the gadget is on now, if any.
    public let current: String?

    public init(networks: [Network], max: Int, current: String?) {
        self.networks = networks
        self.max = max
        self.current = current
    }

    public var isFull: Bool { networks.count >= max }

    public func contains(_ ssid: String) -> Bool {
        networks.contains { $0.ssid == ssid }
    }
}

/// Console arguments that may hold spaces and quotes (network names and
/// passwords), by the rules of the gadget's console (`hg/console_args.hpp`
/// in the SDK): double quotes group, a backslash keeps the next backslash,
/// quote or space.
public enum ConsoleArgument {
    public static func quoted(_ value: String) -> String {
        var out = "\""
        for character in value {
            if character == "\\" || character == "\"" { out.append("\\") }
            out.append(character)
        }
        return out + "\""
    }

    /// Splits a line the way the gadget does.
    static func split(_ line: String) -> [String] {
        var out: [String] = []
        var argument = ""
        var inArgument = false, quoted = false, escaped = false
        for character in line {
            if escaped {
                if character == "\\" || character == "\"" || character == " " { argument.append(character) }
                escaped = false
            } else if character == "\\" {
                escaped = true
                inArgument = true
            } else if quoted {
                if character == "\"" {
                    out.append(argument)
                    argument = ""
                    inArgument = false
                    quoted = false
                } else {
                    argument.append(character)
                }
            } else if character == " " {
                if inArgument { out.append(argument) }
                argument = ""
                inArgument = false
            } else if character == "\"" && !inArgument {
                inArgument = true
                quoted = true
            } else {
                argument.append(character)
                inArgument = true
            }
        }
        if inArgument { out.append(argument) }
        return out
    }
}
