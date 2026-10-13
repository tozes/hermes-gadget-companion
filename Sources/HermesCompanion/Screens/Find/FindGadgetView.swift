import SwiftUI

/// Find a gadget: scan, list what's nearby, and explain how to make one appear.
struct FindGadgetView: View {
    @Environment(GadgetStore.self) private var store
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(spacing: 0) {
            TopBar(title: "Hermes", linkColor: radioColor, linkLabel: radioLabel, spokenState: radioSpoken)
            ScrollView {
                VStack(spacing: 28) {
                    hero
                    if let name = store.forgottenName {
                        forgottenNote(name)
                    }
                    if store.radio == .poweredOn {
                        gadgets
                    } else if store.radio == .unauthorized {
                        GadgetButton("Open Settings") { openSettings() }
                    }
                    VStack(alignment: .leading, spacing: 12) {
                        SectionLabel("Make a gadget appear")
                        PairingSteps()
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 28)
            }
            HintLine(hint)
                .background(Palette.background)
        }
        .background(Palette.background.ignoresSafeArea())
        .onAppear { store.startScan() }
        .onDisappear { store.stopScan() }
    }

    // MARK: Hero

    @ViewBuilder
    private var hero: some View {
        switch store.radio {
        case .unknown:
            Hero(indicator: .spinner(Palette.accent), headline: "Starting Bluetooth", color: Palette.accent)
        case .poweredOff:
            Hero(indicator: .alert, headline: "Bluetooth is off", color: Palette.red,
                 detail: "Turn on Bluetooth in Control Center or Settings to find your gadget.")
        case .unauthorized:
            Hero(indicator: .alert, headline: "Bluetooth not allowed", color: Palette.red,
                 detail: "Hermes uses Bluetooth to pair with your gadget. Allow it in Settings, under Privacy & Security > Bluetooth.")
        case .unsupported:
            Hero(indicator: .alert, headline: "No Bluetooth LE", color: Palette.red,
                 detail: "This device can't connect to a gadget.")
        case .poweredOn:
            if store.isScanning && store.found.isEmpty {
                Hero(indicator: .spinner(Palette.accent), headline: "Looking for gadgets", color: Palette.accent,
                     detail: "Open Phone app on your gadget and it shows up here.")
            } else if store.found.isEmpty {
                Hero(indicator: .question, headline: "No gadgets found", color: Palette.text,
                     detail: "A gadget only shows up while its Phone app pairing is open, or once it's paired with this iPhone.")
            } else {
                Hero(headline: store.found.count == 1 ? "Found a gadget" : "Found \(store.found.count) gadgets",
                     detail: "Pick yours to pair with it.")
            }
        }
    }

    // MARK: Found gadgets

    private var gadgets: some View {
        VStack(alignment: .leading, spacing: 12) {
            if !store.found.isEmpty {
                SectionLabel("Nearby")
                    .overlay(alignment: .topTrailing) {
                        if store.isScanning {
                            StatusIndicator(kind: .spinner(Palette.accent), size: 14)
                        }
                    }
                Panel {
                    ForEach(Array(store.found.enumerated()), id: \.element.id) { index, gadget in
                        if index > 0 { PanelDivider() }
                        GadgetRow(gadget: gadget) { store.connect(to: gadget) }
                    }
                }
            }
            if !store.isScanning {
                GadgetButton("Scan again", kind: store.found.isEmpty ? .primary : .secondary) {
                    store.startScan()
                }
            }
        }
    }

    private func forgottenNote(_ name: String) -> some View {
        Panel {
            VStack(alignment: .leading, spacing: 8) {
                PixelText("\(name) forgot this iPhone", scale: 2, color: Palette.accent, wraps: true)
                Text("If you pair again and iPhone doesn't ask for a code, first remove \(name) under Settings > Bluetooth.")
                    .font(.subheadline)
                    .foregroundStyle(Palette.dim)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
        }
        .accessibilityElement(children: .combine)
    }

    // MARK: Top bar and hint

    private var radioColor: Color {
        switch store.radio {
        case .poweredOn: Palette.green
        case .unknown: Palette.yellow
        default: Palette.red
        }
    }

    private var radioLabel: String {
        switch store.radio {
        case .poweredOn: store.isScanning ? "SCAN" : "BT"
        case .unknown: "BT"
        default: "NO BT"
        }
    }

    private var radioSpoken: String {
        switch store.radio {
        case .poweredOn: store.isScanning ? "Bluetooth on, scanning" : "Bluetooth on"
        case .unknown: "Bluetooth starting"
        case .poweredOff: "Bluetooth off"
        case .unauthorized: "Bluetooth not allowed"
        case .unsupported: "Bluetooth unavailable"
        }
    }

    private var hint: String {
        switch store.radio {
        case .poweredOn: store.isScanning ? "Scanning for 20 seconds" : "Pairing stays open 2 minutes"
        case .poweredOff: "Bluetooth is off"
        case .unauthorized: "Bluetooth needs permission"
        default: "Hermes Gadget"
        }
    }

    private func openSettings() {
        // UIApplication.openSettingsURLString, without importing UIKit.
        if let url = URL(string: "app-settings:") { openURL(url) }
    }
}
