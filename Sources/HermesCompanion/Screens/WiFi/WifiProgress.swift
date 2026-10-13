import GadgetKit
import SwiftUI

/// The three stages of joining, each with the device's indicator for its state.
struct WifiProgress: View {
    let step: WifiJoin.Step

    private enum Mark {
        case waiting, active, done, failed
    }

    var body: some View {
        Panel {
            row("Save the network", mark(stage: 0))
            PanelDivider()
            row("Join Wi-Fi", mark(stage: 1))
            PanelDivider()
            row("Reach Hermes", mark(stage: 2))
        }
    }

    /// Which stage is current, and whether it failed.
    private var position: (stage: Int, failed: Bool) {
        switch step {
        case .editing, .saving: (0, false)
        case .joining: (1, false)
        case .failed: (1, true)
        case .reachingHermes: (2, false)
        case .noHermes: (2, true)
        case .done, .saved: (3, false)
        }
    }

    private func mark(stage: Int) -> Mark {
        let (current, failed) = position
        if stage < current { return .done }
        if stage > current { return .waiting }
        return failed ? .failed : .active
    }

    private func row(_ title: String, _ mark: Mark) -> some View {
        HStack(spacing: 14) {
            switch mark {
            case .waiting: StatusIndicator(kind: .dot(Palette.faint), size: 18)
            case .active: StatusIndicator(kind: .spinner(Palette.accent), size: 18)
            case .done: StatusIndicator(kind: .dot(Palette.green), size: 18)
            case .failed: StatusIndicator(kind: .alert, size: 18)
            }
            PixelText(title, scale: 2, color: mark == .waiting ? Palette.dim : mark == .failed ? Palette.red : Palette.text)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 48)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(title))
        .accessibilityValue(Text(spoken(mark)))
    }

    private func spoken(_ mark: Mark) -> String {
        switch mark {
        case .waiting: "Not started"
        case .active: "In progress"
        case .done: "Done"
        case .failed: "Failed"
        }
    }
}
