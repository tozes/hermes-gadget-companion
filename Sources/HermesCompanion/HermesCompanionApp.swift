import GadgetKit
import SwiftUI

@main
struct HermesCompanionApp: App {
    // Real Bluetooth, unless the debug menu picked a mock scenario.
    @State private var store = GadgetStore(link: MockScenario.current.map { MockGadgetLink(scenario: $0) } ?? BluetoothGadgetLink())

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(store)
        }
    }
}
