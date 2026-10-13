import Foundation
import Observation

/// The app's state, observed by every screen. It drives a `GadgetLink`,
/// sends console lines one at a time, and keeps what the replies say.
@MainActor @Observable
public final class GadgetStore {
    public enum Stage: Equatable {
        case finding, pairing, connected
    }

    public enum PairingProgress: Equatable {
        case connecting
        case awaitingPasskey
        case failed(LinkFailure)
    }

    static let maxLineBytes = 256
    private static let consoleLimit = 500

    /// How long a scan runs before it stops on its own.
    let scanDuration: Duration
    public private(set) var link: any GadgetLink
    public private(set) var radio: RadioState
    public private(set) var isScanning = false
    /// A scan ran for `scanDuration` and stopped on its own.
    private(set) var scanEnded = false
    public private(set) var found: [DiscoveredGadget] = []
    public private(set) var stage: Stage = .finding
    public private(set) var pairing: PairingProgress = .connecting
    public private(set) var gadget: DiscoveredGadget?
    public private(set) var status: GadgetStatus?
    public private(set) var statusDate: Date?
    public private(set) var statusError: String?
    private(set) var isRefreshing = false
    public private(set) var isLoadingSettings = false
    /// The link dropped while connected.
    public private(set) var connectionLost = false
    /// The last `get` value of each setting; secrets are `<set>` or empty.
    public private(set) var settings: [SettingKey: String] = [:]
    /// The saved Wi-Fi networks from the last `wifi list`.
    public private(set) var wifiNetworks: WifiNetworkList?
    /// The firmware has no `wifi` command: it keeps one network, set with
    /// `set wifi_ssid` and `set wifi_pass`.
    public private(set) var keepsOneNetwork = false
    public private(set) var console: [ConsoleEntry] = []
    /// The name of a gadget that was just forgotten, for a note on the find screen.
    public var forgottenName: String?

    private var wantsScan = false
    private var scanTimer: Task<Void, Never>?
    private var queueTail: Task<Void, Never>?
    private var nextEntryID = 0
    /// Bumped whenever the link changes or drops, so stale replies are ignored.
    private var generation = 0

    public convenience init(link: any GadgetLink) {
        self.init(link: link, scanDuration: .seconds(20))
    }

    init(link: any GadgetLink, scanDuration: Duration) {
        self.scanDuration = scanDuration
        self.link = link
        self.radio = link.radio
        attach()
    }

    /// Swaps in another link (the debug menu's mock scenarios) and starts over.
    /// The app is showing the demo: a mock gadget, not real Bluetooth.
    public var isDemo: Bool {
        (link as? MockGadgetLink)?.scenario == .demo
    }

    public func replaceLink(_ newLink: any GadgetLink) {
        link.onEvent = nil
        link.stopScan()
        link.disconnect()
        scanTimer?.cancel()
        generation += 1
        link = newLink
        radio = newLink.radio
        isScanning = false
        scanEnded = false
        found = []
        forgottenName = nil
        console = []
        resetConnection()
        gadget = nil
        stage = .finding
        attach()
        // The find screen may already be showing, so it won't ask again.
        startScan()
    }

    private func attach() {
        link.onEvent = { [weak self] event in self?.handle(event) }
    }

    // MARK: Scanning

    public func startScan() {
        wantsScan = true
        scanEnded = false
        if radio == .poweredOn { beginScan() }
    }

    public func stopScan() {
        wantsScan = false
        endScan()
    }

    private func beginScan() {
        guard !isScanning else { return }
        found = []
        isScanning = true
        link.startScan()
        scanTimer?.cancel()
        let duration = scanDuration
        scanTimer = Task { [weak self] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled, let self, self.isScanning else { return }
            self.wantsScan = false
            self.scanEnded = true
            self.endScan()
        }
    }

    private func endScan() {
        scanTimer?.cancel()
        scanTimer = nil
        if isScanning { link.stopScan() }
        isScanning = false
    }

    // MARK: Connecting

    public func connect(to target: DiscoveredGadget) {
        stopScan()
        forgottenName = nil
        resetConnection()
        gadget = target
        pairing = .connecting
        stage = .pairing
        link.connect(to: target)
    }

