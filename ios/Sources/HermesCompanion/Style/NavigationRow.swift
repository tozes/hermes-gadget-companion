import SwiftUI

/// A row in a panel that opens another screen: a pixel title, a dim
/// one-line summary, and a chevron drawn in the pixel font.
struct NavigationRow<Value: Hashable>: View {
    let title: String
    var summary: String?
    let value: Value

    var body: some View {
        NavigationLink(value: value) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    PixelText(title, scale: 2, color: Palette.text)
                    if let summary {
                        Text(summary)
                            .font(.subheadline)
                            .foregroundStyle(Palette.dim)
                            .multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: 8)
                PixelText(">", scale: 2, color: Palette.dim)
                    .accessibilityHidden(true)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowStyle())
        .accessibilityElement(children: .combine)
    }
}
