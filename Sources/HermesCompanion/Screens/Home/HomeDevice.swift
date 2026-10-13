import SwiftUI

/// The round gadget at the top of the device home, showing what its own
/// screen shows for the current status (copy from the firmware's screens).
struct HomeDevice: View {
    let status: GadgetStatus?
    var talkMode: String?

    var body: some View {
        let link = HermesLink(status)
        let name = status?.name ?? "Gadget"
        switch status?.phase {
        case nil:
            DeviceScreen(dot: Palette.yellow, headline: name, lines: ["..."], spoken: "Reading the gadget's status")
        case .boot:
            DeviceScreen(dot: link.color, headline: "HERMES", headlineColor: Palette.accent, lines: ["gadget"],
                         spoken: "Gadget screen: starting up")
        case .noNetwork:
            DeviceScreen(dot: link.color, headline: "No Wi-Fi", headlineColor: Palette.red,
                         lines: ["offline"], hint: "Set up Wi-Fi",
                         spoken: "Gadget screen: offline, no Wi-Fi")
        case .connecting, .handshake:
            DeviceScreen(dot: link.color, headline: "Connecting", headlineColor: Palette.accent,
                         lines: ["to Hermes"], spoken: "Gadget screen: connecting to Hermes")
        case .online where status?.paired == false:
            let code = status?.pairingCode ?? "--------"
            DeviceScreen(dot: link.color, headline: "Code \(code)", headlineColor: Palette.accent,
                         lines: ["approve on host"], hint: "waiting",
                         spoken: "Gadget screen: pairing code \(code), waiting for approval")
        case .online:
            DeviceScreen(dot: link.color, headline: "Hi, I'm Hermes", lines: ["Ask me anything"],
                         hint: talkMode == "tap" ? "Tap to talk" : "Hold to talk",
                         spoken: "Gadget screen: Hi, I'm Hermes. Ask me anything.")
        case .other(let raw):
            DeviceScreen(dot: link.color, headline: raw, spoken: "Gadget screen: \(raw)")
        }
    }
}
