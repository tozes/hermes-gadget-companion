import Foundation

/// One line of console scrollback.
struct ConsoleEntry: Identifiable, Equatable, Sendable {
    enum Kind: Equatable, Sendable {
        /// A line sent to the gadget.
        case command
        case reply
        /// A note from the app, such as a dropped link.
        case note
    }

    let id: Int
    let kind: Kind
    let text: String
    /// Typed in the console, rather than sent by the app's own screens.
    let typed: Bool
    let date: Date
}
