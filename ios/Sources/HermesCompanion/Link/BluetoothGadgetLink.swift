import CoreBluetooth
import Foundation
import GadgetKit

/// The real link: Core Bluetooth to the gadget's console service
/// (`GadgetService`). Delegate callbacks arrive on the main queue.
///
/// Connecting goes: connect, find the service, subscribe to replies, then
/// write a first command (`phone`). That write needs an encrypted link, so on a
/// phone that hasn't paired yet iOS shows its pairing dialog, and the gadget
/// shows the code. Once the reply arrives, the link is connected.
@MainActor
final class BluetoothGadgetLink: NSObject, GadgetLink {
    var onEvent: (@MainActor (LinkEvent) -> Void)?
    private(set) var radio: RadioState = .unknown

    /// Core Bluetooth never gives up on a connect by itself.
    static let connectTimeout: Duration = .seconds(15)
    /// The gadget closes pairing two minutes after it opens.
    static let pairingTimeout: Duration = .seconds(120)
    /// The gadget answers within 3 s or says `@error device busy`.
    static let replyTimeout: Duration = .seconds(10)
    /// A bonded phone's first reply comes back quickly: only say "enter the
    /// code" when it doesn't.
    static let passkeyHintDelay: Duration = .milliseconds(800)
    /// A gadget whose pairing is closed drops the link as soon as pairing
    /// starts; a wrong code takes longer than this to type.
    static let refusedWithin: TimeInterval = 3

    private enum Phase {
        case idle, connecting, discovering, awaitingPasskey, connected
    }

    private static let serviceUUID = CBUUID(string: GadgetService.service)
    private static let commandUUID = CBUUID(string: GadgetService.command)
    private static let replyUUID = CBUUID(string: GadgetService.reply)

    private var central: CBCentralManager!
    private var wantsScan = false
    /// Peripherals seen while scanning, so `connect` can find them again.
    private var known: [UUID: CBPeripheral] = [:]
    private var peripheral: CBPeripheral?
    private var command: CBCharacteristic?
    private var replies: CBCharacteristic?
    private var phase: Phase = .idle
    private var probeStarted: Date?
    private var assembler = ReplyAssembler()
    private var pending: CheckedContinuation<String, Error>?
    private var pendingTimeout: Task<Void, Never>?
    private var phaseTimeout: Task<Void, Never>?
    private var passkeyHint: Task<Void, Never>?

    /// A write failed for want of pairing: the dialog was cancelled or the code was wrong.
    private struct NotPaired: Error {}
    /// The gadget no longer has this phone's keys.
    private struct PairingRemoved: Error {}

    override init() {
        super.init()
        central = CBCentralManager(delegate: self, queue: .main)
    }

    // MARK: GadgetLink

    func startScan() {
        wantsScan = true
        guard central.state == .poweredOn else { return }
        // A gadget already connected to this iPhone (by another app, or iOS
        // itself) has stopped advertising: list it too.
        for peripheral in central.retrieveConnectedPeripherals(withServices: [Self.serviceUUID]) {
            known[peripheral.identifier] = peripheral
            onEvent?(.discovered(DiscoveredGadget(id: peripheral.identifier, name: peripheral.name ?? "Hermes Gadget", rssi: -60)))
        }
        central.scanForPeripherals(withServices: [Self.serviceUUID],
                                   options: [CBCentralManagerScanOptionAllowDuplicatesKey: true])
    }

    func stopScan() {
        wantsScan = false
        if central.isScanning { central.stopScan() }
    }

    func connect(to gadget: DiscoveredGadget) {
        teardown(cancelConnection: true)
        guard central.state == .poweredOn,
              let target = known[gadget.id] ?? central.retrievePeripherals(withIdentifiers: [gadget.id]).first
        else {
            onEvent?(.failed(.unreachable))
            return
        }
        peripheral = target
        target.delegate = self
        phase = .connecting
        onEvent?(.connecting)
        central.connect(target)
        startPhaseTimeout(Self.connectTimeout, failure: .unreachable)
    }

    func disconnect() {
        teardown(cancelConnection: true)
    }

