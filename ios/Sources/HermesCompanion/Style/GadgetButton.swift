import SwiftUI

/// A full-width button with a pixel-font label, shaped like the device's
/// answer buttons (`Ui::draw_hero`): a rounded fill, the label centred.
struct GadgetButton: View {
    enum Kind {
        /// Amber with dark ink: the one thing to do next.
        case primary
        /// The bar colour: secondary actions.
        case secondary
        /// Dim red: removing or forgetting something.
        case destructive
    }

    private let title: String
    private let kind: Kind
    private let isBusy: Bool
    private let action: () -> Void

    @Environment(\.isEnabled) private var isEnabled

    init(_ title: String, kind: Kind = .primary, isBusy: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.kind = kind
        self.isBusy = isBusy
        self.action = action
    }

    var body: some View {
        Button(action: action) {
            HStack(spacing: 10) {
                if isBusy {
                    StatusIndicator(kind: .spinner(ink), size: 16)
                }
                PixelText(title, scale: 2, color: ink, centered: true, wraps: true)
            }
            .frame(maxWidth: .infinity, minHeight: 48)
            .padding(.horizontal, 12)
            .contentShape(Rectangle())
        }
        .buttonStyle(Style(fill: fill))
        .opacity(isEnabled ? 1 : 0.45)
        .accessibilityLabel(Text(title))
    }

    private var fill: Color {
        switch kind {
        case .primary: Palette.accent
        case .secondary: Palette.bar
        case .destructive: Palette.redDim
        }
    }

    private var ink: Color {
        switch kind {
        case .primary: Palette.accentInk
        case .secondary, .destructive: Palette.text
        }
    }

    private struct Style: ButtonStyle {
        let fill: Color

        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .background(fill, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                .brightness(configuration.isPressed ? -0.12 : 0)
        }
    }
}
