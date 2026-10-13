import GadgetKit
import SwiftUI

/// The header while a Wi-Fi join or save runs or has finished. Each screen
/// draws its own header while the user is still editing.
struct WifiJoinHeader: View {
    let step: WifiJoin.Step
    let ssid: String
    let name: String

    var body: some View {
        switch step {
        case .editing:
            EmptyView()
        case .saving:
            HeaderBand(indicator: .spinner(Palette.accent), headline: "Saving", color: Palette.accent,
                       detail: "Sending the network to \(name).")
        case .joining:
            HeaderBand(indicator: .spinner(Palette.accent), headline: "Joining \(ssid)", color: Palette.accent,
                       detail: "This can take up to half a minute.")
        case .reachingHermes:
            HeaderBand(indicator: .spinner(Palette.accent), headline: "Reaching Hermes", color: Palette.accent,
                       detail: "On Wi-Fi. Connecting to the Hermes server.")
        case .done(approved: true):
            HeaderBand(indicator: .pulse(Palette.green), headline: "Online",
                       detail: "\(name) is on \(ssid) and connected to Hermes.")
        case .done(approved: false):
            HeaderBand(indicator: .ring(Palette.accent), headline: "Waiting for approval", color: Palette.accent,
                       detail: "\(name) is on \(ssid) and reached Hermes. Approve it on the Hermes host to finish; the home screen shows the command.")
        case .noHermes:
            HeaderBand(indicator: .alert, headline: "Hermes isn't answering", color: Palette.red,
                       detail: "\(name) is on \(ssid), but the Hermes server didn't answer. Check the server address in Settings.")
        case .failed(let message):
            HeaderBand(indicator: .alert, headline: "Couldn't join", color: Palette.red, detail: message)
        case .saved:
            HeaderBand(indicator: .dot(Palette.green), headline: "Saved",
                       detail: "\(name) can join \(ssid) when it loses its current network. It shows as not tested until it first joins.")
        }
    }
}
