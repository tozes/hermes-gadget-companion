import SwiftUI

/// Waiting for Hermes: the command to run on the Hermes host, with the
/// gadget's pairing code, as the gadget's own pairing screen shows it.
struct ApprovalPanel: View {
    let name: String
    let code: String?

    var body: some View {
        Panel {
            VStack(alignment: .leading, spacing: 10) {
                PixelText(code.map { "Pairing code \($0)" } ?? "Waiting for approval", scale: 2, color: Palette.accent, wraps: true)
                Text("On the Hermes host, run:")
                    .font(.subheadline)
                    .foregroundStyle(Palette.dim)
                Text("hermes pairing approve gadget \(code ?? "<code>")")
                    .font(.callout.monospaced())
                    .foregroundStyle(Palette.text)
                    .textSelection(.enabled)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.background, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                Text(code == nil
                     ? "The code is on \(name)'s screen."
                     : "\(name) moves on by itself once it's approved. Pull down to check.")
                    .font(.footnote)
                    .foregroundStyle(Palette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
        }
    }
}
