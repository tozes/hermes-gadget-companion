import GadgetKit
import SwiftUI

/// The networks the gadget keeps, first one tried first. Tap a network to
/// join it now or forget it; add more on their own screen.
struct WifiNetworksView: View {
    @Environment(GadgetStore.self) private var store

    @State private var join = WifiJoin()
    @State private var task: Task<Void, Never>?
    /// The network whose actions are showing, then the one being joined.
    @State private var chosen: WifiNetworkList.Network?
    @State private var showingActions = false
    @State private var forgetting: String?
    @State private var actionError: String?

    private var name: String { store.status?.name ?? "The gadget" }
    private var list: WifiNetworkList? { store.wifiNetworks }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                if join.step != .editing {
                    WifiProgress(step: join.step)
                    progressActions
                } else {
                    saved
                    add
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .refreshable { await store.loadWifiNetworks() }
        .gadgetScreen()
        .gadgetNavigation(title: "Wi-Fi")
        .confirmationDialog(chosen?.ssid ?? "", isPresented: $showingActions, titleVisibility: .visible, presenting: chosen) { network in
            if network.ssid != list?.current {
                Button("Join now") { start(network) }
            }
            Button("Forget", role: .destructive) { forget(network) }
            Button("Cancel", role: .cancel) {}
        } message: { network in
            Text(network.ssid == list?.current
                 ? "\(name) is on this network. Forgetting it leaves it, and \(name) moves to another saved network."
                 : "Joining moves it to the top of the list. If it doesn't work, \(name) goes back to its other networks.")
        }
        .onDisappear { task?.cancel() }
    }

    // MARK: Header

    @ViewBuilder
    private var header: some View {
        if join.step == .editing {
            let current = list?.current
            HeaderBand(indicator: .dot(current != nil ? Palette.green : Palette.red),
                       headline: current.map { "On \($0)" } ?? "Not on Wi-Fi",
                       detail: "\(name) keeps up to \(list?.max ?? 5) networks and joins whichever is in range, starting with the one that worked last. 2.4 GHz only.")
        } else {
            WifiJoinHeader(step: join.step, ssid: chosen?.ssid ?? "", name: name)
        }
    }

    // MARK: Saved networks

    @ViewBuilder
    private var saved: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel("Saved networks")
            if let networks = list?.networks, !networks.isEmpty {
                Panel {
                    ForEach(Array(networks.enumerated()), id: \.element.id) { index, network in
                        if index > 0 { PanelDivider() }
                        row(network)
                    }
                }
                .disabled(store.connectionLost || forgetting != nil)
            } else {
                Text("None yet. Add the network \(name) should use.")
                    .font(.body)
                    .foregroundStyle(Palette.dim)
            }
            if let actionError {
                Text(actionError)
                    .font(.footnote)
                    .foregroundStyle(Palette.red)
                    .accessibilityLabel(Text("Error: \(actionError)"))
            }
        }
    }

    private func row(_ network: WifiNetworkList.Network) -> some View {
        Button {
            actionError = nil
            chosen = network
            showingActions = true
        } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(network.ssid)
                        .font(.body)
                        .foregroundStyle(Palette.text)
                        .multilineTextAlignment(.leading)
                    PixelText(state(of: network).uppercased(), scale: 2, color: color(of: network))
                }
                Spacer(minLength: 8)
                if forgetting == network.ssid {
                    StatusIndicator(kind: .spinner(Palette.accent), size: 16)
                } else {
                    PixelText(">", scale: 2, color: Palette.dim)
                        .accessibilityHidden(true)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .frame(minHeight: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(RowStyle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(network.ssid))
        .accessibilityValue(Text(state(of: network)))
        .accessibilityHint(Text("Join or forget"))
    }

    private func state(of network: WifiNetworkList.Network) -> String {
        let open = network.open ? ", open" : ""
        if network.ssid == list?.current { return "On now" + open }
        return (network.tested ? "Saved" : "Saved, not tested") + open
    }

    private func color(of network: WifiNetworkList.Network) -> Color {
        if network.ssid == list?.current { return Palette.green }
        return network.tested ? Palette.dim : Palette.accent
    }

    // MARK: Adding

    @ViewBuilder
    private var add: some View {
        VStack(alignment: .leading, spacing: 10) {
            NavigationLink(value: HomeRoute.addWifi) {
                PixelText("Add a network", scale: 2, color: Palette.accentInk, centered: true)
                    .frame(maxWidth: .infinity, minHeight: 48)
                    .background(Palette.accent, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
            }
            .disabled(list?.isFull == true || store.connectionLost)
            .opacity(list?.isFull == true || store.connectionLost ? 0.45 : 1)
            if list?.isFull == true {
                HintLine("\(list?.max ?? 5) saved. Forget one to add another.")
            }
        }
    }

    // MARK: Joining

    @ViewBuilder
    private var progressActions: some View {
        VStack(spacing: 12) {
            switch join.step {
            case .saving, .joining, .reachingHermes:
                GadgetButton("Stop watching", kind: .secondary) { finish() }
            case .noHermes:
                NavigationLink(value: HomeRoute.settings) {
                    PixelText("Check the server", scale: 2, color: Palette.accentInk, centered: true)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(Palette.accent, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                GadgetButton("Back to networks", kind: .secondary) { finish() }
            case .done, .failed, .saved, .editing:
                GadgetButton("Back to networks") { finish() }
            }
        }
    }

    private func start(_ network: WifiNetworkList.Network) {
        guard !join.isRunning else { return }
        chosen = network
        task?.cancel()
        task = Task { await join.join(store: store, ssid: network.ssid, newPassword: nil) }
    }

    private func finish() {
        task?.cancel()
        join.reset()
        Task { await store.loadWifiNetworks() }
    }

    private func forget(_ network: WifiNetworkList.Network) {
        forgetting = network.ssid
        Task {
            actionError = await store.forgetWifi(network.ssid)
            forgetting = nil
        }
    }
}
