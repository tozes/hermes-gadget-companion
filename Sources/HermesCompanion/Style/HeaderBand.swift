import SwiftUI

/// The header band: status indicator and headline, with an optional dim detail.
struct HeaderBand: View {
    let indicator: StatusIndicator.Kind
    let headline: String
    var color: Color = Palette.text
    var detail: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                StatusIndicator(kind: indicator, size: 22)
                PixelText(headline, scale: 3, color: color, wraps: true)
                    .accessibilityAddTraits(.isHeader)
            }
            if let detail {
                Text(detail)
                    .font(.body)
                    .foregroundStyle(Palette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
