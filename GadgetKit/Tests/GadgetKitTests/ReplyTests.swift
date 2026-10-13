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
        #expect(Reply.parse(#"@value {"key":"volume","value":"70"}"#) == .value(SettingValue(key: "volume", value: "70")))
    }

    @Test func maskedSecretValue() {
        #expect(Reply.parse(#"@value {"key":"wifi_pass","value":"<set>"}"#) == .value(SettingValue(key: "wifi_pass", value: SettingValue.maskedSecret)))
    }

    @Test func ok() {
        #expect(Reply.parse("@ok volume") == .ok("volume"))
    }

    @Test func errors() {
        let validation = Reply.parse("@error screen_timeout must be 0..3600 seconds")
        #expect(validation == .error("screen_timeout must be 0..3600 seconds"))
        #expect(validation.isError)
        #expect(validation.failureMessage == "screen_timeout must be 0..3600 seconds")
        #expect(Reply.parse("@error device busy") == .error("device busy"))
    }

    @Test func phone() {
        #expect(Reply.parse(#"@phone {"paired":1,"connected":true,"pairing":false}"#) == .phone(PhoneState(paired: 1, connected: true, pairing: false)))
    }

    @Test func unknownJSONFieldsAreIgnored() {
        let reply = Reply.parse(#"@phone {"paired":2,"connected":false,"pairing":true,"slots":3,"extra":{"a":[1,2]}}"#)
        #expect(reply == .phone(PhoneState(paired: 2, connected: false, pairing: true)))
        let value = Reply.parse(#"@value {"key":"name","value":"Nox","source":"nvs"}"#)
        #expect(value == .value(SettingValue(key: "name", value: "Nox")))
    }

    @Test func trailingNewlineAndSpacesAreTrimmed() {
        #expect(Reply.parse("  @ok volume \r\n") == .ok("volume"))
    }

    @Test func helpAndDiagAreKeptAsText() {
        let help = "@help commands: status | diag [log] | get <key>"
        #expect(Reply.parse(help) == .other(tag: "help", body: "commands: status | diag [log] | get <key>"))
        let diag = #"@diag {"build":"0.2.0","heap_free":182344}"#
        #expect(Reply.parse(diag) == .other(tag: "diag", body: #"{"build":"0.2.0","heap_free":182344}"#))
    }

    @Test func plainTextIsKept() {
        let reply = Reply.parse("hello there")
        #expect(reply == .other(tag: "", body: "hello there"))
        #expect(reply.failureMessage == "Unexpected reply: hello there")
        #expect(!reply.isError)
    }

    @Test func logDumpIsKeptWhole() {
        let log = "I (1203) hg: boot\nI (1450) hg: phone app ready\n@log end"
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
