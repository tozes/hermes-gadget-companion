import SwiftUI

/// How to make a gadget appear: numbered steps, like the project site's cards.
struct PairingSteps: View {
    private let steps = [
        "On the gadget, open device settings: hold the title bar for a second. Round screens show a SETTINGS label under the status dot instead; with buttons, hold TALK and CANCEL together.",
        "Choose Phone app. Pairing stays open for two minutes.",
        "Pick the gadget here, then type the six-digit code from its screen when iPhone asks.",
    ]

    var body: some View {
        Panel {
            ForEach(Array(steps.enumerated()), id: \.offset) { index, step in
                if index > 0 { PanelDivider() }
                HStack(alignment: .firstTextBaseline, spacing: 14) {
                    PixelText(String(format: "%02d", index + 1), scale: 2, color: Palette.accent)
                        .accessibilityLabel(Text("Step \(index + 1)"))
                    Text(step)
                        .font(.subheadline)
                        .foregroundStyle(Palette.text)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(16)
                .accessibilityElement(children: .combine)
            }
        }
    }
}
