import SwiftUI

/// A Wi-Fi form field: a pixel label over the input, and the gadget's
/// refusal under it.
struct WifiField<Input: View>: View {
    let label: String
    let error: String?
    let input: Input

    init(_ label: String, error: String?, @ViewBuilder input: () -> Input) {
        self.label = label
        self.error = error
        self.input = input()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            PixelText(label.uppercased(), scale: 2, color: Palette.dim)
                .accessibilityHidden(true)
            input
            if let error {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(Palette.red)
                    .accessibilityLabel(Text("Error: \(error)"))
            }
        }
    }
}
