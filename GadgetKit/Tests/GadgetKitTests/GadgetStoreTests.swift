import Foundation
import Testing
@testable import GadgetKit

private let failures: [LinkFailure] = [.pairingRejected, .pairingTimedOut, .pairingRemoved, .unreachable, .connectionLost]

@MainActor
struct GadgetStoreTests {
    let link = FakeGadgetLink()

    /// A store connected to Nox, with the first `status` answered; other lines get `@ok`.
    private func connectedNox() async -> GadgetStore {
        link.respond = { line in line == "status" ? statusSample : "@ok" }
        let store = await connectedStore(link)
        #expect(store.status?.name == "Nox")
        return store
    }

    // MARK: Scanning

    @Test func scanStartsOnceTheRadioIsOn() {
        let link = FakeGadgetLink(radio: .unknown)
        let store = GadgetStore(link: link)
        store.startScan()
        #expect(!store.isScanning)
        #expect(!link.isScanning)
        link.emit(.radio(.poweredOn))
        #expect(store.radio == .poweredOn)
        #expect(store.isScanning)
        #expect(link.isScanning)
    }

    @Test func stopScan() {
        let store = GadgetStore(link: link)
        store.startScan()
        store.stopScan()
        #expect(!store.isScanning)
        #expect(!link.isScanning)
        #expect(!store.scanEnded)
    }

    @Test func scanEndsAfterItsDuration() async {
        let store = GadgetStore(link: link, scanDuration: .milliseconds(20))
        store.startScan()
        #expect(store.isScanning)
        #expect(await eventually { store.scanEnded })
        #expect(!store.isScanning)
        #expect(!link.isScanning)
        // A radio coming back on doesn't restart a scan that ended by itself.
        link.emit(.radio(.poweredOn))
        #expect(!store.isScanning)
    }

    @Test func scanStoppedEarlyDoesNotReportEnded() async throws {
        let store = GadgetStore(link: link, scanDuration: .milliseconds(20))
        store.startScan()
        store.stopScan()
        try await Task.sleep(for: .milliseconds(60))
        #expect(!store.scanEnded)
    }

    @Test func defaultScanDuration() {
        #expect(GadgetStore(link: link).scanDuration == .seconds(20))
    }

    @Test func discoveryUpdatesByID() {
        let store = GadgetStore(link: link)
        store.startScan()
        link.emit(.discovered(nox))
        link.emit(.discovered(kitchen))
        var closer = nox
        closer.rssi = -40
        link.emit(.discovered(closer))
        #expect(store.found == [closer, kitchen])
        #expect(store.found.first?.signalLevel == 4)
    }

    @Test func discoveryIsIgnoredWhenNotScanning() {
        let store = GadgetStore(link: link)
        link.emit(.discovered(nox))
        #expect(store.found.isEmpty)
    }

    @Test func radioOffEndsTheScanAndClearsTheList() {
        let store = GadgetStore(link: link)
        store.startScan()
        link.emit(.discovered(nox))
        link.emit(.radio(.poweredOff))
        #expect(!store.isScanning)
        #expect(!link.isScanning)
        #expect(store.found.isEmpty)
        // The scan picks up again when the radio does.
        link.emit(.radio(.poweredOn))
        #expect(store.isScanning)
    }

    // MARK: Connecting

    @Test func connectPairAndConnect() async {
        link.respond = { line in line == "status" ? statusSample : "@ok" }
        let store = GadgetStore(link: link)
        store.startScan()
        store.connect(to: nox)
        #expect(!link.isScanning)
        #expect(link.target == nox)
        #expect(store.stage == .pairing)
        #expect(store.pairing == .connecting)
        #expect(store.gadget == nox)

        link.emit(.connecting)
        #expect(store.pairing == .connecting)
        link.emit(.awaitingPasskey)
        #expect(store.pairing == .awaitingPasskey)
        #expect(store.stage == .pairing)

        link.emit(.connected)
        #expect(store.stage == .connected)
        #expect(await eventually { store.status != nil })
        #expect(link.sent == ["status"])
        #expect(store.status?.name == "Nox")
        #expect(store.statusDate != nil)
        #expect(store.console.contains { $0.kind == .note && $0.text == "Connected to Nox" })
    }

    @Test(arguments: failures)
    func failureWhilePairing(failure: LinkFailure) {
        let store = GadgetStore(link: link)
        store.connect(to: nox)
        link.emit(.awaitingPasskey)
        link.emit(.failed(failure))
        #expect(store.stage == .pairing)
        #expect(store.pairing == .failed(failure))
        #expect(!store.connectionLost)
    }

    @Test(arguments: failures)
    func disconnectWhilePairing(failure: LinkFailure) {
        let store = GadgetStore(link: link)
        store.connect(to: nox)
        link.emit(.disconnected(failure))
        #expect(store.pairing == .failed(failure))
    }

    @Test func retryAfterAFailure() {
        let store = GadgetStore(link: link)
        store.connect(to: nox)
        link.emit(.failed(.pairingRejected))
        store.retryPairing()
        #expect(store.pairing == .connecting)
        #expect(link.target == nox)
    }

