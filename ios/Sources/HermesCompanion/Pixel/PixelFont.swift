import SwiftUI

/// The gadget's 5x7 bitmap font, ported from the firmware (`font5x7.cpp`).
///
/// Metrics are in font pixels; multiply by the integer scale to get points.
enum PixelFont {
    static let glyphWidth = 5
    static let glyphHeight = 7
    /// Advance and line pitch: the glyph plus spacing.
    static let cellWidth = 6
    static let cellHeight = 9

    /// Seven row masks per glyph; bit 4 is the leftmost pixel.
    private static let glyphs: [[UInt8]] = PixelFontGlyphs.table.map { entry in
        entry.split(separator: " ").map { row in
            row.reduce(UInt8(0)) { ($0 << 1) | ($1 == "#" ? 1 : 0) }
        }
    }

    /// Replaces typographic characters with the ASCII the font has.
    static func normalize(_ text: String) -> String {
        var out = ""
        for ch in text {
            switch ch {
            case "\u{2018}", "\u{2019}": out.append("'")
            case "\u{201C}", "\u{201D}": out.append("\"")
            case "\u{2013}", "\u{2014}", "\u{2212}": out.append("-")
            case "\u{2026}": out.append("...")
            case "\u{2192}": out.append(">")
            case "\u{00B7}", "\u{2022}": out.append(".")
            case "\u{00A0}": out.append(" ")
            default: out.append(ch)
            }
        }
        return out
    }

    /// Whether every character has a glyph (printable ASCII).
    static func canRender(_ text: String) -> Bool {
        text.unicodeScalars.allSatisfy { $0.value >= 0x20 && $0.value <= 0x7E }
    }

    /// Width in font pixels of `count` characters on one line.
    static func width(columns count: Int) -> Int {
        count > 0 ? count * cellWidth - 1 : 0
    }

    /// Height in font pixels of `lines` lines.
    static func height(lines: Int) -> Int {
        lines > 0 ? lines * cellHeight - (cellHeight - glyphHeight) : 0
    }

    /// How many characters fit in `points` at `scale`.
    static func columns(fitting points: CGFloat, scale: CGFloat) -> Int {
        max(1, Int((points + scale) / (CGFloat(cellWidth) * scale)))
    }

    /// Shortens a line to `columns`, ending in ".." like the firmware's `fit()`.
    static func fit(_ text: String, columns: Int) -> String {
        guard text.count > columns else { return text }
        guard columns > 2 else { return String(text.prefix(columns)) }
        return String(text.prefix(columns - 2)) + ".."
    }

    /// Greedy word wrap to `columns`; words longer than a line are split.
    static func wrap(_ text: String, columns: Int) -> [String] {
        var lines: [String] = []
        for paragraph in text.split(separator: "\n", omittingEmptySubsequences: false) {
            var line = ""
            for word in paragraph.split(separator: " ") {
                var word = Substring(word)
                while word.count > columns {
                    if !line.isEmpty { lines.append(line); line = "" }
                    lines.append(String(word.prefix(columns)))
                    word = word.dropFirst(columns)
                }
                if line.isEmpty {
                    line = String(word)
                } else if line.count + 1 + word.count <= columns {
                    line += " " + word
                } else {
                    lines.append(line)
                    line = String(word)
                }
            }
            lines.append(line)
        }
        return lines
    }

    /// The pixels of `lines` as a path in font-pixel units, each line aligned
    /// within `width` pixels.
    static func path(lines: [String], width: Int, centered: Bool) -> Path {
        var path = Path()
        for (row, line) in lines.enumerated() {
            let lineWidth = self.width(columns: line.count)
            var x = centered ? (width - lineWidth) / 2 : 0
            let y = row * cellHeight
            for scalar in line.unicodeScalars {
                let index = (scalar.value >= 0x20 && scalar.value <= 0x7E) ? Int(scalar.value) - 0x20 : Int(UInt8(ascii: "?")) - 0x20
                addGlyph(glyphs[index], x: x, y: y, to: &path)
                x += cellWidth
            }
        }
        return path
    }

    private static func addGlyph(_ rows: [UInt8], x: Int, y: Int, to path: inout Path) {
        for (dy, mask) in rows.enumerated() where mask != 0 {
            var col = 0
            while col < glyphWidth {
                guard mask & (0x10 >> UInt8(col)) != 0 else { col += 1; continue }
                let start = col
                while col < glyphWidth, mask & (0x10 >> UInt8(col)) != 0 { col += 1 }
                path.addRect(CGRect(x: x + start, y: y + dy, width: col - start, height: 1))
            }
        }
    }
}
