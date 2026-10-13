import SwiftUI

/// A round gadget: the site's metal bezel around a dark circular screen.
/// Content is clipped to the screen, as on a round panel.
struct DeviceShell<Content: View>: View {
    private let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        ZStack {
            Circle()
                .fill(
                    LinearGradient(
                        stops: [
                            .init(color: Palette.shellLight, location: 0),
                            .init(color: Palette.shellDark, location: 0.25),
                            .init(color: Palette.shellDeep, location: 0.7),
                            .init(color: Palette.shellSheen, location: 1),
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
                .overlay(Circle().strokeBorder(Palette.shellEdge, lineWidth: 1))
            GeometryReader { proxy in
                let inset = proxy.size.width * 0.035
                ZStack {
                    Circle().fill(Palette.background)
                    content
                }
                .clipShape(Circle())
                .padding(inset)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .shadow(color: .black.opacity(0.4), radius: 20, y: 12)
    }
}
