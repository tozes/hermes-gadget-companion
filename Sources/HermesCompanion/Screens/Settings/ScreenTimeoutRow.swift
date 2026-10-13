import GadgetKit
import SwiftUI

/// Screen timeout: the device menu's presets, or any number of seconds up to
/// an hour. The gadget checks the range and answers `@error` otherwise.
struct ScreenTimeoutRow: View {
    @Environment(GadgetStore.self) private var store

    @State private var custom = ""
    @State private var state: SaveState = .idle

    private static let presets: [PixelSegmented<String>.Option] = [
        .init(value: "0", title: "Off", spoken: "Always on"),
        .init(value: "30", title: "30s", spoken: "30 seconds"),
        .init(value: "60", title: "1m", spoken: "1 minute"),
        .init(value: "120", title: "2m", spoken: "2 minutes"),
        .init(value: "300", title: "5m", spoken: "5 minutes"),
    ]

    private var saved: String? { store.settings[.screenTimeout] }

    var body: some View {
        SettingField("Screen timeout", state: state, note: description) {
            VStack(alignment: .leading, spacing: 10) {
                PixelSegmented(options: Self.presets, selection: saved) { save($0) }
                    .disabled(saved == nil || state == .saving)
                HStack(spacing: 8) {
                    TextField("Seconds", text: $custom, prompt: placeholder("Other: 0 to 3600 seconds"))
                        .keyboardType(.numberPad)
                        .gadgetInput(monospaced: true, invalid: state.isFailure)
                        .accessibilityLabel(Text("Custom timeout in seconds"))
                    if !custom.isEmpty {
                        GadgetButton("Save") { save(custom) }
                            .frame(minWidth: 104)
                            .fixedSize()
                            .disabled(state == .saving)
                    }
                }
            }
        }
    }

    private var description: String? {
        guard let saved, let seconds = Int(saved) else { return nil }
        if seconds == 0 { return "The screen stays on." }
        let duration = Duration.seconds(seconds).formatted(.units(allowed: [.minutes, .seconds], width: .wide))
        return "The screen sleeps after \(duration) without a touch."
    }

    private func save(_ value: String) {
        guard value != saved else { return }
        Task {
            await SaveState.run($state) {
                let error = await store.save(.screenTimeout, value)
                if error == nil { custom = "" }
                return error
            }
        }
    }
}
