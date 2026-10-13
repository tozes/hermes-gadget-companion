import SwiftUI

/// The gadget's own connection, as its top bar shows it: a coloured link dot and a label.
enum HermesLink: Equatable {
    case noNetwork, network, connecting, online

    init(_ status: GadgetStatus?) {
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
    var label: String {
        switch self {
        case .noNetwork: "NO NET"
        case .network: "NET"
        case .connecting: "LINK"
        case .online: "ONLINE"
        }
    }

    var color: Color {
        switch self {
        case .noNetwork: Palette.red
        case .network, .connecting: Palette.yellow
        case .online: Palette.green
        }
    }

    var spoken: String {
        switch self {
        case .noNetwork: "No network"
        case .network: "On Wi-Fi"
        case .connecting: "Connecting to Hermes"
        case .online: "Online"
        }
    }
}

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

extension GadgetStatus.Power {
    /// "84%, charging, USB power", or nil when the board doesn't report a battery.
    var summary: String? {
        guard available != false, batteryPresent != false, let percent = batteryPercent else {
            return externalPower == true ? "USB power, no battery" : nil
        }
        var parts = ["\(percent)%"]
        if charging == true { parts.append("charging") }
        if externalPower == true { parts.append("USB power") } else { parts.append("on battery") }
        return parts.joined(separator: ", ")
    }

    /// The device turns its battery red at this level or below, without USB power.
    static let lowPercent = 10

    /// Low as the device shows it: 10% or less, without USB power.
    var isLow: Bool {
        (batteryPercent ?? 100) <= Self.lowPercent && externalPower != true
    }

    /// The level and charging state for a battery icon, when there is a battery.
    var gauge: (percent: Int, charging: Bool, low: Bool)? {
        guard available != false, batteryPresent != false, let percent = batteryPercent else { return nil }
        return (percent, charging == true, isLow)
    }

    var voltage: String? {
        batteryMv.map { String(format: "%.2f V", Double($0) / 1000) }
    }
}
