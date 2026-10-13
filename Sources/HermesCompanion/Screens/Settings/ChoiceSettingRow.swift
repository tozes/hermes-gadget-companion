import SwiftUI

/// A setting with a few fixed values (talk mode, battery indicator), saved on tap.
struct ChoiceSettingRow: View {
    @Environment(GadgetStore.self) private var store
    let key: SettingKey
    let label: String
    let options: [PixelSegmented<String>.Option]
    /// Explains the selected value.
    var note: ((String?) -> String?)?

    @State private var pending: String?
    @State private var state: SaveState = .idle

    private var saved: String? { store.settings[key] }

    var body: some View {
        let shown = pending ?? saved
        SettingField(label, state: state, note: note?(shown)) {
            PixelSegmented(options: options, selection: shown) { value in
                guard value != shown else { return }
                pending = value
                Task {
                    await SaveState.run($state) { await store.save(key, value) }
                    pending = nil
                }
            }
            .disabled(saved == nil || state == .saving)
        }
    }
}