    public func retryPairing() {
        guard let gadget else { return backToFinding() }
        connect(to: gadget)
    }

    /// Reconnects after the link dropped.
    public func reconnect() {
        retryPairing()
    }

    /// Leaves the pairing screen or the gadget and scans again.
    public func backToFinding() {
        link.disconnect()
        resetConnection()
        gadget = nil
        stage = .finding
        startScan()
    }

    private func resetConnection() {
        generation += 1
        queueTail = nil
        status = nil
        statusDate = nil
        statusError = nil
        isRefreshing = false
        connectionLost = false
        settings = [:]
        wifiNetworks = nil
        keepsOneNetwork = false
    }

    private func handle(_ event: LinkEvent) {
        switch event {
        case .radio(let state):
            radio = state
            if state == .poweredOn {
                if wantsScan { beginScan() }
            } else {
                endScan()
                found = []
            }
        case .discovered(let seen):
            guard isScanning else { return }
            if let index = found.firstIndex(where: { $0.id == seen.id }) {
                found[index] = seen
            } else {
                found.append(seen)
            }
        case .connecting:
            pairing = .connecting
        case .awaitingPasskey:
            pairing = .awaitingPasskey
        case .connected:
            stage = .connected
            connectionLost = false
            note("Connected to \(gadget?.name ?? "gadget")")
            Task { await refreshStatus() }
        case .failed(let failure):
            if stage == .pairing {
                pairing = .failed(failure)
            } else if stage == .connected {
                linkDropped()
            }
        case .disconnected(let failure):
            guard let failure else { return }
            if stage == .pairing {
                pairing = .failed(failure)
            } else if stage == .connected {
                linkDropped()
            }
        }
    }

    private func linkDropped() {
        generation += 1
        queueTail = nil
        isRefreshing = false
        connectionLost = true
        note("Connection lost")
    }

    // MARK: Commands

    /// Sends one console line after any line already in flight, and returns its reply.
    @discardableResult
    public func send(_ line: String, typed: Bool = false) async -> Reply {
        let previous = queueTail
        let task = Task { [weak self] () -> Reply in
            await previous?.value
            guard let self else { return .error("not connected") }
            return await self.perform(line, typed: typed)
        }
        queueTail = Task { _ = await task.value }
        return await task.value
    }

    private func perform(_ line: String, typed: Bool) async -> Reply {
        let started = generation
        let clean = line.replacingOccurrences(of: "\n", with: " ")
        log(.command, clean, typed: typed)
        guard clean.utf8.count <= Self.maxLineBytes else {
            note("Not sent: lines are limited to \(Self.maxLineBytes) bytes", typed: typed)
            return .error("too long: the gadget takes lines up to \(Self.maxLineBytes) bytes")
        }
        do {
            let text = try await link.send(clean)
            guard started == generation else { return .error("not connected") }
            log(.reply, text, typed: typed)
            let reply = Reply.parse(text)
            absorb(reply, to: clean)
            return reply
        } catch SendError.timedOut {
            note("No reply", typed: typed)
            return .error("no reply from the gadget")
        } catch {
            note("Not connected", typed: typed)
            return .error("not connected")
        }
    }

    /// Keeps what a reply says, whether the app or the console sent the line.
    private func absorb(_ reply: Reply, to line: String) {
        switch reply {
        case .status(let new):
            status = new
            statusDate = .now
            statusError = nil
        case .value(let value):
            if let key = SettingKey(rawValue: value.key) { settings[key] = value.value }
        case .phone(let phone):
            status?.phone = phone
        case .wifi(let list):
            wifiNetworks = list
        case .ok:
            let words = line.trimmingCharacters(in: .whitespaces).split(separator: " ", maxSplits: 2).map(String.init)
            if words.count >= 2, words[0] == "set", let key = SettingKey(rawValue: words[1]) {
                recordSaved(key, words.count > 2 ? words[2].trimmingCharacters(in: .whitespaces) : "")
            } else if words == ["phone", "forget"] {
                forgotten()
            }
        default:
            break
        }
    }

