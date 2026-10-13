import Foundation
import Observation

/// The app's state, observed by every screen. It drives a `GadgetLink`,
/// sends console lines one at a time, and keeps what the replies say.
@MainActor @Observable
final class GadgetStore {
    enum Stage: Equatable {
        case finding, pairing, connected
    }

    enum PairingProgress: Equatable {
        case connecting
        case awaitingPasskey
        case failed(LinkFailure)
    }

    static let scanDuration: Duration = .seconds(20)
    static let maxLineBytes = 256
    private static let consoleLimit = 500

    private(set) var link: any GadgetLink
    private(set) var radio: RadioState
    private(set) var isScanning = false
    /// A scan ran for `scanDuration` and stopped on its own.
    private(set) var scanEnded = false
    private(set) var found: [DiscoveredGadget] = []
    private(set) var stage: Stage = .finding
    private(set) var pairing: PairingProgress = .connecting
    private(set) var gadget: DiscoveredGadget?
    private(set) var status: GadgetStatus?
    private(set) var statusDate: Date?
    private(set) var statusError: String?
    private(set) var isRefreshing = false
    private(set) var isLoadingSettings = false
    /// The link dropped while connected.
    private(set) var connectionLost = false
    /// The last `get` value of each setting; secrets are `<set>` or empty.
    private(set) var settings: [SettingKey: String] = [:]
    private(set) var console: [ConsoleEntry] = []
    /// The name of a gadget that was just forgotten, for a note on the find screen.
    var forgottenName: String?

    private var wantsScan = false
    private var scanTimer: Task<Void, Never>?
    private var queueTail: Task<Void, Never>?
    private var nextEntryID = 0
    /// Bumped whenever the link changes or drops, so stale replies are ignored.
    private var generation = 0

    init(link: any GadgetLink) {
        self.link = link
        self.radio = link.radio
        attach()
    }

    /// Swaps in another link (the debug menu's mock scenarios) and starts over.
    func replaceLink(_ newLink: any GadgetLink) {
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

    func startScan() {
        wantsScan = true
        scanEnded = false
        if radio == .poweredOn { beginScan() }
    }

    func stopScan() {
        wantsScan = false
        endScan()
    }

    private func beginScan() {
        guard !isScanning else { return }
        found = []
        isScanning = true
        link.startScan()
        scanTimer?.cancel()
        scanTimer = Task { [weak self] in
            try? await Task.sleep(for: Self.scanDuration)
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

    func connect(to target: DiscoveredGadget) {
        stopScan()
        forgottenName = nil
        resetConnection()
        gadget = target
        pairing = .connecting
        stage = .pairing
        link.connect(to: target)
    }

    func retryPairing() {
        guard let gadget else { return backToFinding() }
        connect(to: gadget)
    }

    /// Reconnects after the link dropped.
    func reconnect() {
        retryPairing()
    }

    /// Leaves the pairing screen or the gadget and scans again.
    func backToFinding() {
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
    func send(_ line: String, typed: Bool = false) async -> Reply {
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
    func refreshStatus() async -> Bool {
        guard stage == .connected, !connectionLost else { return false }
        isRefreshing = true
        defer { isRefreshing = false }
        let reply = await send("status")
        if case .status = reply { return true }
        statusError = reply.failureMessage
        return false
    }

    /// Reads every setting the store doesn't have yet (or all of them, when `all`).
    func loadSettings(all: Bool = false) async {
        guard !isLoadingSettings else { return }
        isLoadingSettings = true
        defer { isLoadingSettings = false }
        for key in SettingKey.allCases where all || settings[key] == nil {
            guard stage == .connected, !connectionLost else { return }
            await send("get \(key.rawValue)")
        }
    }

    /// Settings that haven't been read, for example after `@error device busy`.
    var missingSettings: Bool {
        settings.count < SettingKey.allCases.count
    }

    /// Sets a value; returns the gadget's error message, or nil when it was saved.
    func save(_ key: SettingKey, _ value: String) async -> String? {
        let value = value.replacingOccurrences(of: "\n", with: " ").trimmingCharacters(in: .whitespaces)
        let reply = await send("set \(key.rawValue) \(value)")
        guard case .ok = reply else { return reply.failureMessage ?? "The gadget didn't confirm." }
        return nil
    }

    /// `phone forget`: the gadget removes every paired phone and drops the link.
    /// Returns an error message, or nil when it's done (see `forgotten()`).
    func forget() async -> String? {
        let reply = await send("phone forget")
        guard case .ok = reply else { return reply.failureMessage ?? "The gadget didn't confirm." }
        return nil
    }

    // MARK: Console

    func clearConsole() {
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
