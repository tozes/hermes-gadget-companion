import GadgetKit
import SwiftUI

/// Wi-Fi: the gadget's saved networks, or the one-network form on firmware
/// without the `wifi` command.
struct WifiSetupView: View {
    @Environment(GadgetStore.self) private var store
    @State private var loadError: String?
    @State private var isLoading = false

    var body: some View {
        Group {
            if store.keepsOneNetwork {
                OneNetworkWifiView()
            } else if store.wifiNetworks != nil {
                WifiNetworksView()
            } else {
                loading
            }
        }
        .task {
            if store.wifiNetworks == nil, !store.keepsOneNetwork { await load() }
        }
    }

    private var loading: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if let loadError {
                    HeaderBand(indicator: .alert, headline: "Couldn't read Wi-Fi", color: Palette.red, detail: loadError)
                    GadgetButton("Try again", isBusy: isLoading) { Task { await load() } }
                        .disabled(isLoading || store.connectionLost)
                } else {
                    HeaderBand(indicator: .spinner(Palette.accent), headline: "Reading networks", color: Palette.accent,
                               detail: "Asking \(store.status?.name ?? "the gadget") which networks it keeps.")
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .gadgetScreen()
        .gadgetNavigation(title: "Wi-Fi")
    }

    private func load() async {
        isLoading = true
        loadError = await store.loadWifiNetworks()
        isLoading = false
    }
}
