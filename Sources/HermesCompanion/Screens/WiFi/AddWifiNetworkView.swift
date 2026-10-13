import GadgetKit
import SwiftUI

/// Adds a network to the gadget's list: join it now, or save it for later
/// (a phone hotspot, the office) without the gadget trying it yet.
struct AddWifiNetworkView: View {
    @Environment(GadgetStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    @State private var join = WifiJoin()
    @State private var task: Task<Void, Never>?
    @State private var ssid = ""
    @State private var password = ""
    @State private var openNetwork = false
    @FocusState private var focus: WifiJoin.Field?

    private var name: String { store.status?.name ?? "The gadget" }
    private var network: String { ssid.trimmingCharacters(in: .whitespaces) }
    private var alreadySaved: Bool { store.wifiNetworks?.contains(network) == true }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                form
                if join.step != .editing, join.step != .saved {
                    WifiProgress(step: join.step)
                }
                actions
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .gadgetScreen()
        .gadgetNavigation(title: "Add network")
        .onDisappear { task?.cancel() }
    }

    @ViewBuilder
    private var header: some View {
        if join.step == .editing {
            HeaderBand(indicator: .dot(Palette.accent), headline: "Add a network",
                       detail: "Join it now, or save it for later: \(name) uses it when it loses its current network and this one is in range.")
        } else {
            WifiJoinHeader(step: join.step, ssid: network, name: name)
        }
    }

    private var form: some View {
        Panel {
            VStack(alignment: .leading, spacing: 16) {
                WifiField("Network name", error: join.error(for: .ssid)) {
                    TextField("Network name", text: $ssid, prompt: placeholder("Wi-Fi network"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.username)
                        .submitLabel(.next)
                        .focused($focus, equals: .ssid)
                        .onSubmit { focus = .password }
                        .gadgetInput(invalid: join.error(for: .ssid) != nil)
                }
                WifiField("Password", error: join.error(for: .password)) {
                    SecureField("Password", text: $password, prompt: placeholder(alreadySaved ? "Saved; type to replace" : "Wi-Fi password"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                        .submitLabel(.join)
                        .focused($focus, equals: .password)
                        .onSubmit(joinNow)
                        .gadgetInput(invalid: join.error(for: .password) != nil)
                        .disabled(openNetwork)
                }
                Toggle(isOn: $openNetwork) {
                    Text("Open network, no password")
                        .font(.body)
                        .foregroundStyle(Palette.text)
                }
                .tint(Palette.accent)
                .frame(minHeight: 44)
                if alreadySaved {
                    Text("\(network) is already saved. Saving it again replaces its password.")
                        .font(.footnote)
                        .foregroundStyle(Palette.dim)
                }
            }
            .padding(16)
        }
        .disabled(join.isRunning || store.connectionLost || join.step == .saved)
    }

    @ViewBuilder
    private var actions: some View {
        VStack(spacing: 12) {
            switch join.step {
            case .editing, .failed:
                GadgetButton(join.step == .editing ? "Join now" : "Try again", action: joinNow)
                    .disabled(!canSubmit)
                GadgetButton("Save for later", kind: .secondary, action: saveForLater)
                    .disabled(!canSubmit || !hasNewPassword)
            case .saving, .joining, .reachingHermes:
                GadgetButton("Stop watching", kind: .secondary) {
                    task?.cancel()
                    join.reset()
                }
            case .done, .saved:
                GadgetButton("Done") { dismiss() }
            case .noHermes:
                NavigationLink(value: HomeRoute.settings) {
                    PixelText("Check the server", scale: 2, color: Palette.accentInk, centered: true)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .background(Palette.accent, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
                }
                GadgetButton("Done", kind: .secondary) { dismiss() }
            }
        }
    }

    /// A password to save: typed, or none for an open network.
    private var hasNewPassword: Bool { openNetwork || !password.isEmpty }

    /// A saved network can be joined again with its stored password.
    private var canSubmit: Bool {
        !network.isEmpty && (hasNewPassword || alreadySaved) && !store.connectionLost
    }

    private var chosenPassword: String { openNetwork ? "" : password }

    private func joinNow() {
        guard canSubmit, !join.isRunning else { return }
        focus = nil
        task?.cancel()
        let ssid = network, password: String? = hasNewPassword ? chosenPassword : nil
        task = Task { await join.join(store: store, ssid: ssid, newPassword: password) }
    }

    private func saveForLater() {
        guard canSubmit, !join.isRunning else { return }
        focus = nil
        task?.cancel()
        let ssid = network, password = chosenPassword
        task = Task { await join.save(store: store, ssid: ssid, password: password) }
    }
}
