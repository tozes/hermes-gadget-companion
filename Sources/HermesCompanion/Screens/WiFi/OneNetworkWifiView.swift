import GadgetKit
import SwiftUI

/// Wi-Fi setup on firmware that keeps one network (`set wifi_ssid` and
/// `set wifi_pass`): network name and password, then progress until the
/// gadget is on the network and reaching Hermes.
struct OneNetworkWifiView: View {
    @Environment(GadgetStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    @State private var join = WifiJoin()
    @State private var task: Task<Void, Never>?
    @State private var ssid = ""
    @State private var password = ""
    @State private var openNetwork = false
    @FocusState private var focus: WifiJoin.Field?

    private var name: String { store.status?.name ?? "The gadget" }
    private var passwordSaved: Bool { store.settings[.wifiPassword] == SettingValue.maskedSecret }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                header
                form
                if join.step != .editing {
                    WifiProgress(step: join.step)
                }
                actions
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .gadgetScreen()
        .gadgetNavigation(title: "Wi-Fi")
        .onAppear {
            if ssid.isEmpty { ssid = store.settings[.wifiSSID] ?? "" }
        }
        .onDisappear { task?.cancel() }
    }

    // MARK: Header

    @ViewBuilder
    private var header: some View {
        if join.step == .editing {
            HeaderBand(indicator: .dot(store.status?.network == true ? Palette.green : Palette.red),
                       headline: store.status?.network == true ? "On Wi-Fi" : "Join a network",
                       detail: "\(name) joins 2.4 GHz Wi-Fi networks. It keeps the network for next time.")
        } else {
            WifiJoinHeader(step: join.step, ssid: ssid, name: name)
        }
    }

    // MARK: Form

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
                    SecureField("Password", text: $password, prompt: placeholder(passwordSaved ? "Saved; type to replace" : "Wi-Fi password"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                        .submitLabel(.join)
                        .focused($focus, equals: .password)
                        .onSubmit(start)
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
            }
            .padding(16)
        }
        .disabled(join.isRunning || store.connectionLost)
    }

    // MARK: Actions

    @ViewBuilder
    private var actions: some View {
        VStack(spacing: 12) {
            switch join.step {
            case .editing, .failed:
                GadgetButton(join.step == .editing ? "Join network" : "Try again", action: start)
                    .disabled(ssid.trimmingCharacters(in: .whitespaces).isEmpty || store.connectionLost)
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
                GadgetButton("Edit network", kind: .secondary) { join.reset() }
            }
        }
    }

    private func start() {
        let network = ssid.trimmingCharacters(in: .whitespaces)
        guard !network.isEmpty, !join.isRunning else { return }
        focus = nil
        task?.cancel()
        task = Task { await join.run(store: store, ssid: network, password: password, clearPassword: openNetwork) }
    }
}
