import GadgetKit
import SwiftUI

extension GadgetStore {
    /// Swaps in pretend gadgets to explore the app with, or take screenshots.
    /// Not kept across launches: the app always opens on real Bluetooth.
    func startDemo() {
        replaceLink(MockGadgetLink(scenario: .demo))
    }

    func endDemo() {
        replaceLink(BluetoothGadgetLink())
    }
}

/// Says the app is in the demo, with the way out.
struct DemoNote: View {
    @Environment(GadgetStore.self) private var store
    var detail = "The gadgets here are pretend, so you can look around without one. Nothing is sent over Bluetooth."

    var body: some View {
        Panel {
            VStack(alignment: .leading, spacing: 12) {
                PixelText("Demo", scale: 2, color: Palette.accent)
                Text(detail)
                    .font(.subheadline)
                    .foregroundStyle(Palette.dim)
                    .fixedSize(horizontal: false, vertical: true)
                GadgetButton("Leave the demo", kind: .secondary) { store.endDemo() }
            }
            .padding(16)
        }
    }
}