    @Test func backToFinding() {
        let store = GadgetStore(link: link)
        store.connect(to: nox)
        store.backToFinding()
        #expect(store.stage == .finding)
        #expect(store.gadget == nil)
        #expect(link.target == nil)
        #expect(store.isScanning)
    }

    @Test func linkDroppedWhileConnected() async {
        let store = await connectedNox()
        link.emit(.disconnected(.connectionLost))
        #expect(store.connectionLost)
        #expect(store.stage == .connected)
        #expect(store.console.last?.text == "Connection lost")
        // Nothing is asked of a link that's gone.
        #expect(await store.refreshStatus() == false)
        #expect(link.sent == ["status"])

        store.reconnect()
        #expect(store.stage == .pairing)
        #expect(!store.connectionLost)
        #expect(link.target == nox)
    }

    @Test func failedEventWhileConnectedIsADrop() async {
        let store = await connectedNox()
        link.emit(.failed(.connectionLost))
        #expect(store.connectionLost)
    }

    @Test func disconnectTheAppAskedForIsNotADrop() async {
        let store = await connectedNox()
        link.emit(.disconnected(nil))
        #expect(!store.connectionLost)
        #expect(store.stage == .connected)
    }

    // MARK: Commands

    @Test func commandsGoOneAtATime() async {
        let store = await connectedNox()
        link.holdReplies = true
        let first = Task { await store.send("get volume") }
        #expect(await eventually { link.unanswered == 1 })
        let second = Task { await store.send("get brightness") }
        await settle()
        // The second line waits for the first reply.
        #expect(link.sent == ["status", "get volume"])

        link.answer(#"@value {"key":"volume","value":"70"}"#)
        #expect(await first.value == .value(SettingValue(key: "volume", value: "70")))
        #expect(await eventually { link.unanswered == 1 })
        #expect(link.sent == ["status", "get volume", "get brightness"])
        link.answer(#"@value {"key":"brightness","value":"80"}"#)
        #expect(await second.value == .value(SettingValue(key: "brightness", value: "80")))
        #expect(store.settings[.volume] == "70")
        #expect(store.settings[.brightness] == "80")
    }

    @Test func staleReplyAfterADropIsIgnored() async {
        let store = await connectedNox()
        link.holdReplies = true
        let reply = Task { await store.send("get volume") }
        #expect(await eventually { link.unanswered == 1 })
        link.emit(.disconnected(.connectionLost))
        link.answer(#"@value {"key":"volume","value":"70"}"#)
        #expect(await reply.value == .error("not connected"))
        #expect(store.settings[.volume] == nil)
        #expect(!store.console.contains { $0.kind == .reply && $0.text.contains("volume") })
    }

    @Test func noReply() async {
        let store = await connectedNox()
        link.respond = { _ in throw SendError.timedOut }
        #expect(await store.send("status") == .error("no reply from the gadget"))
        #expect(store.console.last?.text == "No reply")
    }

    @Test func notConnected() async {
        let store = GadgetStore(link: link)
        link.respond = { _ in throw SendError.notConnected }
        #expect(await store.send("status") == .error("not connected"))
    }

    @Test func sentAndReceivedLinesAreLogged() async {
        let store = await connectedNox()
        await store.send("say hi", typed: true)
        let last = Array(store.console.suffix(2))
        #expect(last.map(\.kind) == [.command, .reply])
        #expect(last.map(\.text) == ["say hi", "@ok"])
        #expect(last.map(\.typed) == [true, true])
    }

    @Test func newlinesBecomeSpaces() async {
        let store = await connectedNox()
        await store.send("say one\ntwo")
        #expect(link.sent.last == "say one two")
    }

    @Test func linesOver256BytesAreRefused() async {
        let store = await connectedNox()
        let long = String(repeating: "a", count: 257)
        let reply = await store.send(long)
        #expect(reply == .error("too long: the gadget takes lines up to 256 bytes"))
        #expect(!link.sent.contains(long))
        #expect(store.console.last?.text == "Not sent: lines are limited to 256 bytes")

        // The limit is in bytes, not characters: this is 131 characters, 258 bytes.
        let accented = "say " + String(repeating: "é", count: 127)
        #expect(await store.send(accented).isError)
        #expect(!link.sent.contains(accented))
        #expect(link.sent == ["status"])
    }

    @Test func lineOfExactly256BytesIsSent() async {
        let store = await connectedNox()
        let line = "say " + String(repeating: "a", count: 252)
        #expect(await store.send(line) == .ok(""))
        #expect(link.sent.last == line)
    }

    // MARK: Settings

    @Test func setUpdatesSettings() async {
        let store = await connectedNox()
        link.respond = { line in "@ok " + line.split(separator: " ")[1] }
        #expect(await store.save(.volume, "55") == nil)
        #expect(link.sent.last == "set volume 55")
        #expect(store.settings[.volume] == "55")
    }

    @Test func secretsAreMasked() async {
        let store = await connectedNox()
        #expect(await store.save(.wifiPassword, "correct horse") == nil)
        #expect(link.sent.last == "set wifi_pass correct horse")
        #expect(store.settings[.wifiPassword] == SettingValue.maskedSecret)
        #expect(await store.save(.token, "") == nil)
        #expect(store.settings[.token] == "")
    }

    @Test func renamingUpdatesTheStatusAndGadget() async {
        let store = await connectedNox()
        #expect(await store.save(.name, " Desk ") == nil)
        #expect(link.sent.last == "set name Desk")
        #expect(store.settings[.name] == "Desk")
        #expect(store.status?.name == "Desk")
        #expect(store.gadget?.name == "Desk")
    }

    @Test func settingTypedInTheConsoleIsKept() async {
        let store = await connectedNox()
        await store.send("set talk_mode tap", typed: true)
        #expect(store.settings[.talkMode] == "tap")
    }

    @Test func errorIsReturnedToTheCaller() async {
        let store = await connectedNox()
        link.respond = { _ in "@error screen_timeout must be 0..3600 seconds" }
        #expect(await store.save(.screenTimeout, "9999") == "screen_timeout must be 0..3600 seconds")
        #expect(store.settings[.screenTimeout] == nil)
    }

    @Test func unexpectedReplyToASave() async {
        let store = await connectedNox()
        link.respond = { _ in "@phone {}" }
        #expect(await store.save(.volume, "10") == "The gadget didn't confirm.")
    }

    @Test func loadSettingsReadsEveryKey() async {
        let store = await connectedNox()
        link.respond = { line in
            let key = line.split(separator: " ")[1]
            return #"@value {"key":"\#(key)","value":"x"}"#
        }
        await store.loadSettings()
        #expect(Array(link.sent.dropFirst()) == SettingKey.allCases.map { "get \($0.rawValue)" })
        #expect(!store.missingSettings)
    }

    @Test func busyGadgetLeavesSettingsMissing() async {
        let store = await connectedNox()
        link.respond = { line in line == "get volume" ? "@error device busy" : #"@value {"key":"\#(line.split(separator: " ")[1])","value":"1"}"# }
        await store.loadSettings()
        #expect(store.missingSettings)
        #expect(store.settings[.volume] == nil)
        // Only what's missing is asked again.
        let before = link.sent.count
        link.respond = { _ in #"@value {"key":"volume","value":"70"}"# }
        await store.loadSettings()
        #expect(Array(link.sent[before...]) == ["get volume"])
        #expect(!store.missingSettings)
    }

    @Test func statusErrorIsKept() async {
        let store = await connectedNox()
        link.respond = { _ in "@error device busy" }
        #expect(await store.refreshStatus() == false)
        #expect(store.statusError == "device busy")
        #expect(store.status?.name == "Nox")
    }

    @Test func phoneReplyUpdatesTheStatus() async {
        let store = await connectedNox()
        link.respond = { _ in #"@phone {"paired":2,"connected":true,"pairing":true}"# }
        await store.send("phone")
        #expect(store.status?.phone == PhoneState(paired: 2, connected: true, pairing: true))
    }

    // MARK: Forgetting

    @Test func phoneForgetReturnsToFinding() async {
        let store = await connectedNox()
        link.respond = { _ in "@ok phone forget" }
        #expect(await store.forget() == nil)
        #expect(link.sent.last == "phone forget")
        #expect(store.stage == .finding)
        #expect(store.forgottenName == "Nox")
        #expect(store.gadget == nil)
        #expect(store.status == nil)
        #expect(link.target == nil)
    }

    @Test func refusedForgetStaysConnected() async {
        let store = await connectedNox()
        link.respond = { _ in "@error device busy" }
        #expect(await store.forget() == "device busy")
        #expect(store.stage == .connected)
        #expect(store.forgottenName == nil)
    }

    @Test func connectingClearsTheForgottenNote() async {
        let store = await connectedNox()
        await store.send("phone forget")
        #expect(store.forgottenName == "Nox")
        store.connect(to: kitchen)
        #expect(store.forgottenName == nil)
    }

    // MARK: Replacing the link

    @Test func onlyTheDemoScenarioIsTheDemo() {
        let store = GadgetStore(link: link)
        #expect(!store.isDemo)
        store.replaceLink(MockGadgetLink(scenario: .demo))
        #expect(store.isDemo)
        store.replaceLink(MockGadgetLink(scenario: .online))
        #expect(!store.isDemo)
    }

    @Test func replaceLinkStartsOver() async {
        let store = await connectedNox()
        let other = FakeGadgetLink()
        store.replaceLink(other)
        #expect(link.target == nil)
        #expect(link.onEvent == nil)
        #expect(store.stage == .finding)
        #expect(store.gadget == nil)
        #expect(store.status == nil)
        #expect(store.console.isEmpty)
        #expect(other.isScanning)
        // Events from the old link no longer reach the store.
        link.emit(.connected)
        #expect(store.stage == .finding)
        other.emit(.discovered(kitchen))
        #expect(store.found == [kitchen])
    }
}
