import SwiftUI

/// A Wi-Fi wedge drawn like `BatteryGlyph`: the same dim outline and dark
/// inside, filled when the gadget is on Wi-Fi and left empty when it isn't.
/// Sizes are in font pixels times `scale`; it's as tall as the battery.
struct WifiGlyph: View {
    let connected: Bool
    var scale: CGFloat = 2

    var body: some View {
        let s = scale
        let width = 9 * s, height = 5 * s + 1, t = max(1, s / 2)
        Canvas { context, _ in
            // A quarter circle opening upwards from the bottom middle, like the
            // Wi-Fi fan. Each layer sits inside the last, as in the battery:
            // outline, dark inside, then the fill.
            let apex = CGPoint(x: width / 2, y: height)
            let radius = height
            context.fill(wedge(apex: apex, radius: radius), with: .color(Palette.dim))
            context.fill(wedge(apex: CGPoint(x: apex.x, y: apex.y - t * 1.4), radius: radius - 2.4 * t),
                         with: .color(Palette.bar))
            if connected {
                context.fill(wedge(apex: CGPoint(x: apex.x, y: apex.y - t * 2.8), radius: radius - 4.8 * t),
                             with: .color(Palette.text))
            }
        }
        .frame(width: width, height: height)
        .accessibilityHidden(true)
    }

    private func wedge(apex: CGPoint, radius: CGFloat) -> Path {
        var path = Path()
        path.move(to: apex)
        path.addArc(center: apex, radius: max(radius, 0), startAngle: .degrees(-135), endAngle: .degrees(-45),
                    clockwise: false)
        path.closeSubpath()
        return path
    }
}
