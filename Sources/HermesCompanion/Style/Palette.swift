import SwiftUI

/// The gadget's colours, from the firmware renderer (`ui.cpp`), plus the
/// project site's device shell for the one surface the device doesn't have.
enum Palette {
    static let background = rgb(10, 14, 20)
    static let bar = rgb(24, 31, 42)
    static let text = rgb(232, 238, 242)
    static let dim = rgb(132, 146, 160)
    static let faint = rgb(52, 62, 76)
    static let accent = rgb(242, 179, 61)
    static let green = rgb(61, 214, 140)
    static let blue = rgb(90, 169, 242)
    static let red = rgb(242, 95, 92)
    static let yellow = rgb(242, 201, 76)
    static let greenDim = rgb(28, 92, 62)
    static let blueDim = rgb(52, 96, 140)
    static let accentDim = rgb(110, 82, 30)
    static let redDim = rgb(122, 44, 44)

    /// Dark text on amber, from the site (`--accent-ink`).
    static let accentInk = rgb(26, 18, 3)

    /// The site's brushed-metal device shell (`home.css`).
    static let shellEdge = rgb(120, 128, 138)
    static let shellLight = rgb(102, 113, 122)
    static let shellDark = rgb(34, 41, 48)
    static let shellDeep = rgb(17, 22, 28)
    static let shellSheen = rgb(85, 96, 106)

    private static func rgb(_ r: Double, _ g: Double, _ b: Double) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255)
    }
}
