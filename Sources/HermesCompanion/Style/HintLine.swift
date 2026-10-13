import SwiftUI

/// The dim hint line the gadget keeps at the bottom of every screen.
struct HintLine: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        PixelText(text, scale: 2, color: Palette.dim, centered: true, wraps: true)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
    }
}
