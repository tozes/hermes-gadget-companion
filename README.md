# Hermes Gadget Companion

An iOS and Android app for [Hermes Gadget](https://github.com/Adolanium/hermes-gadget-sdk) devices. It pairs with a gadget over Bluetooth LE to set up Wi-Fi, change settings and show the device's status.

The gadget's side of the link is described in the SDK's [protocol guide](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/protocol.md#phone-app-over-bluetooth). The app sends the same console commands as the gadget's USB console. Bluetooth is experimental in the firmware and off on every board until it passes the SDK's [Bluetooth checks](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/hardware-validation.md#bluetooth).

## Screenshots

Taken in the app's demo, so the gadget, networks and server are made up.

<p>
  <img src="docs/screenshots/home.png" width="160" alt="Home: the mascot, Online, and rows for Wi-Fi, Settings and Advanced">
  <img src="docs/screenshots/wifi.png" width="160" alt="Wi-Fi: on Lisbon Loft, two saved networks, Add a network">
  <img src="docs/screenshots/settings.png" width="160" alt="Settings: name, volume, talk mode, brightness, screen timeout">
  <img src="docs/screenshots/advanced.png" width="160" alt="Advanced: battery, Wi-Fi, Hermes, server, firmware, board, device ID, console and Forget">
  <img src="docs/screenshots/console.png" width="160" alt="Console: quick commands and the gadget> prompt">
</p>

## Status

The SwiftUI interface, iOS 17 or later, with a Core Bluetooth link to the gadget (`BluetoothGadgetLink`) behind the `GadgetLink` protocol. `MockGadgetLink` acts out mock scenarios for trying screens without a gadget.

Without a gadget, tap **Try the demo** on the first screen: pretend gadgets to explore every screen, also handy for screenshots. It isn't kept across launches. To switch between real Bluetooth and the other mock scenarios, long-press any screen's title for a second to open the debug menu. The choice is kept across launches; the launch argument `-mockScenario <name>` sets it too.

The pixel font, the mascot frames and the app icon come from the SDK's firmware. To regenerate them (the icon needs Pillow):

```bash
python3 scripts/generate-assets.py ~/path/to/hermes-gadget-sdk
python3 scripts/make-icon.py ~/path/to/hermes-gadget-sdk
```

## Building

The app is a Swift package built with [xtool](https://github.com/xtool-org/xtool), so it builds on Linux as well as macOS.

```bash
xtool dev build       # builds xtool/HermesCompanion.app
xtool dev             # builds, signs and installs on a connected iPhone
```

The app's logic is in the `GadgetKit` package, which needs only Foundation: `swift test --package-path GadgetKit` runs its tests on Linux or macOS.

Installing on a phone needs `xtool auth login` with your Apple ID. Without a paid developer account, the app's signature expires after seven days and needs reinstalling.

## Android

The Android app (in `android/`, for Android 10 or later) has the same screens, states and copy as the iOS app, styled the same way, with a Jetpack Compose interface and a Bluetooth LE link built on Android's `BluetoothGatt`.

**Status:** it builds, its logic has tests, and every screen works against the mock gadget, but **its Bluetooth link hasn't been tried on a phone yet**. The app's logic is a Kotlin port of GadgetKit (`android/core`), tested on the JVM against the same reply samples as the Swift tests (`fixtures/`), and so is the radio-free half of the link: its operation queue, pairing and failure mapping. Scanning, pairing and talking to a real gadget still need checking on a device.

Build it with JDK 21 and the Android SDK (platform 37.2), then install the debug APK on a phone with USB debugging on:

```bash
cd android
./gradlew :core:test :app:assembleDebug     # tests, then app/build/outputs/apk/debug/app-debug.apk
adb install app/build/outputs/apk/debug/app-debug.apk
```

What a phone and a gadget still have to show:

- the permission prompts (Nearby devices on Android 12+, Location on 10 and 11), and Bluetooth on, off and not allowed;
- scanning finds a gadget with pairing open, and lists one already paired;
- connecting, the larger MTU, and subscribing to replies;
- Android's pairing dialog, and each failure: a wrong or cancelled code, a closed pairing window, and a gadget that forgot the phone (**Pair again**);
- replies longer than one notification, and the link dropping out of range;
- `phone forget`, and pairing again afterwards.

**Try the demo**, the debug menu (hold a screen's title for a second) and the mock scenarios work as on iOS. A scenario can also be set for one launch: `adb shell am start -n com.tozes.hermescompanion/.MainActivity -e mockScenario online`.

The Android pixel font, mascot frames and launcher icon are generated from the SDK too:

```bash
python3 scripts/generate-assets.py ~/path/to/hermes-gadget-sdk     # also writes the Kotlin tables
python3 scripts/make-android-icon.py ~/path/to/hermes-gadget-sdk   # the adaptive launcher icon
```

## License

MIT; see [LICENSE](LICENSE). The mascot art is Hermes Agent's (MIT, © 2025 Nous Research); see [NOTICE](NOTICE).
