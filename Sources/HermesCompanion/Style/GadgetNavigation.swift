import SwiftUI

extension View {
    /// The navigation bar in the gadget's bar colour, with a pixel-font title.
    func gadgetNavigation(title: String) -> some View {
        navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbarBackground(Palette.bar, for: .navigationBar)
            .toolbarBackground(.visible, for: .navigationBar)
            .toolbarColorScheme(.dark, for: .navigationBar)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    PixelText(title.uppercased(), scale: 2, color: Palette.text)
                        .debugMenuTrigger()
                        .accessibilityAddTraits(.isHeader)
                }
            }
    }

    /// The gadget's background behind a screen.
    func gadgetScreen() -> some View {
        scrollContentBackground(.hidden)
            .background(Palette.background.ignoresSafeArea())
    }
}
