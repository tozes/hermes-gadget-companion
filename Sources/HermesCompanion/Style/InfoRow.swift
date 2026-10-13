import SwiftUI

/// A label and value inside a panel. Stacks vertically at accessibility text sizes.
struct InfoRow: View {
    let label: String
    let value: String
    var valueColor: Color = Palette.text
    var monospaced = false
    /// Characters after which a long value may wrap, so an address breaks
    /// after `/` or `.` rather than inside its port number. Such a value
    /// isn't selectable: the break points would be copied with it.
    var breaksAfter: Set<Character> = []

    /// One column for every label, wide enough for the longest ("DEVICE ID")
    /// at the pixel font's scale 2, so the values line up.
    static let labelWidth = CGFloat(PixelFont.width(columns: 10) * 2)

    @Environment(\.dynamicTypeSize) private var typeSize

    var body: some View {
        let layout = typeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 6))
            : AnyLayout(HStackLayout(alignment: .firstTextBaseline, spacing: 12))
        layout {
            PixelText(label.uppercased(), scale: 2, color: Palette.dim)
                .frame(width: typeSize.isAccessibilitySize ? nil : Self.labelWidth, alignment: .leading)
            valueText
                .font(monospaced ? .body.monospaced() : .body)
                .foregroundStyle(valueColor)
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

    @ViewBuilder
    private var valueText: some View {
        if breaksAfter.isEmpty {
            Text(value).textSelection(.enabled)
        } else {
            Text(Self.wrappable(value, after: breaksAfter))
        }
    }

    /// `value` with a zero-width space after each break character, so the line
    /// wraps there first.
    static func wrappable(_ value: String, after breaks: Set<Character>) -> String {
        var out = ""
        for character in value {
            out.append(character)
            if breaks.contains(character) { out.append("\u{200B}") }
        }
        return out
    }
}
