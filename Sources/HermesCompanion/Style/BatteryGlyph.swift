import SwiftUI

/// The firmware's battery icon (`Ui::draw_battery`): an outline with a
/// terminal nub, filled to the level; green while charging, red when low.
/// Sizes are in font pixels times `scale`.
struct BatteryGlyph: View {
    let percent: Int
    var charging = false
    var low = false
    var scale: CGFloat = 2

    var body: some View {
        let s = scale
        let width = 9 * s, height = 5 * s + 1, t = max(1, s / 2)
        let inner = width - 4 * t
        let clamped = min(max(percent, 0), 100)
        let level = max(clamped > 0 ? 1 : 0, inner * CGFloat(clamped) / 100)
        let fill = charging ? Palette.green : low ? Palette.red : Palette.text
        HStack(spacing: 0) {
            ZStack(alignment: .leading) {
                Rectangle().fill(Palette.dim)
                Rectangle().fill(Palette.bar).padding(t)
                Rectangle().fill(fill).frame(width: level).padding(.leading, 2 * t).padding(.vertical, 2 * t)
            }
            .frame(width: width, height: height)
            Rectangle().fill(Palette.dim).frame(width: s, height: 2 * s)
        }
        .accessibilityHidden(true)
    }
}
