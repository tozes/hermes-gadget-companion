# Hermes Gadget Companion

An iOS app for [Hermes Gadget](https://github.com/Adolanium/hermes-gadget-sdk) devices. It pairs with a gadget over Bluetooth LE to set up Wi-Fi, change settings and show the device's status.

The gadget's side of the link is described in the SDK's [protocol guide](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/protocol.md#phone-app-over-bluetooth). The app sends the same console commands as the gadget's USB console. Bluetooth is experimental in the firmware and off on every board until it passes the SDK's [Bluetooth checks](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/hardware-validation.md#bluetooth).

## Status

Early scaffold: SwiftUI, iOS 17 or later. No Bluetooth code yet.

## Building

The app is a Swift package built with [xtool](https://github.com/xtool-org/xtool), so it builds on Linux as well as macOS.

```bash
xtool dev build       # builds xtool/HermesCompanion.app
xtool dev             # builds, signs and installs on a connected iPhone
```

Installing on a phone needs `xtool auth login` with your Apple ID. Without a paid developer account, the app's signature expires after seven days and needs reinstalling.

## License

MIT; see [LICENSE](LICENSE).
