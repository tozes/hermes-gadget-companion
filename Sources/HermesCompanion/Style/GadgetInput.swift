import SwiftUI

extension View {
    /// A text field on the gadget's background: system font for Dynamic Type,
    /// at least 44 points tall, outlined red when its value was refused.
    func gadgetInput(monospaced: Bool = false, invalid: Bool = false) -> some View {
        font(monospaced ? .body.monospaced() : .body)
            .foregroundStyle(Palette.text)
            .tint(Palette.accent)
            .padding(.horizontal, 12)
            .frame(minHeight: 44)
            .background(Palette.background, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 8, style: .continuous)
                    .strokeBorder(invalid ? Palette.red : Palette.faint, lineWidth: 1)
            )
    }
}

/// Placeholder text in the dim colour, which the default placeholder isn't on this background.
func placeholder(_ text: String) -> Text {
    Text(text).foregroundStyle(Palette.dim)
}
