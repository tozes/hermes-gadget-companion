import SwiftUI

/// A rounded panel in the bar colour: the app's only card surface.
struct Panel<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) { content }
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Palette.bar, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

/// A faint divider between rows inside a panel.
struct PanelDivider: View {
    var body: some View {
        Rectangle().fill(Palette.faint).frame(height: 1).padding(.leading, 16)
    }
}
