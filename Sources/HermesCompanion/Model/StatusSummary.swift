import GadgetKit
import SwiftUI

/// What the device home says about a status: the hero's headline, detail and indicator.
struct StatusSummary {
    enum NextStep {
        case setUpWifi
        case approveOnHermes(code: String?)
    }

    let indicator: StatusIndicator.Kind
    let headline: String
    let headlineColor: Color
    let detail: String
    let next: NextStep?

    init(_ status: GadgetStatus?) {
        let name = status?.name ?? "The gadget"
        guard let status else {
            self.init(.spinner(Palette.accent), "Reading status", Palette.accent, "Asking \(name.lowercased()) how it is.")
            return
        }
        switch status.phase {
        case .boot:
            self.init(.spinner(Palette.accent), "Starting up", Palette.accent, "\(name) is booting.")
        case .noNetwork, nil:
            self.init(.alert, "No Wi-Fi", Palette.red, "Set up Wi-Fi so \(name) can reach Hermes.", next: .setUpWifi)
        case .connecting:
            self.init(.spinner(Palette.accent), "Connecting", Palette.accent, "On Wi-Fi. Reaching the Hermes server.")
        case .handshake:
            self.init(.spinner(Palette.accent), "Signing in", Palette.accent, "Saying hello to Hermes.")
        case .online where status.paired == false:
            self.init(
                .ring(Palette.accent), "Waiting for approval", Palette.accent,
                "Approve \(name) on the Hermes host.", next: .approveOnHermes(code: status.pairingCode)
            )
        case .online:
            self.init(.pulse(Palette.green), "Online", Palette.text, "Hermes is ready to talk.")
        case .other(let raw):
            self.init(.dot(Palette.dim), raw, Palette.text, "\(name) reports phase \"\(raw)\".")
        }
    }

    /// The Bluetooth link dropped: the last status is stale, so it isn't shown.
    static func connectionLost(name: String) -> StatusSummary {
        StatusSummary(.alert, "Not connected", Palette.red, "Reconnect to see how \(name) is doing.")
    }

    private init(
        _ indicator: StatusIndicator.Kind, _ headline: String, _ color: Color, _ detail: String,
        next: NextStep? = nil
    ) {
        self.indicator = indicator
        self.headline = headline
        self.headlineColor = color
        self.detail = detail
        self.next = next
    }
}