    func send(_ line: String) async throws -> String {
        guard phase == .connected else { throw SendError.notConnected }
        do {
            return try await write(line, timeout: Self.replyTimeout)
        } catch let error as SendError {
            throw error
        } catch {
            throw SendError.notConnected
        }
    }

    // MARK: Connecting

    private func peripheralConnected() {
        guard phase == .connecting, let peripheral else { return }
        phase = .discovering
        peripheral.discoverServices([Self.serviceUUID])
    }

    private func servicesFound(_ error: Error?) {
        guard phase == .discovering, let peripheral else { return }
        guard error == nil, let service = peripheral.services?.first(where: { $0.uuid == Self.serviceUUID }) else {
            return fail(.unreachable)
        }
        peripheral.discoverCharacteristics([Self.commandUUID, Self.replyUUID], for: service)
    }

    private func characteristicsFound(_ service: CBService, _ error: Error?) {
        guard phase == .discovering, let peripheral else { return }
        let characteristics = service.characteristics ?? []
        command = characteristics.first { $0.uuid == Self.commandUUID }
        replies = characteristics.first { $0.uuid == Self.replyUUID }
        guard error == nil, command != nil, let replies else { return fail(.unreachable) }
        peripheral.setNotifyValue(true, for: replies)
    }

    private func subscribed(_ characteristic: CBCharacteristic, _ error: Error?) {
        guard phase == .discovering, characteristic.uuid == Self.replyUUID else { return }
        guard error == nil, characteristic.isNotifying else { return fail(.unreachable) }
        probe()
    }

    /// The first command, which makes iOS pair if it hasn't.
    private func probe() {
        phase = .awaitingPasskey
        probeStarted = .now
        startPhaseTimeout(Self.pairingTimeout, failure: .pairingTimedOut)
        passkeyHint = Task { [weak self] in
            try? await Task.sleep(for: Self.passkeyHintDelay)
            guard !Task.isCancelled, let self, self.phase == .awaitingPasskey else { return }
            self.onEvent?(.awaitingPasskey)
        }
        Task { [weak self] in
            guard let self else { return }
            do {
                _ = try await self.write("phone", timeout: Self.pairingTimeout)
                guard self.phase == .awaitingPasskey else { return }
                self.phase = .connected
                self.phaseTimeout?.cancel()
                self.passkeyHint?.cancel()
                self.onEvent?(.connected)
            } catch is PairingRemoved {
                self.fail(.pairingRemoved)
            } catch is NotPaired {
                self.fail(.pairingRejected)
            } catch {
                // A timeout or a dropped link: teardown has already reported it.
                if self.phase == .awaitingPasskey { self.fail(.pairingTimedOut) }
            }
        }
    }

    // MARK: Writing

    private func write(_ line: String, timeout: Duration) async throws -> String {
        guard let peripheral, let command, pending == nil else { throw SendError.notConnected }
        assembler.reset()
        return try await withCheckedThrowingContinuation { continuation in
            pending = continuation
            peripheral.writeValue(Data(line.utf8), for: command, type: .withResponse)
            pendingTimeout = Task { [weak self] in
                try? await Task.sleep(for: timeout)
                guard !Task.isCancelled else { return }
                self?.finishPending(.failure(SendError.timedOut))
            }
        }
    }

    private func finishPending(_ result: Result<String, Error>) {
        pendingTimeout?.cancel()
        pendingTimeout = nil
        guard let continuation = pending else { return }
        pending = nil
        continuation.resume(with: result)
    }

    private func wrote(_ error: Error?) {
        guard let error else { return }  // the reply follows as notifications
        finishPending(.failure(Self.writeFailure(error)))
    }

    private static func writeFailure(_ error: Error) -> Error {
        if let cb = error as? CBError, cb.code == .peerRemovedPairingInformation { return PairingRemoved() }
        if let att = error as? CBATTError,
           [.insufficientAuthentication, .insufficientEncryption, .insufficientEncryptionKeySize].contains(att.code) {
            return NotPaired()
        }
        return SendError.notConnected
    }

    private func received(_ data: Data?) {
        guard let data else { return }
        for reply in assembler.append(data) {
            // A reply nobody waits for (after a timeout) is dropped.
            finishPending(.success(reply))
        }
    }

    // MARK: Ending

