import GadgetKit
import SwiftUI

/// Pairing: connecting, waiting for the passkey that iOS asks for, and the
/// ways it fails. The code is on the gadget's screen; the app never sees it.
struct PairingView: View {
    @Environment(GadgetStore.self) private var store

    private var name: String { store.gadget?.name ?? "the gadget" }

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: name, linkColor: dotColor, linkLabel: "PAIR", spokenState: "Pairing")
            ScrollView {
                VStack(spacing: 28) {
                    HeaderBand(indicator: indicator, headline: headline, color: headlineColor, detail: detail)
                    actions
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 28)
            }
            HintLine(hint)
                .background(Palette.background)
        }
        .background(Palette.background.ignoresSafeArea())
    }

    // MARK: Header

    private var indicator: StatusIndicator.Kind {
        switch store.pairing {
        case .connecting: .spinner(Palette.accent)
        case .awaitingPasskey: .ring(Palette.accent)
        case .failed: .alert
        }
    }

    private var headlineColor: Color {
        if case .failed = store.pairing { return Palette.red }
        return Palette.accent
    }

    private var dotColor: Color {
        if case .failed = store.pairing { return Palette.red }
        return Palette.yellow
    }

    private var headline: String {
        switch store.pairing {
        case .connecting: "Connecting to \(name)"
        case .awaitingPasskey: "Enter the code"
        case .failed(.pairingRejected): "Pairing failed"
        case .failed(.pairingTimedOut): "Pairing timed out"
        case .failed(.pairingRemoved): "Pair again"
        case .failed(.unreachable): "Can't reach \(name)"
        case .failed(.connectionLost): "Connection lost"
        }
    }

    private var detail: String {
        switch store.pairing {
        case .connecting:
            "Keep your iPhone near the gadget."
        case .awaitingPasskey where store.isDemo:
            "With a real gadget, iPhone asks for the six digits shown on \(name)'s screen. In the demo, pairing finishes by itself."
        case .awaitingPasskey:
            "iPhone is asking for a pairing code. Type the six digits shown on \(name)'s screen. Only the gadget and iPhone see the code, so it never appears in this app."
        case .failed(.pairingRejected):
            "The code didn't match, or the request was cancelled. Check the six digits on \(name)'s screen and try again."
        case .failed(.pairingTimedOut):
            "Pairing on \(name) closes two minutes after you open it. On the gadget, open device settings > Phone app again, then try again."
        case .failed(.pairingRemoved):
            "\(name) no longer knows this iPhone. In iPhone Settings > Bluetooth, tap the info button next to it and choose Forget This Device. Then open device settings > Phone app on the gadget and try again."
        case .failed(.unreachable):
            "Move closer and check that the gadget is on."
        case .failed(.connectionLost):
            "The gadget went away before pairing finished. Move closer and try again."
        }
    }

    private var hint: String {
        switch store.pairing {
        case .connecting: "Connecting"
        case .awaitingPasskey: "The code is on the gadget"
        case .failed: "Pairing stays open 2 minutes"
        }
    }

    // MARK: Actions

    @ViewBuilder
    private var actions: some View {
        VStack(spacing: 12) {
            switch store.pairing {
            case .connecting, .awaitingPasskey:
                GadgetButton("Cancel", kind: .secondary) { store.backToFinding() }
            case .failed:
                GadgetButton("Try again") { store.retryPairing() }
                GadgetButton("Back to gadgets", kind: .secondary) { store.backToFinding() }
            }
        }
    }
}
