import SwiftUI

/// A percentage setting (volume, brightness), saved when the slider is released.
struct SliderSettingRow: View {
    @Environment(GadgetStore.self) private var store
    let key: SettingKey
    let label: String
    let range: ClosedRange<Int>
    var note: String?

    @State private var value: Double = 0
    @State private var editing = false
    @State private var state: SaveState = .idle

    private var saved: Int? { store.settings[key].flatMap { Int($0) } }

    var body: some View {
        SettingField(label, state: state, note: note) {
            HStack(spacing: 14) {
                Slider(
                    value: $value,
                    in: Double(range.lowerBound)...Double(range.upperBound),
                    step: 1
                ) {
                    Text(label)
                } onEditingChanged: { isEditing in
                    editing = isEditing
                    if !isEditing { save() }
                }
                .tint(Palette.accent)
                .disabled(saved == nil)
                PixelText(saved == nil ? "--" : "\(Int(value))%", scale: 2, color: Palette.text)
                    .frame(width: 56, alignment: .trailing)
                    .accessibilityHidden(true)
            }
            .frame(minHeight: 44)
        }
        .onAppear(perform: load)
        .onChange(of: saved) { load() }
    }

    private func load() {
        guard let saved, !editing else { return }
        value = Double(saved)
    }

    private func save() {
        let new = Int(value)
        guard new != saved else { return }
        Task {
            await SaveState.run($state) {
                let error = await store.save(key, String(new))
                // Show what the gadget kept, not the value it refused.
                if error != nil, let saved { value = Double(saved) }
                return error
            }
        }
    }
}
