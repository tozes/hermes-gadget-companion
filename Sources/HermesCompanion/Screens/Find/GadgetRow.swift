import GadgetKit
import SwiftUI

/// A gadget found while scanning: its advertised name and signal.
struct GadgetRow: View {
    let gadget: DiscoveredGadget
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Circle().fill(Palette.green).frame(width: 8, height: 8)
                PixelText(gadget.name, scale: 3, color: Palette.text)
                Spacer(minLength: 8)
                SignalBars(level: gadget.signalLevel, scale: 2)
                PixelText(">", scale: 2, color: Palette.dim)
            }
            .padding(.horizontal, 16)
            .frame(minHeight: 60)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(gadget.name))
        .accessibilityValue(Text("Signal \(gadget.signalDescription)"))
        .accessibilityHint(Text("Connects and pairs"))
        .accessibilityAddTraits(.isButton)
    }
}
