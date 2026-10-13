import GadgetKit
import SwiftUI

enum HomeRoute: Hashable {
    case wifi, addWifi, settings, console
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
                    HomeDevice(status: store.status, talkMode: store.settings[.talkMode])
                        .frame(maxWidth: 300)
                    header
                    nextStep
                    if let status = store.status {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionLabel("Gadget")
                            StatusDetails(status: status)
                        }
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel("Set up")
                        Panel {
                            NavigationRow(title: "Wi-Fi", summary: wifiSummary, value: HomeRoute.wifi)
                            PanelDivider()
                            NavigationRow(title: "Settings", summary: "Name, sound, screen, Hermes server", value: HomeRoute.settings)
                            PanelDivider()
                            NavigationRow(title: "Console", summary: "Type console commands", value: HomeRoute.console)
                        }
                        .disabled(store.connectionLost)
                    }
                    ForgetGadgetSection(name: name)
                    HintLine(updatedHint)
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 24)
            }
            .refreshable { await store.refreshStatus() }
            .gadgetScreen()
            .gadgetNavigation(title: name)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) { linkStatus }
            }
            .navigationDestination(for: HomeRoute.self) { route in
                switch route {
                case .wifi: WifiSetupView()
                case .addWifi: AddWifiNetworkView()
                case .settings: SettingsView()
                case .console: ConsoleView()
                }
            }
        }
        .task { await poll() }
    }

    // MARK: Parts

    private var header: some View {
        let summary = StatusSummary(store.status)
        return VStack(alignment: .leading, spacing: 8) {
            HeaderBand(indicator: summary.indicator, headline: summary.headline, color: summary.headlineColor, detail: summary.detail)
            if let error = store.statusError {
                Text("Couldn't refresh: \(error)")
                    .font(.footnote)
                    .foregroundStyle(Palette.red)
            }
        }
    }

    @ViewBuilder
    private var nextStep: some View {
        switch StatusSummary(store.status).next {
        case .setUpWifi:
            GadgetButton("Set up Wi-Fi") { path.append(.wifi) }
                .disabled(store.connectionLost)
        case .approveOnHermes(let code):
            ApprovalPanel(name: name, code: code)
        case nil:
            EmptyView()
        }
    }

    /// The gadget's link dot and label, with its battery, like its own top bar.
    private var linkStatus: some View {
        let link = HermesLink(store.status)
        let gauge = store.status?.power?.gauge
        return HStack(spacing: 8) {
            if let gauge {
                BatteryGlyph(percent: gauge.percent, charging: gauge.charging, low: gauge.low, scale: 2)
            }
            Circle().fill(store.connectionLost ? Palette.faint : link.color).frame(width: 8, height: 8)
            PixelText(link.label, scale: 2, color: Palette.dim)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(gauge.map { "\(link.spoken). Battery \($0.percent) percent\($0.charging ? ", charging" : "")" } ?? link.spoken))
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
