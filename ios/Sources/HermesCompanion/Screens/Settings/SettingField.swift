import SwiftUI

/// Where a setting's last save stands.
enum SaveState: Equatable {
    case idle
    case saving
    case saved
    /// The gadget's `@error` message.
    case failed(String)
}

/// One setting inside a panel: a pixel label with the save state beside it,
/// the control, and the gadget's error underneath the control it belongs to.
struct SettingField<Control: View>: View {
    let label: String
    let state: SaveState
    var note: String?
    private let control: Control

    init(_ label: String, state: SaveState, note: String? = nil, @ViewBuilder control: () -> Control) {
        self.label = label
        self.state = state
        self.note = note
        self.control = control()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                PixelText(label.uppercased(), scale: 2, color: Palette.dim)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 8)
                switch state {
                case .saving:
                    StatusIndicator(kind: .spinner(Palette.accent), size: 14)
                    PixelText("saving", scale: 2, color: Palette.dim)
                case .saved:
                    PixelText("saved", scale: 2, color: Palette.green)
                case .idle, .failed:
                    EmptyView()
                }
            }
            control
            if case .failed(let message) = state {
                HStack(alignment: .firstTextBaseline, spacing: 8) {
                    PixelText("!", scale: 2, color: Palette.red)
                        .accessibilityHidden(true)
                    Text(message)
                        .font(.footnote)
                        .foregroundStyle(Palette.red)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .accessibilityElement(children: .combine)
                .accessibilityLabel(Text("Error: \(message)"))
            } else if let note {
                Text(note)
                    .font(.footnote)
                    .foregroundStyle(Palette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .padding(16)
    }
}

extension SaveState {
    /// Runs a save and shows "saved" for a moment afterwards.
    @MainActor
    static func run(_ state: Binding<SaveState>, _ save: () async -> String?) async {
        state.wrappedValue = .saving
        if let error = await save() {
            state.wrappedValue = .failed(error)
            return
        }
        state.wrappedValue = .saved
        try? await Task.sleep(for: .seconds(2))
        if state.wrappedValue == .saved { state.wrappedValue = .idle }
    }
}
