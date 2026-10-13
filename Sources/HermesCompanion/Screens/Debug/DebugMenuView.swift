import GadgetKit
import SwiftUI

/// The hidden debug menu: switch mock scenarios and trigger mock events.
struct DebugMenuView: View {
    @Environment(GadgetStore.self) private var store
    @Environment(\.dismiss) private var dismiss

    private var mock: MockGadgetLink? { store.link as? MockGadgetLink }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    Text("Pick a mock scenario to try a screen without a gadget, or real Bluetooth to talk to one. The choice is kept across launches; the launch argument -mockScenario <name> sets a scenario too.")
                        .font(.footnote)
                        .foregroundStyle(Palette.dim)

                    if let mock {
                        VStack(alignment: .leading, spacing: 12) {
                            SectionLabel("Events")
                            GadgetButton("Approve on Hermes", kind: .secondary) {
                                mock.approveOnHermes()
                                Task { await store.refreshStatus() }
                            }
                            GadgetButton("Drop the connection", kind: .secondary) { mock.drop() }
                            // The store's radio is observable; the mock's isn't.
                            GadgetButton(store.radio == .poweredOn ? "Turn Bluetooth off" : "Turn Bluetooth on", kind: .secondary) {
                                mock.setRadio(store.radio == .poweredOn ? .poweredOff : .poweredOn)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel("Scenarios")
                        Panel {
                            realRow
                            PanelDivider()
                            ForEach(Array(MockScenario.allCases.enumerated()), id: \.element) { index, scenario in
                                if index > 0 { PanelDivider() }
                                scenarioRow(scenario)
                            }
                        }
                    }
                }
                .padding(20)
            }
            .gadgetScreen()
            .gadgetNavigation(title: "Debug")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private var realRow: some View {
        Button {
            UserDefaults.standard.removeObject(forKey: MockScenario.defaultsKey)
            store.replaceLink(BluetoothGadgetLink())
            dismiss()
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(mock == nil ? Palette.accent : Palette.faint)
                    .frame(width: 10, height: 10)
                    .padding(.top, 6)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Real Bluetooth").font(.body).foregroundStyle(Palette.text)
                    Text("Find and pair with gadgets nearby.").font(.footnote).foregroundStyle(Palette.dim)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(mock == nil ? .isSelected : [])
    }

    private func scenarioRow(_ scenario: MockScenario) -> some View {
        Button {
            UserDefaults.standard.set(scenario.rawValue, forKey: MockScenario.defaultsKey)
            store.replaceLink(MockGadgetLink(scenario: scenario))
            dismiss()
        } label: {
            HStack(alignment: .top, spacing: 12) {
                Circle()
                    .fill(mock?.scenario == scenario ? Palette.accent : Palette.faint)
                    .frame(width: 10, height: 10)
                    .padding(.top, 6)
                VStack(alignment: .leading, spacing: 4) {
                    Text(scenario.title).font(.body).foregroundStyle(Palette.text)
                    Text(scenario.detail).font(.footnote).foregroundStyle(Palette.dim)
                    Text(scenario.rawValue).font(.caption.monospaced()).foregroundStyle(Palette.accent)
                }
                Spacer(minLength: 0)
            }
            .padding(16)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(mock?.scenario == scenario ? .isSelected : [])
    }
}
