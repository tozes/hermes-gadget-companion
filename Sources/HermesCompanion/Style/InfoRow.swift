import SwiftUI

/// A label and value inside a panel. Stacks vertically at accessibility text sizes.
struct InfoRow: View {
    let label: String
    let value: String
    var valueColor: Color = Palette.text
    var monospaced = false

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout = typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: 12))
        layout {
            PixelText(label.uppercased(), scale: 2, color: Palette.dim)
                .frame(minWidth: 96, alignment: .leading)
            Text(value)
                .font(monospaced ? .body.monospaced() : .body)
                .foregroundStyle(valueColor)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .frame(minHeight: 44)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
        .accessibilityValue(Text(value))
    }
}
