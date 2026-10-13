import SwiftUI

/// A section label: dim pixel caps over a faint rule, like the header band's divider.
struct SectionLabel: View {
    let text: String

    init(_ text: String) {
        self.text = text
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            PixelText(text.uppercased(), scale: 2, color: Palette.dim)
            Rectangle().fill(Palette.faint).frame(height: 1)
        }
        .accessibilityElement(children: .combine)
        .accessibilityAddTraits(.isHeader)
    }
}
