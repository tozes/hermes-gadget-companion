import Foundation
@testable import GadgetKit

/// A `GadgetLink` the test drives by hand: it records what the store asks of
/// the link, the test sends its events with `emit`, and replies come from
/// `respond` at once, or wait until the test answers them when `holdReplies`
/// is set. Nothing sleeps.
@MainActor
final class FakeGadgetLink: GadgetLink {
    var onEvent: (@MainActor (LinkEvent) -> Void)?
    private(set) var radio: RadioState

    /// Whether the store has the link scanning.
    private(set) var isScanning = false
    /// The gadget the store last asked to connect to, until it disconnects.
    private(set) var target: DiscoveredGadget?
    /// Every line the link was asked to write, in order.
    private(set) var sent: [String] = []

    /// The reply to each line, unless `holdReplies` is set.
    var respond: (String) throws -> String = { _ in "@ok" }
    var holdReplies = false
    private var waiting: [CheckedContinuation<String, Error>] = []

    init(radio: RadioState = .poweredOn) {
        self.radio = radio
    }

    // MARK: Driving the store

    func emit(_ event: LinkEvent) {
        if case .radio(let state) = event { radio = state }
        onEvent?(event)
    }

    /// Lines written and not yet answered (with `holdReplies`).
    var unanswered: Int { waiting.count }

    /// Answers the oldest held line.
    func answer(_ text: String) {
        waiting.removeFirst().resume(returning: text)
    }

    func fail(_ error: SendError) {
        waiting.removeFirst().resume(throwing: error)
    }

    // MARK: GadgetLink

    func startScan() { isScanning = true }

    func stopScan() { isScanning = false }

    func connect(to gadget: DiscoveredGadget) { target = gadget }

    func disconnect() { target = nil }

    func send(_ line: String) async throws -> String {
        sent.append(line)
        if holdReplies {
            return try await withCheckedThrowingContinuation { waiting.append($0) }
        }
        return try respond(line)
    }
}

/// Waits, without blocking the main actor, until `condition` holds; false if
/// it doesn't within `timeout`. Fake replies arrive within a few yields; only
/// the store's own timers need real time.
@MainActor
func eventually(timeout: Duration = .seconds(2), _ condition: () -> Bool) async -> Bool {
    let deadline = ContinuousClock.now + timeout
    while !condition() {
        guard ContinuousClock.now < deadline else { return false }
        await Task.yield()
        try? await Task.sleep(for: .milliseconds(1))
    }
    return true
}

/// Lets the main actor run whatever is ready, a few times over.
@MainActor
func settle() async {
    for _ in 0..<50 { await Task.yield() }
}

/// A store whose link reached `.connected` to Nox and was asked for the first
/// `status`; `link.respond` should answer it.
@MainActor
func connectedStore(_ link: FakeGadgetLink) async -> GadgetStore {
    let store = GadgetStore(link: link)
    store.connect(to: nox)
    link.emit(.connected)
    _ = await eventually { link.sent.contains("status") && !store.isRefreshing }
    return store
}

/// The real `@status` sample from a gadget (sanitized).
let statusSample = #"@status {"device_id":"hg-5a1c9e2f7b3d4086","name":"Nox","board":"esp32s3-touch-amoled-1.75c","firmware":"0.2.0","phase":"no_network","screen":"offline","paired":false,"server":"wss://hermes.example.com:8765/gadget","network":false,"display_sleeping":false,"power":{"available":true,"battery_present":true,"battery_mv":4126,"battery_percent":84,"charging":true,"external_power":true},"phone":{"paired":1,"connected":true,"pairing":false}}"#

let nox = DiscoveredGadget(id: UUID(uuidString: "6E0B9C1A-4F2D-4C8E-9B57-3A1D2C4F7E01")!, name: "Nox", rssi: -52)
let kitchen = DiscoveredGadget(id: UUID(uuidString: "6E0B9C1A-4F2D-4C8E-9B57-3A1D2C4F7E02")!, name: "Kitchen", rssi: -74)
