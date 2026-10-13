import Foundation

/// A `GadgetLink` that acts out a `MockScenario` with realistic delays:
/// two gadgets nearby, iOS's passkey pairing, and console replies from `MockDevice`.
@MainActor
final class MockGadgetLink: GadgetLink {
    var onEvent: (@MainActor (LinkEvent) -> Void)?
    private(set) var radio: RadioState = .unknown
    let scenario: MockScenario

    private static let noxID = UUID(uuidString: "6E0B9C1A-4F2D-4C8E-9B57-3A1D2C4F7E01")!
    private static let kitchenID = UUID(uuidString: "6E0B9C1A-4F2D-4C8E-9B57-3A1D2C4F7E02")!

    private var devices: [UUID: MockDevice]
    private var bonded: Set<UUID> = []
    private var connected: UUID?
    private var scanTask: Task<Void, Never>?
    private var connectTask: Task<Void, Never>?
    private var dropTask: Task<Void, Never>?
    /// A connect is under way: connecting or waiting for the passkey.
    private var isConnecting = false
    private var commandCount = 0

    init(scenario: MockScenario) {
        self.scenario = scenario
        devices = [Self.noxID: .nox(scenario), Self.kitchenID: .kitchen()]
        // Core Bluetooth starts in .unknown and reports the real state a moment later.
        Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(400))
            self?.setRadio(scenario.radio)
        }
    }

    // MARK: Debug actions

    func setRadio(_ state: RadioState) {
        radio = state
        if state != .poweredOn {
            scanTask?.cancel()
            if connected != nil || isConnecting { drop() }
        }
        onEvent?(.radio(state))
    }

    /// Hermes approves Nox, as `hermes pairing approve` would.
    func approveOnHermes() {
        devices[Self.noxID]?.approve()
    }

    /// The link drops, as if the gadget went out of range.
    func drop() {
        connectTask?.cancel()
        dropTask?.cancel()
        guard let id = connected else {
            if isConnecting {
                isConnecting = false
                onEvent?(.failed(.connectionLost))
            }
            return
        }
        devices[id]?.phoneConnected = false
        connected = nil
        onEvent?(.disconnected(.connectionLost))
    }

    // MARK: GadgetLink

    func startScan() {
        guard radio == .poweredOn else { return }
        scanTask?.cancel()
        guard scenario != .nothingNearby else { return }
        scanTask = Task { [weak self] in
            let schedule: [(UUID, Int, Duration)] = [(Self.noxID, -52, .milliseconds(900)), (Self.kitchenID, -74, .milliseconds(1300))]
            var seen: [(UUID, Int)] = []
            for (id, rssi, delay) in schedule {
                try? await Task.sleep(for: delay)
                guard !Task.isCancelled, let self else { return }
                seen.append((id, rssi))
                self.advertise(id, rssi: rssi)
            }
            // Signal strength wanders while the scan runs.
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(1500))
                guard !Task.isCancelled, let self else { return }
                for (id, rssi) in seen { self.advertise(id, rssi: rssi + Int.random(in: -4...4)) }
            }
        }
    }

    func stopScan() {
        scanTask?.cancel()
        scanTask = nil
    }

    func connect(to gadget: DiscoveredGadget) {
        connectTask?.cancel()
        isConnecting = true
        connectTask = Task { [weak self] in
            guard let self else { return }
            self.onEvent?(.connecting)
            try? await Task.sleep(for: .milliseconds(900))
            guard !Task.isCancelled else { return }
            guard self.radio == .poweredOn, self.devices[gadget.id] != nil else {
                self.finishConnecting(.failed(.unreachable))
                return
            }
            if !self.bonded.contains(gadget.id) {
                self.onEvent?(.awaitingPasskey)
                switch self.scenario {
                case .pairingRejected:
                    try? await Task.sleep(for: .seconds(4))
                    guard !Task.isCancelled else { return }
                    self.finishConnecting(.failed(.pairingRejected))
                    return
                case .pairingTimesOut:
                    try? await Task.sleep(for: .seconds(8))
                    guard !Task.isCancelled else { return }
                    self.finishConnecting(.failed(.pairingTimedOut))
                    return
                default:
                    // Long enough to read the instructions and type six digits.
                    try? await Task.sleep(for: .seconds(4))
                    guard !Task.isCancelled else { return }
                    self.bonded.insert(gadget.id)
                }
            }
            self.connected = gadget.id
            self.devices[gadget.id]?.phoneConnected = true
            self.finishConnecting(.connected)
            if self.scenario == .flaky { self.scheduleDrop(after: .seconds(60)) }
        }
    }

    func disconnect() {
        connectTask?.cancel()
        isConnecting = false
        dropTask?.cancel()
        if let id = connected { devices[id]?.phoneConnected = false }
        connected = nil
    }

    func send(_ line: String) async throws -> String {
        guard let id = connected else { throw SendError.notConnected }
        commandCount += 1
        if scenario == .flaky, commandCount % 4 == 0 {
            try? await Task.sleep(for: .seconds(3))
            return "@error device busy"
        }
        try? await Task.sleep(for: .milliseconds(Int.random(in: 120...420)))
        guard connected == id, var device = devices[id] else { throw SendError.notConnected }
        let reply = device.handle(line, now: .now)
        devices[id] = device
        if line.trimmingCharacters(in: .whitespaces) == "phone forget" {
            // The gadget drops every bond, then the connected phone.
            bonded.remove(id)
            scheduleDrop(after: .milliseconds(300))
        }
        return reply
    }

    // MARK: Private

    private func finishConnecting(_ event: LinkEvent) {
        isConnecting = false
        onEvent?(event)
    }

    private func advertise(_ id: UUID, rssi: Int) {
        guard let device = devices[id] else { return }
        onEvent?(.discovered(DiscoveredGadget(id: id, name: device.settings["name", default: "Hermes"], rssi: rssi)))
    }

    private func scheduleDrop(after delay: Duration) {
        dropTask?.cancel()
        dropTask = Task { [weak self] in
            try? await Task.sleep(for: delay)
            guard !Task.isCancelled else { return }
            self?.drop()
        }
    }
}
