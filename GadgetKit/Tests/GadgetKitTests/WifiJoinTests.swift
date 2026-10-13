import Foundation
import Testing
@testable import GadgetKit

@MainActor
struct WifiJoinTests {
    let link = FakeGadgetLink()

    private static func status(network: Bool, phase: String, paired: Bool = true) -> String {
        #"@status {"name":"Nox","network":\#(network),"phase":"\#(phase)","paired":\#(paired)}"#
    }

    static let offline = status(network: false, phase: "no_network")
    static let connecting = status(network: true, phase: "connecting")
    static let handshake = status(network: true, phase: "handshake")
    static let online = status(network: true, phase: "online")

    /// A store connected to Nox: `set` lines get `@ok <key>` unless `refuse`
    /// names the key, and `status` follows `statuses` after the first (offline) one.
    private func store(statuses: [String], refuse: [String: String] = [:]) async -> GadgetStore {
        var queue = [Self.offline] + statuses
        link.respond = { line in
            let words = line.split(separator: " ")
            if words.first == "set", words.count > 1 {
                let key = String(words[1])
                return refuse[key].map { "@error \($0)" } ?? "@ok \(key)"
            }
            guard line == "status" else { return "@error unknown command (try: help)" }
            return queue.count > 1 ? queue.removeFirst() : queue[0]
        }
        return await connectedStore(link)
    }

    /// Quick polls and short timeouts; a run that succeeds ends well before them.
    private func join(timeout: TimeInterval = 2) -> WifiJoin {
        WifiJoin(joinTimeout: timeout, hermesTimeout: timeout, pollInterval: .milliseconds(1))
    }

    private var setLines: [String] { link.sent.filter { $0.hasPrefix("set ") } }

    @Test func joinsAndReachesHermes() async {
        let store = await store(statuses: [Self.offline, Self.connecting, Self.handshake, Self.online])
        let join = join()
        await join.run(store: store, ssid: "Lisbon Loft", password: "correct horse", clearPassword: false)
        #expect(join.step == .done(approved: true))
        #expect(!join.isRunning)
        #expect(setLines == ["set wifi_ssid Lisbon Loft", "set wifi_pass correct horse"])
        #expect(store.settings[.wifiSSID] == "Lisbon Loft")
        #expect(store.settings[.wifiPassword] == SettingValue.maskedSecret)
    }

    @Test func onlineButNotApproved() async {
        let store = await store(statuses: [Self.connecting, Self.status(network: true, phase: "online", paired: false)])
        let join = join()
        await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false)
        #expect(join.step == .done(approved: false))
    }

    @Test func emptyPasswordKeepsTheSavedOne() async {
        let store = await store(statuses: [Self.online])
        let join = join()
        await join.run(store: store, ssid: "Lisbon Loft", password: "", clearPassword: false)
        #expect(setLines == ["set wifi_ssid Lisbon Loft"])
        #expect(join.step == .done(approved: true))
    }

    @Test func openNetworkClearsThePassword() async {
        let store = await store(statuses: [Self.online])
        let join = join()
        await join.run(store: store, ssid: "Cafe", password: "ignored", clearPassword: true)
        #expect(setLines.map { $0.trimmingCharacters(in: .whitespaces) } == ["set wifi_ssid Cafe", "set wifi_pass"])
        #expect(store.settings[.wifiPassword] == "")
    }

    @Test func refusedNetworkName() async {
        let store = await store(statuses: [Self.online], refuse: ["wifi_ssid": "wifi_ssid too long"])
        let join = join()
        await join.run(store: store, ssid: String(repeating: "x", count: 40), password: "pw", clearPassword: false)
        #expect(join.step == .editing)
        #expect(join.error(for: .ssid) == "wifi_ssid too long")
        #expect(join.error(for: .password) == nil)
        #expect(setLines.count == 1)
    }

    @Test func refusedPassword() async {
        let store = await store(statuses: [Self.online], refuse: ["wifi_pass": "wifi_pass too long"])
        let join = join()
        await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false)
        #expect(join.step == .editing)
        #expect(join.error(for: .password) == "wifi_pass too long")
        #expect(join.error(for: .ssid) == nil)
        // Only the status from connecting: no polling.
        #expect(link.sent.filter { $0 == "status" }.count == 1)
    }

    @Test func networkNeverComesUp() async {
        let store = await store(statuses: [Self.offline])
        let join = join(timeout: 0.05)
        await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false)
        guard case .failed(let message) = join.step else {
            Issue.record("step is \(join.step)")
            return
        }
        #expect(message.hasPrefix("Lisbon Loft didn't connect."))
    }

    @Test func hermesNeverAnswers() async {
        let store = await store(statuses: [Self.connecting])
        let join = join(timeout: 0.05)
        await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false)
        #expect(join.step == .noHermes)
    }

    @Test func failedRefreshesDontCount() async {
        // The status from before the join says online; busy replies mustn't
        // let that stale status pass for a new one.
        link.respond = { line in line == "status" ? Self.online : "@ok" }
        let store = await connectedStore(link)
        #expect(store.status?.network == true)
        link.respond = { line in line == "status" ? "@error device busy" : "@ok wifi_ssid" }
        let join = join(timeout: 0.05)
        await join.run(store: store, ssid: "Lisbon Loft", password: "", clearPassword: false)
        guard case .failed = join.step else {
            Issue.record("step is \(join.step)")
            return
        }
    }

    @Test func busyRepliesInBetweenAreSkipped() async {
        let store = await store(statuses: [Self.connecting, Self.online])
        var count = 0
        let answer = link.respond
        link.respond = { line in
            count += 1
            if line == "status", count % 2 == 0 { return "@error device busy" }
            return try answer(line)
        }
        let join = join()
        await join.run(store: store, ssid: "Lisbon Loft", password: "", clearPassword: false)
        #expect(join.step == .done(approved: true))
        #expect(store.statusError == nil)
    }

    @Test func linkDropsWhileJoining() async {
        let store = await store(statuses: [Self.offline])
        let join = join()
        let run = Task { await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false) }
        #expect(await eventually { join.step == .joining })
        link.emit(.disconnected(.connectionLost))
        await run.value
        guard case .failed(let message) = join.step else {
            Issue.record("step is \(join.step)")
            return
        }
        #expect(message.hasPrefix("The Bluetooth connection dropped"))
    }

    @Test func resetAbandonsTheRun() async {
        let store = await store(statuses: [Self.offline])
        let join = join()
        let run = Task { await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false) }
        #expect(await eventually { join.step == .joining })
        join.reset()
        await run.value
        #expect(join.step == .editing)
        #expect(!join.isRunning)
    }

    @Test func cancellingAbandonsTheRun() async {
        let store = await store(statuses: [Self.offline])
        let join = join()
        let run = Task { await join.run(store: store, ssid: "Lisbon Loft", password: "pw", clearPassword: false) }
        #expect(await eventually { join.step == .joining })
        run.cancel()
        await run.value
        // The run stops without writing an outcome.
        #expect(join.step == .joining)
    }

    @Test func defaultTimings() {
        let join = WifiJoin()
        #expect(join.joinTimeout == 30)
        #expect(join.hermesTimeout == 30)
        #expect(join.pollInterval == .seconds(1))
        #expect(join.step == .editing)
    }
}
