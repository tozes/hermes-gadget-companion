import SwiftUI

/// Text in the gadget's 5x7 pixel font, drawn nearest-neighbour at an integer
/// scale (points per font pixel).
///
/// Use it for headlines, labels and hints. Long text, inputs and anything
/// that must reflow belong in the system font. The scale steps up at the
/// accessibility text sizes, and VoiceOver reads the original string.
/// Text the font can't draw (non-ASCII names) falls back to the system
/// monospaced font.
struct PixelText: View {
    private let text: String
    private let scale: Int
    private let color: Color
    private let centered: Bool
    private let wraps: Bool

    @Environment(\.dynamicTypeSize) private var typeSize

    init(_ text: String, scale: Int = 2, color: Color = Palette.text, centered: Bool = false, wraps: Bool = false) {
        self.text = text
        self.scale = scale
        self.color = color
        self.centered = centered
        self.wraps = wraps
    }

    private var effectiveScale: Int {
        if typeSize >= .accessibility3 { return scale + 2 }
        if typeSize >= .accessibility1 { return scale + 1 }
        return scale
    }

    var body: some View {
        let glyphs = PixelFont.normalize(text)
        Group {
            if PixelFont.canRender(glyphs) {
                PixelTextLayout(text: glyphs, scale: CGFloat(effectiveScale), wraps: wraps) {
                    PixelTextCanvas(text: glyphs, scale: CGFloat(effectiveScale), color: color, centered: centered, wraps: wraps)
                }
            } else {
                Text(text)
                    .font(.system(size: CGFloat(7 * effectiveScale), weight: .semibold, design: .monospaced))
                    .foregroundStyle(color)
                    .multilineTextAlignment(centered ? .center : .leading)
                    .lineLimit(wraps ? nil : 1)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(text))
    }
}

/// Sizes the canvas to the wrapped (or fitted) text for the offered width.
private struct PixelTextLayout: Layout {
    let text: String
    let scale: CGFloat
    let wraps: Bool

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let lines = PixelTextCanvas.lines(text, width: proposal.width, scale: scale, wraps: wraps)
        let columns = lines.map(\.count).max() ?? 0
        return CGSize(
            width: CGFloat(PixelFont.width(columns: columns)) * scale,
            height: CGFloat(PixelFont.height(lines: lines.count)) * scale
        )
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        subviews.first?.place(at: bounds.origin, proposal: ProposedViewSize(bounds.size))
    }
}

private struct PixelTextCanvas: View {
    let text: String
    let scale: CGFloat
    let color: Color
    let centered: Bool
    let wraps: Bool

    nonisolated static func lines(_ text: String, width: CGFloat?, scale: CGFloat, wraps: Bool) -> [String] {
        guard let width, width.isFinite else {
            return text.split(separator: "\n", omittingEmptySubsequences: false).map(String.init)
        }
        let columns = PixelFont.columns(fitting: width, scale: scale)
        if wraps { return PixelFont.wrap(text, columns: columns) }
        return text.split(separator: "\n", omittingEmptySubsequences: false).map { PixelFont.fit(String($0), columns: columns) }
    }

    var body: some View {
        Canvas { context, size in
            let lines = Self.lines(text, width: size.width, scale: scale, wraps: wraps)
            let width = Int((size.width / scale).rounded(.down))
            let path = PixelFont.path(lines: lines, width: width, centered: centered)
            context.scaleBy(x: scale, y: scale)
            context.fill(path, with: .color(color), style: FillStyle(antialiased: false))
        }
    }
}
