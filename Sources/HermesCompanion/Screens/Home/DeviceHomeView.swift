import GadgetKit
import SwiftUI

enum HomeRoute: Hashable {
    case wifi, addWifi, settings, advanced, console
}

/// The connected gadget at a glance, with the way to every other screen.
struct DeviceHomeView: View {
    @Environment(GadgetStore.self) private var store
    @State private var path: [HomeRoute] = []

    private static let pollInterval: Duration = .seconds(15)

    private var name: String { store.status?.name ?? store.gadget?.name ?? "Gadget" }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(spacing: 24) {
                    if store.connectionLost {
                        ConnectionLostBanner(name: name, reconnect: store.reconnect, leave: store.backToFinding)
                    }
                    header
                    nextStep
                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel("Set up")
                        Panel {
                            NavigationRow(title: "Wi-Fi", summary: wifiSummary, value: HomeRoute.wifi)
                            PanelDivider()
                            NavigationRow(title: "Settings", summary: "Name, sound, screen, Hermes server", value: HomeRoute.settings)
                        }
                        .disabled(store.connectionLost)
                        Panel {
                            NavigationRow(title: "Advanced", summary: "Status details, console, forget this gadget",
                                          value: HomeRoute.advanced)
                        }
                    }
                    HintLine(updatedHint)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
            .refreshable { await store.refreshStatus() }
            .gadgetScreen()
            .gadgetNavigation(title: name)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    // Back to the gadget list: leaves this gadget and scans again.
                    // Drawn like the system back button the other screens get.
                    Button(action: store.backToFinding) {
                        HStack(spacing: 4) {
                            Image(systemName: "chevron.backward").fontWeight(.semibold)
                            Text("Gadgets")
                        }
                    }
                    .accessibilityLabel("Back to gadgets")
                    .accessibilityHint("Disconnects from \(name) and looks for gadgets nearby.")
                }
                ToolbarItem(placement: .topBarTrailing) { topBar }
            }
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .wifi: WifiSetupView()
                case .addWifi: AddWifiNetworkView()
                case .settings: SettingsView()
                case .advanced: AdvancedView()
                case .console: ConsoleView()
                }
            }
        }
        .task { await poll() }
    }

    // MARK: Parts

    /// The mascot with the connection state under her, as on the find screen.
    private var header: some View {
        let summary = store.connectionLost ? StatusSummary.connectionLost(name: name) : StatusSummary(store.status)
        return VStack(spacing: 8) {
            Hero(indicator: summary.indicator, headline: summary.headline, color: summary.headlineColor,
                 detail: summary.detail)
            if let error = store.statusError {
                Text("Couldn't refresh: \(error)")
                    .font(.footnote)
                    .foregroundStyle(Palette.red)
                    .multilineTextAlignment(.center)
            }
        }
    }

    @ViewBuilder
    private var nextStep: some View {
        switch store.connectionLost ? nil : StatusSummary(store.status).next {
        case .setUpWifi:
            GadgetButton("Set up Wi-Fi") { path.append(.wifi) }
                .disabled(store.connectionLost)
        case .approveOnHermes(let code):
            ApprovalPanel(name: name, code: code)
        case nil:
            EmptyView()
        }
    }

    /// The gadget's Wi-Fi and battery, like its own top bar. Whether it's
    /// online is the header's to show, once.
    @ViewBuilder
    private var topBar: some View {
        let status = store.status
        HStack(spacing: 10) {
            // A dropped link leaves the last status behind: don't show it as current.
            if !store.connectionLost, let network = status?.network {
                WifiGlyph(connected: network, scale: 2)
            }
            if !store.connectionLost, let gauge = status?.power?.gauge {
                BatteryGlyph(percent: gauge.percent, charging: gauge.charging, low: gauge.low, scale: 2)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(topBarSpoken))
    }

    private var topBarSpoken: String {
        var parts: [String] = []
        if store.connectionLost { return "" }
        if let network = store.status?.network { parts.append(network ? "Wi-Fi connected" : "No Wi-Fi") }
        if let gauge = store.status?.power?.gauge {
            parts.append("Battery \(gauge.percent) percent\(gauge.charging ? ", charging" : "")")
        }
        return parts.joined(separator: ". ")
    }

    private var wifiSummary: String {
        if let list = store.wifiNetworks, !list.networks.isEmpty {
            let saved = list.networks.count == 1 ? "1 network saved" : "\(list.networks.count) networks saved"
            return list.current.map { "On \($0) · \(saved)" } ?? "Not connected · \(saved)"
        }
        if let ssid = store.settings[.wifiSSID], !ssid.isEmpty {
            return store.status?.network == true ? "On \(ssid)" : "\(ssid), not connected"
        }
        return store.status?.network == true ? "Connected" : "Not set up"
    }

    private var updatedHint: String {
        guard let date = store.statusDate else { return "Pull down to refresh" }
        return "Updated \(date.formatted(date: .omitted, time: .shortened))"
    }

    // MARK: Polling

    /// Keeps status fresh while the home is on screen, and loads settings once.
    private func poll() async {
        if store.missingSettings { await store.loadSettings() }
        while !Task.isCancelled {
            try? await Task.sleep(for: Self.pollInterval)
            guard !Task.isCancelled, path.isEmpty else { continue }
            await store.refreshStatus()
        }
    }
}
