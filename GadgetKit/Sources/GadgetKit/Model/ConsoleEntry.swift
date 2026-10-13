import Foundation

/// One line of console scrollback.
public struct ConsoleEntry: Identifiable, Equatable, Sendable {
    public enum Kind: Equatable, Sendable {
        /// A line sent to the gadget.
        case command
        case reply
        /// A note from the app, such as a dropped link.
        case note
    }

    public let id: Int
    public let kind: Kind
    public let text: String
    /// Typed in the console, rather than sent by the app's own screens.
    public let typed: Bool
    let date: Date
}
