import Foundation

/// The gadget's own connection, as its top bar shows it: a coloured link dot and a label.
public enum HermesLink: Equatable {
    case noNetwork, network, connecting, online

    public init(_ status: GadgetStatus?) {
        guard let status, status.network == true else {
            self = .noNetwork
            return
        }
        switch status.phase {
        case .online: self = .online
        case .connecting, .handshake: self = .connecting
        default: self = .network
        }
    }

    /// The firmware's top-bar label (`Ui::draw_top`).
    public var label: String {
        switch self {
        case .noNetwork: "NO NET"
        case .network: "NET"
        case .connecting: "LINK"
        case .online: "ONLINE"
        }
    }

    public var spoken: String {
        switch self {
        case .noNetwork: "No network"
        case .network: "On Wi-Fi"
        case .connecting: "Connecting to Hermes"
        case .online: "Online"
        }
    }
}