    private func recordSaved(_ key: SettingKey, _ value: String) {
        settings[key] = key.isSecret && !value.isEmpty ? SettingValue.maskedSecret : value
        if key == .name, !value.isEmpty {
            status?.name = value
            gadget?.name = value
        }
    }

    /// The gadget dropped every phone: leave it and say so on the find screen.
    private func forgotten() {
        let name = status?.name ?? gadget?.name ?? "The gadget"
        link.disconnect()
        resetConnection()
        gadget = nil
        stage = .finding
        forgottenName = name
    }

    /// Asks for `status`; returns whether a new one arrived.
    @discardableResult
    public func refreshStatus() async -> Bool {
        guard stage == .connected, !connectionLost else { return false }
        isRefreshing = true
        defer { isRefreshing = false }
        let reply = await send("status")
        if case .status = reply { return true }
        statusError = reply.failureMessage
        return false
    }

    /// Reads every setting the store doesn't have yet (or all of them, when `all`).
    public func loadSettings(all: Bool = false) async {
        guard !isLoadingSettings else { return }
        isLoadingSettings = true
        defer { isLoadingSettings = false }
        for key in SettingKey.allCases where all || settings[key] == nil {
            guard stage == .connected, !connectionLost else { return }
            await send("get \(key.rawValue)")
        }
    }

    /// Settings that haven't been read, for example after `@error device busy`.
    public var missingSettings: Bool {
        settings.count < SettingKey.allCases.count
    }

    /// Sets a value; returns the gadget's error message, or nil when it was saved.
    public func save(_ key: SettingKey, _ value: String) async -> String? {
        let value = value.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        let reply = await send("set \(key.rawValue) \(value)")
        guard case .ok = reply else { return reply.failureMessage ?? "The gadget didn't confirm." }
        return nil
    }

    // MARK: Wi-Fi networks

    /// Reads the saved networks (`wifiNetworks`). Returns an error message, or
    /// nil when they arrived or the firmware keeps one network (`keepsOneNetwork`).
    @discardableResult
    public func loadWifiNetworks() async -> String? {
        let reply = await send("wifi list")
        switch reply {
        case .wifi:
            return nil
        case .error(let message) where message.hasPrefix("unknown command"):
            keepsOneNetwork = true
            return nil
        default:
            return reply.failureMessage ?? "The gadget didn't answer."
        }
    }

    /// Saves a network without joining it; an empty password saves an open network.
    public func addWifi(_ ssid: String, password: String) async -> String? {
        var line = "wifi add " + ConsoleArgument.quoted(ssid)
        if !password.isEmpty { line += " " + ConsoleArgument.quoted(password) }
        return await changeWifi(line)
    }

    /// Moves a saved network first; the gadget leaves its network and joins it.
    public func joinWifi(_ ssid: String) async -> String? {
        await changeWifi("wifi join " + ConsoleArgument.quoted(ssid))
    }

    public func forgetWifi(_ ssid: String) async -> String? {
        await changeWifi("wifi forget " + ConsoleArgument.quoted(ssid))
    }

    private func changeWifi(_ line: String) async -> String? {
        let reply = await send(line)
        guard case .ok = reply else { return reply.failureMessage ?? "The gadget didn't confirm." }
        await loadWifiNetworks()
        return nil
    }

    /// `phone forget`: the gadget removes every paired phone and drops the link.
    /// Returns an error message, or nil when it's done (see `forgotten()`).
    public func forget() async -> String? {
        let reply = await send("phone forget")
        guard case .ok = reply else { return reply.failureMessage ?? "The gadget didn't confirm." }
        return nil
    }

    // MARK: Console

    public func clearConsole() {
        console = []
    }

    private func note(_ text: String, typed: Bool = true) {
        log(.note, text, typed: typed)
    }

    private func log(_ kind: ConsoleEntry.Kind, _ text: String, typed: Bool) {
        nextEntryID += 1
        console.append(ConsoleEntry(id: nextEntryID, kind: kind, text: text, typed: typed, date: .now))
        if console.count > Self.consoleLimit {
            console.removeFirst(console.count - Self.consoleLimit)
        }
    }
}
