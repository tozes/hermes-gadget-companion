import GadgetKit
import SwiftUI

/// The gadget at a glance: power, connection, firmware and board, from `status`.
struct StatusDetails: View {
    let status: GadgetStatus

    var body: some View {
        Panel {
            InfoRow(label: "Battery", value: battery, valueColor: status.power?.isLow == true ? Palette.red : Palette.text)
            PanelDivider()
            InfoRow(label: "Wi-Fi", value: status.network == true ? "Connected" : "Not connected",
                    valueColor: status.network == true ? Palette.text : Palette.red)
            PanelDivider()
            InfoRow(label: "Hermes", value: hermes)
            if let server = status.server, !server.isEmpty {
                PanelDivider()
                InfoRow(label: "Server", value: server, monospaced: true, breaksAfter: ["/", "."])
            }
            PanelDivider()
            InfoRow(label: "Firmware", value: status.firmware ?? "Unknown")
            PanelDivider()
            InfoRow(label: "Board", value: status.board ?? "Unknown", monospaced: true)
            if let id = status.deviceId {
                PanelDivider()
                InfoRow(label: "Device ID", value: id, monospaced: true)
            }
            if let phones = status.phone?.paired {
                PanelDivider()
                InfoRow(label: "Phones", value: phones == 1 ? "1 paired" : "\(phones) paired")
            }
            if status.displaySleeping == true {
                PanelDivider()
                InfoRow(label: "Display", value: "Asleep")
            }
        }
    }

    private var battery: String {
        guard let power = status.power, let summary = power.summary else { return "Not reported by this board" }
        guard let voltage = power.voltage else { return summary }
        return "\(summary) (\(voltage))"
    }

    private var hermes: String {
        switch status.phase {
        case .online: status.paired == false ? "Online, waiting for approval" : "Online and approved"
        case .connecting: "Connecting"
        case .handshake: "Signing in"
        case .boot: "Starting up"
        case .noNetwork, nil: "Offline"
        case .other(let raw): raw
        }
    }
}
