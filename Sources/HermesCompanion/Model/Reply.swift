import Foundation

/// One reply from the gadget's console. Machine-readable replies start with
/// `@` and a tag; JSON tags are decoded, anything else is kept as text.
enum Reply: Equatable, Sendable {
    case status(GadgetStatus)
    case value(SettingValue)
    case ok(String)
    case error(String)
    case phone(PhoneState)
    /// `@help`, `@diag`, a log dump, or JSON that didn't decode.
    case other(tag: String, body: String)

    static func parse(_ text: String) -> Reply {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.hasPrefix("@") else {
            return .other(tag: "", body: trimmed)
        }
        let head = trimmed.dropFirst()
        let space = head.firstIndex(of: " ") ?? head.endIndex
        let tag = String(head[..<space])
        let body = head[space...].trimmingCharacters(in: .whitespaces)
        switch tag {
        case "ok": return .ok(body)
        case "error": return .error(body)
        case "status": return decode(body).map(Reply.status) ?? .other(tag: tag, body: body)
        case "value": return decode(body).map(Reply.value) ?? .other(tag: tag, body: body)
        case "phone": return decode(body).map(Reply.phone) ?? .other(tag: tag, body: body)
        default: return .other(tag: tag, body: body)
        }
    }

    var isError: Bool {
        if case .error = self { return true }
        return false
    }

    /// The message of an `@error`, or a description of a reply that wasn't expected.
    var failureMessage: String? {
        switch self {
        case .error(let message): message
        case .other(let tag, let body): tag.isEmpty ? "Unexpected reply: \(body)" : "Unexpected reply: @\(tag)"
        default: nil
        }
    }

    private static func decode<T: Decodable>(_ json: String) -> T? {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try? decoder.decode(T.self, from: Data(json.utf8))
    }
}
