import Foundation
import Testing
@testable import GadgetKit

struct ReplyAssemblerTests {
    private func bytes(_ text: String, nul: Bool = true) -> Data {
        Data(text.utf8) + (nul ? Data([0]) : Data())
    }

    @Test func wholeReplyInOneChunk() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(bytes("@ok volume")) == ["@ok volume"])
        #expect(!assembler.hasPartialReply)
    }

    @Test func piecesAreJoinedAtTheNul() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(bytes("@status {\"name\":", nul: false)) == [])
        #expect(assembler.hasPartialReply)
        #expect(assembler.append(bytes("\"Nox\"", nul: false)) == [])
        #expect(assembler.append(bytes("}")) == [#"@status {"name":"Nox"}"#])
        #expect(!assembler.hasPartialReply)
    }

    @Test func endOfOneReplyAndStartOfTheNext() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(bytes("@ok vol", nul: false)) == [])
        #expect(assembler.append(bytes("ume") + bytes("@error dev", nul: false)) == ["@ok volume"])
        #expect(assembler.append(bytes("ice busy")) == ["@error device busy"])
    }

    @Test func severalRepliesInOneChunk() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(bytes("@ok a") + bytes("@ok b") + bytes("@ok c")) == ["@ok a", "@ok b", "@ok c"])
    }

    @Test func emptyReply() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(Data([0])) == [""])
        #expect(assembler.append(Data([0, 0])) == ["", ""])
    }

    @Test func emptyChunk() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(Data()) == [])
        #expect(!assembler.hasPartialReply)
    }

    @Test func multiLineReply() {
        var assembler = ReplyAssembler()
        let log = Fixture.reply("diag-log")
        let data = bytes(log)
        #expect(assembler.append(data.prefix(20)) == [])
        #expect(assembler.append(data.dropFirst(20)) == [log])
    }

    @Test func utf8SplitAcrossChunks() {
        var assembler = ReplyAssembler()
        let reply = "@value {\"key\":\"name\",\"value\":\"Café ☕\"}"
        let data = bytes(reply)
        // Split inside "é" (2 bytes) and inside "☕" (3 bytes).
        let e = data.firstIndex(of: 0xC3)!
        let cup = data.firstIndex(of: 0xE2)!
        let chunks = [data[..<(e + 1)], data[(e + 1)..<(cup + 2)], data[(cup + 2)...]]
        var replies: [String] = []
        for chunk in chunks { replies += assembler.append(Data(chunk)) }
        #expect(replies == [reply])
    }

    @Test func resetDropsAPartialReply() {
        var assembler = ReplyAssembler()
        #expect(assembler.append(bytes("@status {\"na", nul: false)) == [])
        assembler.reset()
        #expect(!assembler.hasPartialReply)
        #expect(assembler.append(bytes("@ok volume")) == ["@ok volume"])
    }
}
