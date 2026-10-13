import SwiftUI

/// Highlights a panel row while pressed.
struct RowStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background(configuration.isPressed ? Palette.faint : Color.clear)
    }
}
