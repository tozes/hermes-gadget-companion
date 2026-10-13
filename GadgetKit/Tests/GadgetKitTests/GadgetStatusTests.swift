import Foundation
import Testing
@testable import GadgetKit

struct GadgetStatusTests {
    private func decode(_ json: String) throws -> GadgetStatus {
        let decoder = JSONDecoder()
        decoder.keyDecodingStrategy = .convertFromSnakeCase
        return try decoder.decode(GadgetStatus.self, from: Data(json.utf8))
    }

    @Test func realSample() throws {
        guard case .status(let status) = Reply.parse(statusSample) else {
            Issue.record("the sample didn't decode")
            return
        }
        #expect(status.deviceId == "hg-5a1c9e2f7b3d4086")
        #expect(status.name == "Nox")
        #expect(status.board == "esp32s3-touch-amoled-1.75c")
        #expect(status.firmware == "0.2.0")
        #expect(status.phase == .noNetwork)
        #expect(status.screen == "offline")
        #expect(status.paired == false)
        #expect(status.pairingCode == nil)
        #expect(status.server == "wss://hermes.example.com:8765/gadget")
        #expect(status.network == false)
        #expect(status.displaySleeping == false)
        let power = try #require(status.power)
        #expect(power.available == true)
        #expect(power.batteryPresent == true)
        #expect(power.batteryMv == 4126)
        #expect(power.batteryPercent == 84)
        #expect(power.charging == true)
        #expect(power.externalPower == true)
        #expect(status.phone == PhoneState(paired: 1, connected: true, pairing: false))
    }

    @Test func emptyObjectDecodes() throws {
        let status = try decode("{}")
        #expect(status == GadgetStatus())
    }

    @Test func missingOptionalFields() throws {
        let status = try decode(#"{"name":"Kitchen","phase":"online","power":{"available":false}}"#)
        #expect(status.name == "Kitchen")
        #expect(status.phase == .online)
        #expect(status.network == nil)
        #expect(status.phone == nil)
        #expect(status.power == GadgetStatus.Power(available: false))
        #expect(status.power?.batteryPercent == nil)
    }

    @Test func pairingCode() throws {
        let status = try decode(#"{"phase":"online","screen":"pairing","pairing_code":"YGJEQNXU","paired":false}"#)
        #expect(status.pairingCode == "YGJEQNXU")
    }

    @Test(arguments: [
        ("boot", GadgetStatus.Phase.boot),
        ("no_network", .noNetwork),
        ("connecting", .connecting),
        ("handshake", .handshake),
        ("online", .online),
        ("updating", .other("updating")),
    ])
    func phases(raw: String, phase: GadgetStatus.Phase) throws {
        #expect(try decode(#"{"phase":"\#(raw)"}"#).phase == phase)
        #expect(phase.rawValue == raw)
    }

    @Test func phaseRoundTrips() throws {
        let status = GadgetStatus(name: "Nox", phase: .other("updating"))
        let encoder = JSONEncoder()
        encoder.keyEncodingStrategy = .convertToSnakeCase
        let json = String(decoding: try encoder.encode(status), as: UTF8.self)
        #expect(try decode(json) == status)
    }
}
