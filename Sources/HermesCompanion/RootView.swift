import GadgetKit
import SwiftUI

/// Shows the screen for where the app is: finding a gadget, pairing, or connected.
struct RootView: View {
    @Environment(GadgetStore.self) private var store
    @State private var debugMenu = DebugMenu()

    var body: some View {
        ZStack {
            Palette.background.ignoresSafeArea()
            switch store.stage {
            case .finding:
                FindGadgetView()
                    .transition(.opacity)
            case .pairing:
                PairingView()
                    .transition(.opacity)
            case .connected:
                DeviceHomeView()
                    .transition(.opacity)
            }
        }
        .animation(.easeInOut(duration: 0.25), value: store.stage)
        .environment(debugMenu)
        .sheet(isPresented: $debugMenu.isPresented) {
            DebugMenuView()
                .environment(debugMenu)
        }
        .preferredColorScheme(.dark)
        .tint(Palette.accent)
    }
}
