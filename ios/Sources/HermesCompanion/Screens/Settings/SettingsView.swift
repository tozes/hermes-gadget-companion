import GadgetKit
import SwiftUI

/// The gadget's settings, read with `get` and written with `set`. Each one
/// saves on its own, and the gadget's `@error` shows under that setting.
struct SettingsView: View {
    @Environment(GadgetStore.self) private var store

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if store.isLoadingSettings {
                    HStack(spacing: 10) {
                        StatusIndicator(kind: .spinner(Palette.accent), size: 16)
                        PixelText("Reading settings", scale: 2, color: Palette.dim)
                    }
                } else if store.missingSettings {
                    Text("Some settings didn't load. Pull down to try again.")
                        .font(.footnote)
                        .foregroundStyle(Palette.red)
                }

                section("Gadget") {
                    TextSettingRow(key: .name, label: "Name", prompt: "Gadget name",
                                   note: "Shown on the gadget and when your phone looks for it.")
                }

                section("Sound and talking") {
                    SliderSettingRow(key: .volume, label: "Volume", range: SettingKey.volumeRange)
                    PanelDivider()
                    ChoiceSettingRow(
                        key: .talkMode, label: "Talk mode",
                        options: TalkMode.allCases.map { .init(value: $0.rawValue, title: $0.title, spoken: "\($0.title) to talk") },
                        note: { $0.flatMap(TalkMode.init(rawValue:))?.explanation }
                    )
                }

                section("Screen") {
                    SliderSettingRow(key: .brightness, label: "Brightness", range: SettingKey.brightnessRange)
                    PanelDivider()
                    ScreenTimeoutRow()
                    PanelDivider()
                    ChoiceSettingRow(
                        key: .batteryDisplay, label: "Battery indicator",
                        options: BatteryDisplay.allCases.map { .init(value: $0.rawValue, title: $0.title, spoken: $0.spokenTitle) },
                        note: { _ in "Shown at the top of the gadget's screen, when the board reports a battery level." }
                    )
                }

                section("Hermes") {
                    TextSettingRow(key: .server, label: "Server", prompt: "wss://host:port/path",
                                   note: "The gadget reconnects when the address or token changes.",
                                   monospaced: true, isURL: true)
                    PanelDivider()
                    SecretSettingRow(key: .token, label: "Access token")
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .refreshable { await store.loadSettings(all: true) }
        .gadgetScreen()
        .gadgetNavigation(title: "Settings")
        .task {
            if store.missingSettings { await store.loadSettings() }
        }
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionLabel(title)
            Panel { content() }
                .disabled(store.connectionLost)
        }
    }
}
