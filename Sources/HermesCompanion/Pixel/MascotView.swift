import SwiftUI

/// The mascot, pixel for pixel: white 1-bit art at the largest whole-pixel
/// scale that fits, blinking every few seconds like the device.
struct MascotView: View {
    enum Mood: Hashable {
        case idle
        /// Alternates the talk and idle frames, as the device does while speaking.
        case talking
    }

    var mood: Mood = .idle
    var color: Color = Palette.text

    @State private var frame: MascotFrame = .idle
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        MascotCanvas(frame: frame, color: color)
            .aspectRatio(1, contentMode: .fit)
            .task(id: mood) { await animate() }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text("Hermes, a girl with headphones, drawn in white pixels"))
            .accessibilityAddTraits(.isImage)
    }

    private func animate() async {
        frame = .idle
        guard !reduceMotion else { return }
        while !Task.isCancelled {
            switch mood {
            case .idle:
                // The device blinks for two of every 45 frames at 10 fps.
                try? await Task.sleep(for: .milliseconds(4300))
                guard !Task.isCancelled else { return }
                frame = .blink
                try? await Task.sleep(for: .milliseconds(200))
                frame = .idle
            case .talking:
                try? await Task.sleep(for: .milliseconds(200))
                frame = frame == .talk ? .idle : .talk
            }
        }
    }
}

private struct MascotCanvas: View, Equatable {
    let frame: MascotFrame
    let color: Color

    @Environment(\.displayScale) private var displayScale

    var body: some View {
        let scale = displayScale
        Canvas { context, size in
            let side = min(size.width, size.height)
            guard let (bitmap, pixel) = Mascot.pick(frame: frame, side: side, displayScale: scale) else { return }
            let drawn = CGFloat(bitmap.size) * pixel
            // Snap the origin to a device pixel so every art pixel is the same width.
            let x = ((size.width - drawn) / 2 * scale).rounded() / scale
            let y = ((size.height - drawn) / 2 * scale).rounded() / scale
            var path = Path()
            path.addRects(bitmap.runs)
            context.translateBy(x: x, y: y)
            context.scaleBy(x: pixel, y: pixel)
            context.fill(path, with: .color(color), style: FillStyle(antialiased: false))
        }
    }

    nonisolated static func == (lhs: MascotCanvas, rhs: MascotCanvas) -> Bool {
        lhs.frame == rhs.frame && lhs.color == rhs.color
    }
}
