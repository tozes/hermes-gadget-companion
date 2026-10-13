import SwiftUI

/// A round gadget showing its hero screen, as on the device: the link dot at
/// the top, the mascot, a headline, short dim lines, and a hint at the bottom.
/// Keep the text short; the readable explanation belongs outside the device.
struct DeviceScreen: View {
    var dot: Color = Palette.green
    var mood: MascotView.Mood = .idle
    let headline: String
    var headlineColor: Color = Palette.text
    var lines: [String] = []
    var hint: String?
    /// What VoiceOver reads for the whole device.
    var spoken: String?

    var body: some View {
        DeviceShell {
            GeometryReader { proxy in
                let d = proxy.size.width
                VStack(spacing: 0) {
                    Circle().fill(dot)
                        .frame(width: max(6, d * 0.022), height: max(6, d * 0.022))
                        .padding(.top, d * 0.12)
                    MascotView(mood: mood)
                        .frame(width: d * 0.44, height: d * 0.44)
                        .padding(.top, d * 0.04)
                    PixelText(headline, scale: 2, color: headlineColor, centered: true)
                        .padding(.top, d * 0.02)
                    ForEach(Array(lines.enumerated()), id: \.offset) { _, line in
                        PixelText(line, scale: 2, color: Palette.dim, centered: true)
                            .padding(.top, 3)
                    }
                    Spacer(minLength: 0)
                    if let hint {
                        PixelText(hint, scale: 2, color: Palette.dim, centered: true)
                            .padding(.bottom, d * 0.11)
                    }
                }
                .padding(.horizontal, d * 0.14)
                .frame(width: d, height: proxy.size.height)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(spoken ?? ([headline] + lines).joined(separator: ". ")))
    }
}