    private func startPhaseTimeout(_ duration: Duration, failure: LinkFailure) {
        phaseTimeout?.cancel()
        phaseTimeout = Task { [weak self] in
            try? await Task.sleep(for: duration)
            guard !Task.isCancelled else { return }
            self?.fail(failure)
        }
    }

    /// Ends a connect that didn't work and reports why.
    private func fail(_ failure: LinkFailure) {
        guard phase != .idle else { return }
        teardown(cancelConnection: true)
        onEvent?(.failed(failure))
    }

    /// The link went away without the app asking.
    private func lost(_ error: Error?) {
        let was = phase
        let elapsed = probeStarted.map { Date.now.timeIntervalSince($0) } ?? 0
        teardown(cancelConnection: false)
        switch was {
        case .idle:
            return
        case .connected:
            onEvent?(.disconnected(.connectionLost))
        case .connecting, .discovering:
            onEvent?(.failed(.unreachable))
        case .awaitingPasskey:
            if let cb = error as? CBError, cb.code == .peerRemovedPairingInformation {
                onEvent?(.failed(.pairingRemoved))
            } else {
                // The gadget drops the link both when its pairing window is closed
                // (at once) and after a wrong code (once it's typed).
                onEvent?(.failed(elapsed < Self.refusedWithin ? .pairingTimedOut : .pairingRejected))
            }
        }
    }

    private func teardown(cancelConnection: Bool) {
        phaseTimeout?.cancel()
        passkeyHint?.cancel()
        phaseTimeout = nil
        passkeyHint = nil
        phase = .idle
        probeStarted = nil
        finishPending(.failure(SendError.notConnected))
        assembler.reset()
        if let peripheral, cancelConnection { central.cancelPeripheralConnection(peripheral) }
        peripheral?.delegate = nil
        peripheral = nil
        command = nil
        replies = nil
    }

    private func radioChanged() {
        let state: RadioState = switch central.state {
        case .poweredOn: .poweredOn
        case .poweredOff, .resetting: .poweredOff
        case .unauthorized: .unauthorized
        case .unsupported: .unsupported
        default: .unknown
        }
        radio = state
        if state != .poweredOn, phase != .idle {
            // Core Bluetooth drops connections without a disconnect callback here.
            lost(nil)
        }
        onEvent?(.radio(state))
        if state == .poweredOn, wantsScan { startScan() }
    }

    private func discovered(_ peripheral: CBPeripheral, _ advertisement: [String: Any], _ rssi: NSNumber) {
        known[peripheral.identifier] = peripheral
        let value = rssi.intValue
        guard value != 127 else { return }  // "not available"
        let name = advertisement[CBAdvertisementDataLocalNameKey] as? String ?? peripheral.name ?? "Hermes Gadget"
        onEvent?(.discovered(DiscoveredGadget(id: peripheral.identifier, name: name, rssi: value)))
    }

    private func isCurrent(_ other: CBPeripheral) -> Bool {
        other.identifier == peripheral?.identifier
    }
}

// Core Bluetooth calls these on the main queue (see `init`); the
// @preconcurrency conformances check that at run time.
extension BluetoothGadgetLink: @preconcurrency CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        radioChanged()
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral,
                        advertisementData: [String: Any], rssi RSSI: NSNumber) {
        discovered(peripheral, advertisementData, RSSI)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        guard isCurrent(peripheral) else { return }
        peripheralConnected()
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral,
                        error: Error?) {
        guard isCurrent(peripheral) else { return }
        fail(.unreachable)
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral,
                        error: Error?) {
        guard isCurrent(peripheral) else { return }
        lost(error)
    }
}

extension BluetoothGadgetLink: @preconcurrency CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        guard isCurrent(peripheral) else { return }
        servicesFound(error)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService,
                    error: Error?) {
        guard isCurrent(peripheral) else { return }
        characteristicsFound(service, error)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic,
                    error: Error?) {
        guard isCurrent(peripheral) else { return }
        subscribed(characteristic, error)
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        guard isCurrent(peripheral) else { return }
        wrote(error)
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic,
                    error: Error?) {
        guard isCurrent(peripheral), characteristic.uuid == Self.replyUUID else { return }
        received(characteristic.value)
    }
}
