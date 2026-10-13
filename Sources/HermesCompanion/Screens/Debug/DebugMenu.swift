import Observation
import SwiftUI

/// Whether the hidden debug menu is showing.
@MainActor @Observable
final class DebugMenu {
    var isPresented = false
}

extension View {
    /// Opens the debug menu on a one-second long press, or with the
    /// VoiceOver action "Debug menu". Attached to every screen's title.
    func debugMenuTrigger() -> some View {
        modifier(DebugMenuTrigger())
    }
}

private struct DebugMenuTrigger: ViewModifier {
    @Environment(DebugMenu.self) private var menu: DebugMenu?

    func body(content: Content) -> some View {
        content
            .onLongPressGesture(minimumDuration: 1) { menu?.isPresented = true }
            .accessibilityAction(named: Text("Debug menu")) { menu?.isPresented = true }
    }
}
