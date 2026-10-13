import Testing
@testable import GadgetKit

struct ReplyTests {
    @Test func status() {
        guard case .status(let status) = Reply.parse(statusSample) else {
            Issue.record("not a status")
            return
        }
        #expect(status.name == "Nox")
        #expect(status.phase == .noNetwork)
        #expect(status.phone == PhoneState(paired: 1, connected: true, pairing: false))
    }

    @Test func value() {
        #expect(Reply.parse(Fixture.reply("value-volume")) == .value(SettingValue(key: "volume", value: "70")))
    }

    @Test func maskedSecretValue() {
        #expect(Reply.parse(Fixture.reply("value-secret")) == .value(SettingValue(key: "wifi_pass", value: SettingValue.maskedSecret)))
    }

    @Test func ok() {
        #expect(Reply.parse(Fixture.reply("ok-volume")) == .ok("volume"))
    }

    @Test func errors() {
        let validation = Reply.parse(Fixture.reply("error-screen-timeout"))
        #expect(validation == .error("screen_timeout must be 0..3600 seconds"))
        #expect(validation.isError)
        #expect(validation.failureMessage == "screen_timeout must be 0..3600 seconds")
        #expect(Reply.parse(Fixture.reply("error-busy")) == .error("device busy"))
    }

    @Test func phone() {
        #expect(Reply.parse(Fixture.reply("phone")) == .phone(PhoneState(paired: 1, connected: true, pairing: false)))
    }

    @Test func unknownJSONFieldsAreIgnored() {
        let reply = Reply.parse(Fixture.reply("phone-extra-fields"))
        #expect(reply == .phone(PhoneState(paired: 2, connected: false, pairing: true)))
        let value = Reply.parse(Fixture.reply("value-extra-fields"))
        #expect(value == .value(SettingValue(key: "name", value: "Nox")))
    }

    @Test func trailingNewlineAndSpacesAreTrimmed() {
        #expect(Reply.parse("  @ok volume \r\n") == .ok("volume"))
    }

    @Test func helpAndDiagAreKeptAsText() {
        let help = Fixture.reply("help")
        #expect(Reply.parse(help) == .other(tag: "help", body: String(help.dropFirst("@help ".count))))
        #expect(Reply.parse(help).failureMessage == "Unexpected reply: @help")
        let diag = Fixture.reply("diag")
        #expect(Reply.parse(diag) == .other(tag: "diag", body: #"{"build":"0.2.0","heap_free":182344}"#))
    }

    @Test func plainTextIsKept() {
        let reply = Reply.parse("hello there")
        #expect(reply == .other(tag: "", body: "hello there"))
        #expect(reply.failureMessage == "Unexpected reply: hello there")
        #expect(!reply.isError)
    }

    @Test func logDumpIsKeptWhole() {
        let log = Fixture.reply("diag-log")
        #expect(log.hasSuffix("\n@log end"))
        #expect(Reply.parse(log) == .other(tag: "", body: log))
    }

    @Test(arguments: [
        "@status {not json",
        #"@status ["online"]"#,
        "@value {}",
        #"@value {"key":"volume"}"#,
        #"@phone {"paired":"one"}"#,
        "@status",
    ])
    func malformedJSONIsKeptAsText(text: String) {
        guard case .other(let tag, _) = Reply.parse(text) else {
            Issue.record("decoded \(text)")
            return
        }
        #expect(["status", "value", "phone"].contains(tag))
        #expect(Reply.parse(text).failureMessage == "Unexpected reply: @\(tag)")
    }

    @Test func emptyOkAndError() {
        #expect(Reply.parse("@ok") == .ok(""))
        #expect(Reply.parse("@error") == .error(""))
    }
}
