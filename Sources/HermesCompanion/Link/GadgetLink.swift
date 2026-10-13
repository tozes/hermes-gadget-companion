import Foundation

/// The phone's Bluetooth radio, as Core Bluetooth reports it.
enum RadioState: Sendable, Equatable {
    case unknown
    case poweredOn
    case poweredOff
    /// The user hasn't allowed Bluetooth for this app.
    case unauthorized
    case unsupported
}

/// A gadget seen while scanning: it advertises the Hermes service while
/// Phone app pairing is open, and about once a second while a phone is paired.
struct DiscoveredGadget: Identifiable, Hashable, Sendable {
    let id: UUID
    var name: String
    /// Signal strength in dBm.
    var rssi: Int

    /// 1 (weak) to 4 (strong).
    var signalLevel: Int {
        switch rssi {
        case (-55)...: 4
        case (-67)...: 3
        case (-80)...: 2
        default: 1
        }
    }

    var signalDescription: String {
        ["weak", "fair", "good", "strong"][signalLevel - 1]
    }
}

/// Why a connection didn't come up or went away.
enum LinkFailure: Error, Sendable, Equatable {
    /// iOS reported that pairing failed: a wrong code, or the user cancelled the dialog.
    case pairingRejected
    /// No pairing happened in time: usually the gadget's pairing window had closed.
    case pairingTimedOut
    /// iOS still has keys the gadget has dropped (after `phone forget` or a
    /// factory reset): the phone must forget the gadget before pairing again.
    case pairingRemoved
    /// The gadget couldn't be reached.
    case unreachable
    /// An established link dropped.
    case connectionLost
}

/// What a link reports to its owner. All events arrive on the main actor.
enum LinkEvent: Sendable, Equatable {
    case radio(RadioState)
    case discovered(DiscoveredGadget)
    case connecting
    /// Connected and waiting on the first encrypted write, which makes iOS
    /// show its pairing dialog. The passkey is on the gadget's screen; the
    /// app never sees it.
    case awaitingPasskey
    /// Paired, subscribed to replies, and ready for commands.
    case connected
    case failed(LinkFailure)
    /// The link closed: `nil` when the app asked for it.
    case disconnected(LinkFailure?)
}

enum SendError: Error, Sendable, Equatable {
    case notConnected
    case timedOut
}

/// A Bluetooth LE link to a gadget's console (see the SDK's protocol guide,
/// "Phone app over Bluetooth"). The phone writes one console line and gets
/// one `@` reply. `BluetoothGadgetLink` is the real one; `MockGadgetLink`
/// acts out the debug menu's scenarios.
@MainActor
protocol GadgetLink: AnyObject {
    /// Receives every event; the store sets it once.
    var onEvent: (@MainActor (LinkEvent) -> Void)? { get set }
    var radio: RadioState { get }

    func startScan()
    func stopScan()
    /// Connects and pairs; progress and the outcome arrive as events.
    func connect(to gadget: DiscoveredGadget)
    func disconnect()
    /// Writes one console line (UTF-8, at most 256 bytes) and returns the
    /// reply text without its terminating NUL. Callers send one line at a time.
    func send(_ line: String) async throws -> String
}
