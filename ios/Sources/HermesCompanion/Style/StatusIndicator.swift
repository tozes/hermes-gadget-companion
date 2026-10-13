import SwiftUI

/// The round indicator beside a header's headline, as the firmware draws it
/// (`Ui::draw_indicator`): a spinner while busy, a ring while pairing, a
/// pulsing dot when ready, a red "!" on errors.
struct StatusIndicator: View {
    enum Kind: Equatable {
        case spinner(Color)
        case ring(Color)
        case pulse(Color)
        case dot(Color)
        case alert
        case question
    }

    let kind: Kind
    var size: CGFloat = 22

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        Group {
            if animated && !reduceMotion {
                TimelineView(.periodic(from: .now, by: 0.1)) { timeline in
                    shape(frame: Int(timeline.date.timeIntervalSinceReferenceDate * 10))
                }
            } else {
                shape(frame: 0)
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private var animated: Bool {
        switch kind {
        case .spinner, .pulse: true
        default: false
        }
    }

    @ViewBuilder
    private func shape(frame: Int) -> some View {
        let r = size / 2
        switch kind {
        case .spinner(let color):
            let dot = max(2, r / 5)
            ZStack {
                ForEach(0..<8, id: \.self) { i in
                    let age = ((frame % 8) - i + 8) % 8
                    let angle = Double(i) * .pi / 4
                    Circle()
                        .fill(age == 0 ? color : age < 3 ? Palette.dim : Palette.faint)
                        .frame(width: dot * 2, height: dot * 2)
                        .offset(x: (r - dot) * sin(angle), y: -(r - dot) * cos(angle))
                }
            }
        case .ring(let color):
            ZStack {
                Circle().strokeBorder(color, lineWidth: max(2, size / 9))
                Circle().fill(color).frame(width: size / 3, height: size / 3)
            }
        case .pulse(let color):
            // A triangle wave over 24 frames, like the firmware's tri().
            let p = frame % 24
            let tri = Double(p < 12 ? p : 24 - p)
            Circle().fill(color).frame(width: size * (8 + tri / 3) / 12, height: size * (8 + tri / 3) / 12)
        case .dot(let color):
            Circle().fill(color).frame(width: size * 2 / 3, height: size * 2 / 3)
        case .alert:
            badge("!", fill: Palette.red)
        case .question:
            badge("?", fill: Palette.accent)
        }
    }

    private func badge(_ mark: String, fill: Color) -> some View {
        ZStack {
            Circle().fill(fill)
            PixelText(mark, scale: max(1, Int(size / 10)), color: Palette.background)
        }
    }
}
