import Foundation

/// The reply samples in `fixtures/replies/` at the repository root, shared
/// with the Android port's tests (see fixtures/README.md). A SwiftPM package
/// can't bundle resources from outside its directory, so the files are found
/// from this source file's path.
enum Fixture {
    static let replies = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()  // GadgetKitTests
        .deletingLastPathComponent()  // Tests
        .deletingLastPathComponent()  // GadgetKit
        .deletingLastPathComponent()  // the repository
        .appendingPathComponent("fixtures/replies")

    /// A reply exactly as the gadget sends it, without the NUL: `reply("error-busy")`.
    static func reply(_ name: String) -> String {
        let url = replies.appendingPathComponent(name + ".txt")
        guard let data = try? Data(contentsOf: url) else {
            fatalError("missing fixture \(url.path)")
        }
        return String(decoding: data, as: UTF8.self)
    }
}
