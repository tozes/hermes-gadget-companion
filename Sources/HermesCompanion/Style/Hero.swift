import SwiftUI

/// The device's hero layout (`Ui::draw_hero`): the mascot, a headline in
/// the state's colour, and a dim detail underneath, all centred.
struct Hero: View {
    var mood: MascotView.Mood = .idle
    var mascotSide: CGFloat = 168
    var indicator: StatusIndicator.Kind?
    let headline: String
    var color: Color = Palette.text
    var detail: String?

    var body: some View {
        VStack(spacing: 14) {
            MascotView(mood: mood)
                .frame(width: mascotSide, height: mascotSide)
            HStack(spacing: 10) {
                if let indicator {
                    StatusIndicator(kind: indicator, size: 18)
                }
                PixelText(headline, scale: 3, color: color, centered: true, wraps: true)
                    .accessibilityAddTraits(.isHeader)
            }
            if let detail {
                Text(detail)
                    .font(.body)
                    .foregroundStyle(Palette.dim)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity)
    }
}
