import Foundation
import Observation

/// Joining Wi-Fi: save the network, then watch `status` until the gadget is
/// on the network and its phase moves on to Hermes.
@MainActor @Observable
final class WifiJoin {
    enum Step: Equatable {
        case editing
        case saving
        case joining
        case reachingHermes
        /// Online; `approved` is false while Hermes still has to approve the gadget.
        case done(approved: Bool)
        /// On Wi-Fi, but Hermes never answered.
        case noHermes
        case failed(String)
    }

    enum Field: Equatable {
        case ssid, password
    }

    private(set) var step: Step = .editing
    /// Bumped by each run and by `reset()`, so an abandoned run stops writing.
    private var runID = 0
    /// A refused `set`, shown under its field.
    private(set) var fieldError: (field: Field, message: String)?

    static let joinTimeout: TimeInterval = 30
    static let hermesTimeout: TimeInterval = 30
    private static let pollInterval: Duration = .seconds(1)

    var isRunning: Bool {
        switch step {
        case .saving, .joining, .reachingHermes: true
        default: false
        }
    }

    func error(for field: Field) -> String? {
        fieldError?.field == field ? fieldError?.message : nil
    }

    func reset() {
        runID += 1
        step = .editing
        fieldError = nil
    }

    /// Saves the network and follows the join. An empty password keeps the
    /// saved one unless `clearPassword` (an open network) is set. Cancelling
    /// the calling task, or `reset()`, abandons the run.
    func run(store: GadgetStore, ssid: String, password: String, clearPassword: Bool) async {
        runID += 1
        let id = runID
        fieldError = nil
        step = .saving
        let ssidError = await store.save(.wifiSSID, ssid)
        guard isCurrent(id) else { return }
        if let ssidError {
            fieldError = (.ssid, ssidError)
            step = .editing
            return
        }
        if !password.isEmpty || clearPassword {
            let passwordError = await store.save(.wifiPassword, clearPassword ? "" : password)
            guard isCurrent(id) else { return }
            if let passwordError {
                fieldError = (.password, passwordError)
                step = .editing
                return
            }
        }

        step = .joining
        switch await poll(until: Self.joinTimeout, store: store, id: id, { $0.network == true }) {
        case .met:
            break
        case .cancelled:
            return
        case .lost:
            step = .failed(Self.lostMessage)
            return
        case .timedOut:
            step = .failed("\(ssid) didn't connect. Check the network name and password; the gadget joins 2.4 GHz networks only.")
            return
        }
        step = .reachingHermes
        switch await poll(until: Self.hermesTimeout, store: store, id: id, { $0.phase == .online }) {
        case .met: step = .done(approved: store.status?.paired != false)
        case .cancelled: return
        case .lost: step = .failed(Self.lostMessage)
        case .timedOut: step = .noHermes
        }
    }

    private static let lostMessage = "The Bluetooth connection dropped, so the app can't tell whether the gadget joined. Reconnect from the home screen to check."

    private func isCurrent(_ id: Int) -> Bool {
        id == runID && !Task.isCancelled
    }

    private enum PollOutcome {
        case met, timedOut, lost, cancelled
    }

    /// Asks for `status` until `condition` holds for a status that arrived
    /// during this poll. Skipped or failed refreshes don't count.
    private func poll(until timeout: TimeInterval, store: GadgetStore, id: Int, _ condition: (GadgetStatus) -> Bool) async -> PollOutcome {
        let deadline = Date.now.addingTimeInterval(timeout)
        while Date.now < deadline {
            try? await Task.sleep(for: Self.pollInterval)
            guard isCurrent(id) else { return .cancelled }
            if store.connectionLost || store.stage != .connected { return .lost }
            let fresh = await store.refreshStatus()
            guard isCurrent(id) else { return .cancelled }
            if store.connectionLost { return .lost }
            if fresh, let status = store.status, condition(status) { return .met }
        }
        return .timedOut
    }
}
