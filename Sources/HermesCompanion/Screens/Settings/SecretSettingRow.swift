import SwiftUI

/// The Hermes access token: write-only. The gadget only says whether one is set.
struct SecretSettingRow: View {
    @Environment(GadgetStore.self) private var store
    let key: SettingKey
    let label: String

    @State private var draft = ""
    @State private var state: SaveState = .idle

    private var isSet: Bool { store.settings[key] == SettingValue.maskedSecret }

    var body: some View {
        SettingField(label, state: state, note: isSet ? "A token is saved. Type a new one to replace it." : "No token saved.") {
            HStack(spacing: 8) {
                SecureField(label, text: $draft, prompt: placeholder(isSet ? "Saved" : "Paste the token"))
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .submitLabel(.done)
                    .onSubmit { save(draft) }
                    .gadgetInput(monospaced: true, invalid: state.isFailure)
                if !draft.isEmpty {
                    GadgetButton("Save") { save(draft) }
                        .frame(minWidth: 104)
                        .fixedSize()
                        .disabled(state == .saving)
                } else if isSet {
                    GadgetButton("Clear", kind: .secondary) { save("") }
                        .frame(minWidth: 104)
                        .fixedSize()
                        .disabled(state == .saving)
                }
            }
        }
    }

    private func save(_ value: String) {
        Task {
            await SaveState.run($state) {
                let error = await store.save(key, value)
                if error == nil { draft = "" }
                return error
            }
        }
    }
}
