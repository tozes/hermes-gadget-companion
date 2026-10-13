import GadgetKit
import SwiftUI

/// A text setting (name, server): edit, then Save or Return. Errors from
/// the gadget appear under the field.
struct TextSettingRow: View {
    @Environment(GadgetStore.self) private var store
    let key: SettingKey
    let label: String
    let prompt: String
    var note: String?
    var monospaced = false
    var isURL = false

    @State private var draft = ""
    @State private var loaded = false
    @State private var state: SaveState = .idle

    private var saved: String { store.settings[key] ?? "" }
    /// The gadget trims values, so compare what it would keep.
    private var trimmed: String { draft.trimmingCharacters(in: .whitespaces) }
    private var isDirty: Bool { loaded && trimmed != saved }

    var body: some View {
        SettingField(label, state: state, note: note) {
            HStack(spacing: 8) {
                TextField(label, text: $draft, prompt: placeholder(prompt))
                    .textInputAutocapitalization(key == .name ? .words : .never)
                    .autocorrectionDisabled()
                    .keyboardType(isURL ? .URL : .default)
                    .submitLabel(.done)
                    .onSubmit(save)
                    .gadgetInput(monospaced: monospaced, invalid: state.isFailure)
                    .disabled(!loaded)
                if isDirty {
                    GadgetButton("Save", action: save)
                        .frame(minWidth: 104)
                        .fixedSize()
                        .disabled(state == .saving)
                }
            }
        }
        .onAppear(perform: load)
        .onChange(of: saved) { load() }
    }

    private func load() {
        // Don't overwrite what the user is typing.
        guard store.settings[key] != nil, !isDirty else { return }
        draft = saved
        loaded = true
    }

    private func save() {
        guard isDirty else {
            draft = saved
            return
        }
        let value = trimmed
        Task {
            await SaveState.run($state) {
                let error = await store.save(key, value)
                if error == nil { draft = value }
                return error
            }
        }
    }
}

extension SaveState {
    var isFailure: Bool {
        if case .failed = self { return true }
        return false
    }
}
