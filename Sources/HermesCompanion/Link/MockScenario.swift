import Foundation

/// The states the mock link can act out, picked in the debug menu (long-press
/// any screen's title) or with the launch argument `-mockScenario <rawValue>`.
/// Without one, the app uses real Bluetooth.
enum MockScenario: String, CaseIterable, Identifiable, Sendable {
    case newGadget
    case online
    case awaitingApproval
    case lowBattery
    case nothingNearby
    case bluetoothOff
    case bluetoothDenied
    case pairingRejected
    case pairingTimesOut
    case wifiFails
    case hermesUnreachable
    case flaky

    static let defaultsKey = "mockScenario"

    /// The scenario from the launch argument or the last debug-menu choice;
    /// nil means real Bluetooth.
    static var current: MockScenario? {
        UserDefaults.standard.string(forKey: defaultsKey).flatMap(MockScenario.init(rawValue:))
    }

    var id: String { rawValue }

    var title: String {
        switch self {
        case .newGadget: "New gadget, no Wi-Fi"
        case .online: "Online and approved"
        case .awaitingApproval: "Waiting for Hermes approval"
        case .lowBattery: "Low battery"
        case .nothingNearby: "No gadgets nearby"
        case .bluetoothOff: "Bluetooth off"
        case .bluetoothDenied: "Bluetooth not allowed"
        case .pairingRejected: "Pairing fails"
        case .pairingTimesOut: "Pairing times out"
        case .wifiFails: "Wi-Fi join fails"
        case .hermesUnreachable: "Wi-Fi works, Hermes doesn't"
        case .flaky: "Busy replies and dropped links"
        }
    }

    var detail: String {
        switch self {
        case .newGadget: "The status from a real device: no network, not approved. Wi-Fi setup succeeds."
        case .online: "Set up, approved, charging over USB."
        case .awaitingApproval: "Online, showing a pairing code for the Hermes host."
        case .lowBattery: "Online, on battery at 9%."
        case .nothingNearby: "The scan finds nothing and ends after 20 seconds."
        case .bluetoothOff: "The phone's Bluetooth is off."
        case .bluetoothDenied: "The app isn't allowed to use Bluetooth."
        case .pairingRejected: "iOS reports a wrong code."
        case .pairingTimesOut: "The pairing window closed before the code was entered."
        case .wifiFails: "Joining Wi-Fi never brings the network up."
        case .hermesUnreachable: "Wi-Fi joins, but the server never answers."
        case .flaky: "Some commands get \"@error device busy\"; the link drops after a minute."
        }
    }

    var radio: RadioState {
        switch self {
        case .bluetoothOff: .poweredOff
        case .bluetoothDenied: .unauthorized
        default: .poweredOn
        }
    }
}
