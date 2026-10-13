import GadgetKit
import SwiftUI

/// Details and tools most people don't need: the gadget's status in full
/// (server, IDs, voltage), the console, and forgetting the gadget.
struct AdvancedView: View {
    @Environment(GadgetStore.self) private var store

    private var name: String { store.status?.name ?? store.gadget?.name ?? "Gadget" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if store.isDemo {
                    DemoNote()
                }
                if let status = store.status {
                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel("Gadget")
                        StatusDetails(status: status)
                    }
                }
                VStack(alignment: .leading, spacing: 12) {
                    SectionLabel("Tools")
                    Panel {
                        NavigationRow(title: "Console", summary: "Type console commands", value: HomeRoute.console)
                    }
                    .disabled(store.connectionLost)
                }
                ForgetGadgetSection(name: name)
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .refreshable { await store.refreshStatus() }
        .gadgetScreen()
        .gadgetNavigation(title: "Advanced")
    }
}
