import GadgetKit
import SwiftUI

/// One scrollback line: commands after a prompt, replies coloured by their
/// tag (`@ok` green, `@error` red), notes from the app dimmed.
struct ConsoleLine: View {
    let entry: ConsoleEntry

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 6) {
            Text(prefix)
                .foregroundStyle(entry.kind == .command && entry.typed ? Palette.accent : Palette.dim)
                .accessibilityHidden(true)
            Text(entry.text)
                .foregroundStyle(color)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
        }
        .font(.system(.footnote, design: .monospaced))
        .accessibilityElement(children: .combine)
        .accessibilityLabel(Text("\(spokenKind): \(entry.text)"))
    }

    private var prefix: String {
        switch entry.kind {
        case .command: entry.typed ? ">" : "app>"
        case .reply: " "
        case .note: "#"
        }
    }

    private var spokenKind: String {
        switch entry.kind {
        case .command: entry.typed ? "Sent" : "App sent"
        case .reply: "Reply"
        case .note: "Note"
        }
    }

    private var color: Color {
        switch entry.kind {
        case .command: entry.typed ? Palette.text : Palette.dim
        case .note: Palette.dim
        case .reply:
            if entry.text.hasPrefix("@ok") { Palette.green }
            else if entry.text.hasPrefix("@error") { Palette.red }
            else if entry.text.hasPrefix("@") { Palette.text }
            else { Palette.dim }
        }
    }
}
