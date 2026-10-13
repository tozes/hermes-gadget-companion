import SwiftUI

/// The device's top bar (`Ui::draw_top`): a title on the left, then an
/// optional battery and a coloured link dot with a short label on the right.
struct TopBar: View {
    let title: String
    var linkColor: Color?
    var linkLabel: String?
    var battery: (percent: Int, charging: Bool, low: Bool)?
    /// Read by VoiceOver instead of the short pixel labels.
    var spokenState: String?

    var body: some View {
        HStack(spacing: 10) {
            PixelText(title.uppercased(), scale: 2, color: Palette.text)
                .debugMenuTrigger()
                .accessibilityAddTraits(.isHeader)
            Spacer(minLength: 8)
            HStack(spacing: 10) {
                if let battery {
                    HStack(spacing: 6) {
                        BatteryGlyph(percent: battery.percent, charging: battery.charging, low: battery.low, scale: 2)
                        PixelText("\(battery.percent)%", scale: 2, color: battery.low ? Palette.red : Palette.dim)
                    }
                }
                if let linkColor {
                    Circle().fill(linkColor).frame(width: 8, height: 8)
                }
                if let linkLabel {
                    PixelText(linkLabel, scale: 2, color: Palette.dim)
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(Text(spokenState ?? linkLabel ?? ""))
        }
        .padding(.horizontal, 16)
        .frame(minHeight: 44)
        .background(Palette.bar)
    }
}
