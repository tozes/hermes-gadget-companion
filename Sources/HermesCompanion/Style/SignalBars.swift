import SwiftUI

/// Four stepped bars for a gadget's Bluetooth signal, lit up to `level` (1...4).
struct SignalBars: View {
    let level: Int
    var scale: CGFloat = 2

    var body: some View {
        HStack(alignment: .bottom, spacing: scale) {
            ForEach(1...4, id: \.self) { bar in
                Rectangle()
                    .fill(bar <= level ? Palette.green : Palette.faint)
                    .frame(width: 2 * scale, height: CGFloat(bar * 2 + 1) * scale)
            }
        }
        .accessibilityHidden(true)
    }
}
