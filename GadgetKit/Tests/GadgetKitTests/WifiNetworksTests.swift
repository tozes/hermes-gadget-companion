import Foundation
import Testing
@testable import GadgetKit

/// Saved Wi-Fi networks: the `wifi` command's replies, the store's calls,
/// joining one, and the mock that acts out the firmware.
@MainActor
struct WifiNetworksTests {
    let link = FakeGadgetLink()
    let now = Date(timeIntervalSinceReferenceDate: 0)

    static let twoSaved = #"@wifi {"networks":[{"ssid":"Lisbon Loft","tested":true,"open":false},{"ssid":"Pocket \"5G\"","tested":false,"open":true}],"max":5,"current":"Lisbon Loft"}"#

    // MARK: Replies and arguments

    @Test func parsesTheList() {
        guard case .wifi(let list) = Reply.parse(Self.twoSaved) else {
            Issue.record("not a wifi list")
            return
        }
        #expect(list.networks == [
            .init(ssid: "Lisbon Loft", tested: true, open: false),
            .init(ssid: #"Pocket "5G""#, tested: false, open: true),
        ])
        #expect(list.max == 5)
        #expect(list.current == "Lisbon Loft")
        #expect(!list.isFull)
        #expect(list.contains("Lisbon Loft"))
    }

    @Test func listWithoutCurrentNetwork() {
        guard case .wifi(let list) = Reply.parse(#"@wifi {"networks":[],"max":5}"#) else {
            Issue.record("not a wifi list")
            return
        }
        #expect(list.current == nil)
        #expect(list.networks.isEmpty)
    }

    @Test func missingFieldsGetDefaults() {
        guard case .wifi(let list) = Reply.parse(#"@wifi {"networks":[{"ssid":"A"}]}"#) else {
            Issue.record("not a wifi list")
            return
        }
        #expect(list.networks == [WifiNetworkList.Network(ssid: "A", tested: false, open: false)])
        #expect(list.max == 5)
        #expect(list.current == nil)
        guard case .wifi(let empty) = Reply.parse("@wifi {}") else {
            Issue.record("an empty object is not a wifi list")
            return
        }
        #expect(empty.networks.isEmpty)
    }

    @Test func aNetworkWithoutANameIsNotAList() {
        guard case .other(let tag, _) = Reply.parse(#"@wifi {"networks":[{"tested":true}],"max":5}"#) else {
            Issue.record("decoded a network with no name")
            return
        }
        #expect(tag == "wifi")
    }

    /// The same cases as the firmware's `console args` test.
    @Test func argumentsSplitLikeTheGadget() {
        #expect(ConsoleArgument.split("add  Home   secret") == ["add", "Home", "secret"])
        #expect(ConsoleArgument.split(#"add "My Home" "pass word""#) == ["add", "My Home", "pass word"])
        #expect(ConsoleArgument.split(#"add "" a\ b\"c d\\e"#) == ["add", "", #"a b"c"#, #"d\e"#])
        #expect(ConsoleArgument.split(#"x"y" "a"b"#) == [#"x"y""#, "a", "b"])
        for value in ["", "plain", "two  spaces", #"q"u\o"te\"#, " edge "] {
            #expect(ConsoleArgument.split(ConsoleArgument.quoted(value)) == [value])
        }
    }

    // MARK: The store

    /// A store connected to Nox: `status` answers `status`, `respond` the rest.
    private func store(status: String = statusSample, _ respond: @escaping (String) -> String) async -> GadgetStore {
        link.respond = { line in line == "status" ? status : respond(line) }
        return await connectedStore(link)
    }

    @Test func loadsTheList() async {
        let store = await store { _ in Self.twoSaved }
        #expect(await store.loadWifiNetworks() == nil)
        #expect(store.wifiNetworks?.networks.count == 2)
        #expect(!store.keepsOneNetwork)
    }

    @Test func olderFirmwareKeepsOneNetwork() async {
        let store = await store { _ in "@error unknown command (try: help)" }
        #expect(await store.loadWifiNetworks() == nil)
        #expect(store.keepsOneNetwork)
        #expect(store.wifiNetworks == nil)
    }

    @Test func addQuotesNameAndPasswordThenRereadsTheList() async {
        let store = await store { line in line.hasPrefix("wifi add") ? "@ok wifi add" : Self.twoSaved }
        #expect(await store.addWifi(#"Pocket "5G""#, password: #"pa\ss word"#) == nil)
        #expect(link.sent.suffix(2) == [#"wifi add "Pocket \"5G\"" "pa\\ss word""#, "wifi list"])
        #expect(store.wifiNetworks?.networks.count == 2)
        // An open network: no password argument at all.
        _ = await store.addWifi("Cafe", password: "")
        #expect(link.sent.suffix(2) == [#"wifi add "Cafe""#, "wifi list"])
    }

    @Test func refusalsComeBackAsMessages() async {
        let store = await store { _ in "@error no saved network Cafe" }
        #expect(await store.joinWifi("Cafe") == "no saved network Cafe")
        #expect(await store.forgetWifi("Cafe") == "no saved network Cafe")
        #expect(link.sent.suffix(2) == [#"wifi join "Cafe""#, #"wifi forget "Cafe""#])
    }

    @Test func aNewConnectionForgetsTheList() async {
        let store = await store { _ in Self.twoSaved }
        await store.loadWifiNetworks()
        store.backToFinding()
        #expect(store.wifiNetworks == nil)
    }

    // MARK: Joining and saving

    private func join(timeout: TimeInterval = 2) -> WifiJoin {
        WifiJoin(joinTimeout: timeout, hermesTimeout: timeout, pollInterval: .milliseconds(1))
    }

    private static func list(current: String?) -> String {
        let field = current.map { #","current":"\#($0)""# } ?? ""
        return #"@wifi {"networks":[{"ssid":"Pocket","tested":false,"open":false},{"ssid":"Lisbon Loft","tested":true,"open":false}],"max":5\#(field)}"#
    }

    private static let online = #"@status {"name":"Nox","network":true,"phase":"online","paired":true}"#

    @Test func joinsANewNetworkUntilItIsCurrent() async {
        // The gadget stays on its old network for a moment after `wifi join`.
        var lists = [Self.list(current: "Lisbon Loft"), Self.list(current: "Lisbon Loft"), Self.list(current: nil), Self.list(current: "Pocket")]
        let store = await store(status: Self.online) { line in
            if line.hasPrefix("wifi add") { return "@ok wifi add" }
            if line.hasPrefix("wifi join") { return "@ok wifi join" }
            return lists.count > 1 ? lists.removeFirst() : lists[0]
        }
        let join = join()
        await join.join(store: store, ssid: "Pocket", newPassword: "hotspot-pass")
        #expect(join.step == .done(approved: true))
        #expect(link.sent.contains(#"wifi add "Pocket" "hotspot-pass""#))
        #expect(link.sent.contains(#"wifi join "Pocket""#))
        #expect(store.wifiNetworks?.current == "Pocket")
        // It polled past the lists that still showed the old network.
        #expect(link.sent.filter { $0 == "wifi list" }.count >= 4)
    }

    @Test func joinsASavedNetworkWithoutSavingItAgain() async {
        let store = await store(status: Self.online) { line in
            line.hasPrefix("wifi join") ? "@ok wifi join" : Self.list(current: "Pocket")
        }
        let join = join()
        await join.join(store: store, ssid: "Pocket", newPassword: nil)
        #expect(join.step == .done(approved: true))
        #expect(!link.sent.contains { $0.hasPrefix("wifi add") })
    }

    @Test func aJoinThatNeverLandsSaysTheGadgetGoesBack() async {
        let store = await store { line in line.hasPrefix("wifi join") ? "@ok wifi join" : Self.list(current: "Lisbon Loft") }
        await store.loadWifiNetworks()
        let join = join(timeout: 0.05)
        await join.join(store: store, ssid: "Pocket", newPassword: nil)
        guard case .failed(let message) = join.step else {
            Issue.record("expected a failure, got \(join.step)")
            return
        }
        #expect(message.hasPrefix("Pocket didn't connect."))
        #expect(message.hasSuffix("It goes back to its other saved networks."))
    }

    @Test func aRefusedPasswordShowsUnderThePasswordField() async {
        let store = await store { _ in "@error Use an 8..63 character Wi-Fi password, a 64-digit hex key, or leave it empty for an open network" }
        let join = join()
        await join.join(store: store, ssid: "Pocket", newPassword: "short")
        #expect(join.step == .editing)
        #expect(join.error(for: .password)?.hasPrefix("Use an 8..63") == true)
        #expect(join.error(for: .ssid) == nil)
        #expect(!link.sent.contains { $0.hasPrefix("wifi join") })
    }

    @Test func savesForLater() async {
        let store = await store { line in line.hasPrefix("wifi add") ? "@ok wifi add" : Self.list(current: "Lisbon Loft") }
        let join = join()
        await join.save(store: store, ssid: "Pocket", password: "hotspot-pass")
        #expect(join.step == .saved)
        #expect(!join.isRunning)
        #expect(!link.sent.contains { $0.hasPrefix("wifi join") })
        #expect(store.wifiNetworks?.contains("Pocket") == true)
    }

    @Test func aFullListIsRefusedUnderTheName() async {
        let store = await store { _ in "@error 5 networks saved; forget one first" }
        let join = join()
        await join.save(store: store, ssid: "Sixth", password: "")
        #expect(join.step == .editing)
        #expect(join.error(for: .ssid) == "5 networks saved; forget one first")
    }

    // MARK: The mock

    @Test func mockListsAddsJoinsAndForgets() {
        var device = MockDevice.nox(.online)
        guard case .wifi(let start) = Reply.parse(device.handle("wifi", now: now)) else {
            Issue.record("no list")
            return
        }
        #expect(start.networks.map(\.ssid) == ["Lisbon Loft", "Pocket Hotspot"])
        #expect(start.current == "Lisbon Loft")
        #expect(device.handle(#"wifi add "Cafe Wi-Fi" "espresso1""#, now: now) == "@ok wifi add")
        #expect(device.network)  // saving a network doesn't leave the current one
        #expect(device.handle(#"wifi join "Cafe Wi-Fi""#, now: now) == "@ok wifi join")
        #expect(!device.network)
        #expect(device.settings["wifi_ssid"] == "Cafe Wi-Fi")
        device.tick(now: now.addingTimeInterval(3))
        guard case .wifi(let joined) = Reply.parse(device.handle("wifi list", now: now.addingTimeInterval(3))) else {
            Issue.record("no list")
            return
        }
        #expect(joined.current == "Cafe Wi-Fi")
        #expect(joined.networks.first == .init(ssid: "Cafe Wi-Fi", tested: true, open: false))
        #expect(device.handle(#"wifi forget "Pocket Hotspot""#, now: now) == "@ok wifi forget")
        #expect(device.networks.map(\.ssid) == ["Cafe Wi-Fi", "Lisbon Loft"])
    }

    @Test func mockRefusesLikeTheFirmware() {
        var device = MockDevice.nox(.online)
        #expect(device.handle("wifi add Home short", now: now).hasPrefix("@error Use an 8..63"))
        #expect(device.handle(#"wifi add """#, now: now).hasPrefix("@error Network name"))
        #expect(device.handle("wifi join Elsewhere", now: now) == "@error no saved network Elsewhere")
        for i in 0..<3 { #expect(device.handle("wifi add Net\(i)", now: now) == "@ok wifi add") }
        #expect(device.handle("wifi add Net3", now: now) == "@error 5 networks saved; forget one first")
        #expect(device.handle("wifi add", now: now).hasPrefix("@error usage"))
        #expect(device.handle("wifi rename a", now: now).hasPrefix("@error usage"))
    }

    @Test func mockSetWifiSSIDPutsANetworkFirstAndKeepsTheOthers() {
        var device = MockDevice.nox(.online)
        #expect(device.handle("set wifi_ssid Office", now: now) == "@ok wifi_ssid")
        #expect(device.handle("set wifi_pass password2", now: now) == "@ok wifi_pass")
        #expect(device.networks.map(\.ssid) == ["Office", "Lisbon Loft", "Pocket Hotspot"])
        #expect(device.networks[0].password == "password2")
        #expect(!device.network)
        #expect(device.handle("set wifi_ssid", now: now) == "@ok wifi_ssid")
        #expect(device.networks.map(\.ssid) == ["Lisbon Loft", "Pocket Hotspot"])
        #expect(device.settings["wifi_ssid"] == "Lisbon Loft")
    }
}
