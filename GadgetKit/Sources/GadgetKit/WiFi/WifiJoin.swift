import Foundation
import Observation

/// Joining Wi-Fi: save the network, then watch `status` until the gadget is
/// on the network and its phase moves on to Hermes.
@MainActor @Observable
public final class WifiJoin {
    public enum Step: Equatable {
        case editing
        case saving
        case joining
        case reachingHermes
        /// Online; `approved` is false while Hermes still has to approve the gadget.
        case done(approved: Bool)
        /// On Wi-Fi, but Hermes never answered.
        case noHermes
        case failed(String)
        /// Saved for later with `wifi add`; the gadget joins it when it's in range.
        case saved
    }

    public enum Field: Equatable {
        case ssid, password
    }

    public private(set) var step: Step = .editing
    /// Bumped by each run and by `reset()`, so an abandoned run stops writing.
    private var runID = 0
    /// A refused `set`, shown under its field.
    private(set) var fieldError: (field: Field, message: String)?

    let joinTimeout: TimeInterval
    let hermesTimeout: TimeInterval
    let pollInterval: Duration

    public convenience init() {
        self.init(joinTimeout: 30, hermesTimeout: 30, pollInterval: .seconds(1))
    }

    init(joinTimeout: TimeInterval, hermesTimeout: TimeInterval, pollInterval: Duration) {
        self.joinTimeout = joinTimeout
        self.hermesTimeout = hermesTimeout
        self.pollInterval = pollInterval
    }

    public var isRunning: Bool {
        switch step {
        case .saving, .joining, .reachingHermes: true
        default: false
        }
    }

    public func error(for field: Field) -> String? {
        fieldError?.field == field ? fieldError?.message : nil
    }

    public func reset() {
        runID += 1
        step = .editing
        fieldError = nil
    }

    /// Saves the network and follows the join. An empty password keeps the
    /// saved one unless `clearPassword` (an open network) is set. Cancelling
    /// the calling task, or `reset()`, abandons the run.
    public func run(store: GadgetStore, ssid: String, password: String, clearPassword: Bool) async {
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
        await follow(store: store, ssid: ssid, id: id) {
            await store.refreshStatus() && store.status?.network == true
        }
    }

    /// Saves a network for later (firmware that keeps several networks). The
    /// gadget doesn't try it now, so the password isn't checked yet.
    public func save(store: GadgetStore, ssid: String, password: String) async {
        runID += 1
        let id = runID
        fieldError = nil
        step = .saving
        let error = await store.addWifi(ssid, password: password)
        guard isCurrent(id) else { return }
        if let error {
            fieldError = (Self.field(for: error), error)
            step = .editing
            return
        }
        step = .saved
    }

    /// Joins a saved network now, after saving it when `newPassword` is set (a
    /// new network, or a new password; empty for an open network), on firmware
    /// that keeps several networks. Follows the join until the gadget reports
    /// that network as its current one, then waits for Hermes as `run` does.
    public func join(store: GadgetStore, ssid: String, newPassword: String?) async {
        runID += 1
        let id = runID
        fieldError = nil
        step = .saving
        if let newPassword {
            let error = await store.addWifi(ssid, password: newPassword)
            guard isCurrent(id) else { return }
            if let error {
                fieldError = (Self.field(for: error), error)
                step = .editing
                return
            }
        }
        let error = await store.joinWifi(ssid)
        guard isCurrent(id) else { return }
        if let error {
            step = .failed(error)
            return
        }
        step = .joining
        let others = (store.wifiNetworks?.networks.count ?? 0) > 1
        await follow(store: store, ssid: ssid, id: id, fallsBack: others) {
            await store.loadWifiNetworks() == nil && store.wifiNetworks?.current == ssid
        }
    }

    /// Which field a refused `wifi add` belongs under.
    private static func field(for error: String) -> Field {
        error.lowercased().contains("password") ? .password : .ssid
    }

    /// From `.joining`: waits until `onNetwork`, then for Hermes. `fallsBack`:
    /// the gadget has other saved networks to go back to if this one fails.
    private func follow(store: GadgetStore, ssid: String, id: Int, fallsBack: Bool = false,
                        onNetwork: () async -> Bool) async {
        switch await poll(until: joinTimeout, store: store, id: id, onNetwork) {
        case .met:
            break
        case .cancelled:
            return
        case .lost:
            step = .failed(Self.lostMessage)
            return
        case .timedOut:
            let back = fallsBack ? " It goes back to its other saved networks." : ""
            step = .failed("\(ssid) didn't connect. Check the network name and password; the gadget joins 2.4 GHz networks only." + back)
            return
        }
        step = .reachingHermes
        let online = await poll(until: hermesTimeout, store: store, id: id) {
            await store.refreshStatus() && store.status?.phase == .online
        }
        switch online {
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

    /// Runs `check` until it holds. `check` asks the gadget again each time,
    /// and must count a skipped or failed refresh as not met.
    private func poll(until timeout: TimeInterval, store: GadgetStore, id: Int, _ check: () async -> Bool) async -> PollOutcome {
        let deadline = Date.now.addingTimeInterval(timeout)
        while Date.now < deadline {
            try? await Task.sleep(for: pollInterval)
            guard isCurrent(id) else { return .cancelled }
            if store.connectionLost || store.stage != .connected { return .lost }
            let met = await check()
            guard isCurrent(id) else { return .cancelled }
            if store.connectionLost { return .lost }
            if met { return .met }
        }
        return .timedOut
    }
}
