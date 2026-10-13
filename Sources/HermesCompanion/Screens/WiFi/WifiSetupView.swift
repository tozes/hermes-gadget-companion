import SwiftUI

/// Wi-Fi setup: network name and password, then progress until the gadget
/// is on the network and reaching Hermes.
struct WifiSetupView: View {
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
        switch join.step {
        case .editing:
            HeaderBand(indicator: .dot(store.status?.network == true ? Palette.green : Palette.red),
                       headline: store.status?.network == true ? "On Wi-Fi" : "Join a network",
                       detail: "\(name) joins 2.4 GHz Wi-Fi networks. It keeps the network for next time.")
        case .saving:
            HeaderBand(indicator: .spinner(Palette.accent), headline: "Saving", color: Palette.accent,
                       detail: "Sending the network to \(name).")
        case .joining:
            HeaderBand(indicator: .spinner(Palette.accent), headline: "Joining \(ssid)", color: Palette.accent,
                       detail: "This can take up to half a minute.")
        case .reachingHermes:
            HeaderBand(indicator: .spinner(Palette.accent), headline: "Reaching Hermes", color: Palette.accent,
                       detail: "On Wi-Fi. Connecting to the Hermes server.")
        case .done(approved: true):
            HeaderBand(indicator: .pulse(Palette.green), headline: "Online",
                       detail: "\(name) is on \(ssid) and connected to Hermes.")
        case .done(approved: false):
            HeaderBand(indicator: .ring(Palette.accent), headline: "Waiting for approval", color: Palette.accent,
                       detail: "\(name) is on \(ssid) and reached Hermes. Approve it on the Hermes host to finish; the home screen shows the command.")
        case .noHermes:
            HeaderBand(indicator: .alert, headline: "Hermes isn't answering", color: Palette.red,
                       detail: "\(name) is on \(ssid), but the Hermes server didn't answer. Check the server address in Settings.")
        case .failed(let message):
            HeaderBand(indicator: .alert, headline: "Couldn't join", color: Palette.red, detail: message)
        }
    }

    // MARK: Form

    private var form: some View {
        Panel {
            VStack(alignment: .leading, spacing: 16) {
                field("Network name", error: join.error(for: .ssid)) {
                    TextField("Network name", text: $ssid, prompt: placeholder("Wi-Fi network"))
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.username)
                        .submitLabel(.next)
                        .focused($focus, equals: .ssid)
                        .onSubmit { focus = .password }
                        .gadgetInput(invalid: join.error(for: .ssid) != nil)
                }
                field("Password", error: join.error(for: .password)) {
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

    private func field<Input: View>(_ label: String, error: String?, @ViewBuilder input: () -> Input) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            PixelText(label.uppercased(), scale: 2, color: Palette.dim)
                .accessibilityHidden(true)
            input()
            if let error {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(Palette.red)
                    .accessibilityLabel(Text("Error: \(error)"))
            }
        }
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
            case .done:
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
