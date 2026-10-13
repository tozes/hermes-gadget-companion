import Foundation

/// The gadget's Bluetooth service (SDK `docs/protocol.md`, "Phone app over Bluetooth").
enum GadgetService {
    static let service = "DC530001-7A2A-400F-B8F6-E87185576192"
    /// Write with response: one console line. Needs a paired (encrypted) link.
    static let command = "DC530002-7A2A-400F-B8F6-E87185576192"
    /// Notify: the reply, in pieces, ending with a 0x00 byte.
    static let reply = "DC530003-7A2A-400F-B8F6-E87185576192"
}

/// Joins reply notifications into whole replies: each reply ends with one NUL
/// byte, and one notification may carry the end of a reply and the start of the
/// next.
struct ReplyAssembler: Sendable {
    private var buffer = Data()

    /// Adds a notification's bytes; returns the replies it completed, in order.
    mutating func append(_ chunk: Data) -> [String] {
        var replies: [String] = []
        for byte in chunk {
            if byte == 0 {
                replies.append(String(decoding: buffer, as: UTF8.self))
                buffer.removeAll(keepingCapacity: true)
            } else {
                buffer.append(byte)
            }
        }
        return replies
    }

    /// Drops a partial reply, e.g. when the link changes.
    mutating func reset() {
        buffer.removeAll()
    }

    var hasPartialReply: Bool { !buffer.isEmpty }
}
