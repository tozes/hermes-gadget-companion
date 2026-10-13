import SwiftUI

/// A raw terminal for developers: type a console line, see the reply.
struct ConsoleView: View {
    @Environment(GadgetStore.self) private var store

    @State private var line = ""
    @State private var showAppLines = false
    @FocusState private var focused: Bool

    private static let quickCommands = ["status", "phone", "get volume", "diag", "help"]

    private var entries: [ConsoleEntry] {
        showAppLines ? store.console : store.console.filter(\.typed)
    }

    var body: some View {
        VStack(spacing: 0) {
            scrollback
            quickBar
            inputBar
        }
        .background(Palette.background.ignoresSafeArea())
        .gadgetNavigation(title: "Console")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Toggle("Show the app's own commands", isOn: $showAppLines)
                    Button("Clear", role: .destructive) { store.clearConsole() }
                } label: {
                    PixelText("...", scale: 2, color: Palette.accent)
                        .frame(minWidth: 44, minHeight: 44)
                }
                .accessibilityLabel(Text("Console options"))
            }
        }
        .onAppear { focused = true }
    }

    // MARK: Scrollback

    private var scrollback: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 4) {
                    if entries.isEmpty {
                        Text("Type a command such as status or get volume. Replies start with @.")
                            .font(.system(.footnote, design: .monospaced))
                            .foregroundStyle(Palette.dim)
                    }
                    ForEach(entries) { entry in
                        ConsoleLine(entry: entry).id(entry.id)
                    }
                }
                .padding(16)
            }
            .defaultScrollAnchor(.bottom)
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: entries.last?.id) { _, last in
                guard let last else { return }
                withAnimation(.easeOut(duration: 0.15)) { proxy.scrollTo(last, anchor: .bottom) }
            }
        }
    }

    // MARK: Input

    private var quickBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Self.quickCommands, id: \.self) { command in
                    Button { send(command) } label: {
                        Text(command)
                            .font(.system(.footnote, design: .monospaced))
                            .foregroundStyle(Palette.text)
                            .padding(.horizontal, 12)
                            .frame(minHeight: 44)
                            .background(Palette.bar, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .accessibilityHint(Text("Sends \(command)"))
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .disabled(store.connectionLost)
    }

    private var inputBar: some View {
        HStack(spacing: 10) {
            PixelText("gadget>", scale: 2, color: Palette.accent)
                .accessibilityHidden(true)
            TextField("Command", text: $line, prompt: placeholder("command"))
                .font(.body.monospaced())
                .foregroundStyle(Palette.text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.send)
                .focused($focused)
                .onSubmit { send(line) }
                .frame(minHeight: 44)
                .accessibilityLabel(Text("Console command"))
            Button { send(line) } label: {
                PixelText("Send", scale: 2, color: line.isEmpty ? Palette.dim : Palette.accent)
                    .frame(minWidth: 44, minHeight: 44)
            }
            .disabled(line.trimmingCharacters(in: .whitespaces).isEmpty)
            .accessibilityLabel(Text("Send"))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
        .background(Palette.bar.ignoresSafeArea(edges: .bottom))
        .disabled(store.connectionLost)
    }

    private func send(_ text: String) {
        let command = text.trimmingCharacters(in: .whitespaces)
        guard !command.isEmpty else { return }
        line = ""
        focused = true
        Task { await store.send(command, typed: true) }
    }
}
