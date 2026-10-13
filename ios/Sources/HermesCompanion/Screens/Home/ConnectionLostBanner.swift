import SwiftUI

/// Shown on the device home when the Bluetooth link drops.
struct ConnectionLostBanner: View {
    let name: String
    let reconnect: () -> Void
    let leave: () -> Void

    var body: some View {
        Panel {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    StatusIndicator(kind: .alert, size: 18)
                    PixelText("Connection lost", scale: 2, color: Palette.red)
                        .accessibilityAddTraits(.isHeader)
                }
                Text("\(name) is out of reach or turned off. What's below is from the last status.")
                    .font(.subheadline)
                    .foregroundStyle(Palette.dim)
                    .fixedSize(horizontal: false, vertical: true)
                GadgetButton("Reconnect", action: reconnect)
                GadgetButton("Back to gadgets", kind: .secondary, action: leave)
            }
            .padding(16)
        }
        .overlay(RoundedRectangle(cornerRadius: 10, style: .continuous).strokeBorder(Palette.redDim, lineWidth: 1))
    }
}
