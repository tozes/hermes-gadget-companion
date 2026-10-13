# Contributing

Thanks for helping. Bug reports from real phones and gadgets, fixes, screens and docs are all welcome.

The app talks to a [Hermes Gadget](https://github.com/Adolanium/hermes-gadget-sdk) over Bluetooth LE. The gadget's side (its firmware, the Bluetooth service and the console commands) lives in the SDK repository; this one holds the phone apps, for iPhone and Android.

## Reporting a bug

Open an [issue](https://github.com/tozes/hermes-gadget-companion/issues/new) and include:

- the phone model and iOS or Android version, and the app's commit;
- the gadget's board and firmware version (the app's home screen shows both);
- what you did, what happened and what you expected;
- for a connection or pairing problem, the replies from the app's console and, if you have it, the gadget's serial log (`hermes-gadget diag --port …` in the SDK).

Remove anything that identifies your setup first: server addresses, device IDs, Wi-Fi names, tokens. Security problems go through a private [security advisory](https://github.com/tozes/hermes-gadget-companion/security/advisories/new), not an issue.

## Setting up

The app is a Swift package built with [xtool](https://github.com/xtool-org/xtool), so you can work on it from Linux or macOS.

```bash
swift test --package-path GadgetKit   # the app's logic; Linux or macOS, no iPhone needed
xtool dev build                      # the iOS app, unsigned
xtool dev                            # build, sign and install on a connected iPhone
```

- **Linux:** install Swift 6 and xtool, then the iOS SDK with `xtool sdk install <Xcode.xip>` (download Xcode from Apple with your Apple ID). Installing on a phone needs `xtool auth login` and `usbmuxd`.
- **macOS:** xtool works the same way, or open `Package.swift` in Xcode.

Without a paid Apple developer account, a signed build expires after seven days and needs installing again.

### Android

The Android app is a Gradle project in `android/`. It needs JDK 21 and the Android SDK with platform 37.2 (`platforms;android-37.2`) and the build tools the Android Gradle Plugin asks for; Android Studio installs both, or use the command-line tools' `sdkmanager`. Point Gradle at the SDK with `ANDROID_HOME` or `android/local.properties` (`sdk.dir=…`, not committed). The wrapper fetches Gradle itself.

```bash
cd android
./gradlew :core:test                                   # the app's logic; any JVM, no phone needed
./gradlew :core:test :app:assembleDebug :app:lintDebug # what CI runs
adb install app/build/outputs/apk/debug/app-debug.apk  # on a phone with USB debugging on
```

- **Linux and macOS:** as above. Google ships the SDK's build tools for x86-64 only on Linux, so an aarch64 Linux machine runs them (aapt2, adb) under qemu user emulation.
- **Windows:** the same, with `gradlew.bat` instead of `./gradlew`, in PowerShell or a command prompt.

## How the code is organised

| Part | What's in it | Runs on |
|---|---|---|
| `GadgetKit` package (`GadgetKit/Sources/GadgetKit`) | Reply parsing, the `GadgetStore` view model, the `GadgetLink` protocol, the mock gadget and its scenarios, Wi-Fi setup logic, the generated font and mascot data. Foundation and Observation only | Linux, macOS, iOS |
| The `HermesCompanion` app (`Sources/HermesCompanion`) | The SwiftUI screens and styles, and `BluetoothGadgetLink` (Core Bluetooth) | iOS |
| `GadgetKitTests` (`GadgetKit/Tests/GadgetKitTests`) | Swift Testing tests for GadgetKit | Linux, macOS |
| `android/core` | The Kotlin port of GadgetKit (`com.tozes.hermescompanion.core`), plus the radio-free half of the Android Bluetooth link (`core.ble`: the GATT operation queue, pairing and failure mapping), and their JUnit tests. Kotlin, coroutines and kotlinx.serialization only | any JVM |
| `android/app` | The Compose screens and styles, and the Android side of `BluetoothGadgetLink` (permissions, scanning, `BluetoothGatt`) | Android 10+ |
| `fixtures/replies` | Reply samples, exactly as the gadget sends them, read by both test suites | |

GadgetKit is a separate local package so its tests build without SwiftUI or Core Bluetooth; the app depends on it by path. `android/core` plays the same part for the Android app, which mirrors the iOS app screen for screen: a change to one app's screens, states or copy belongs in the other too. Screens only talk to `GadgetStore`, and the store only talks to a `GadgetLink`. Put logic in GadgetKit where it can be tested, and keep views thin. `MockGadgetLink` acts out the demo (**Try the demo** on the first screen) and the scenarios in the debug menu (hold any screen's title for a second), so most UI work doesn't need a gadget. Take screenshots in the demo: its data is made up, so nothing real leaks.

## Working with the gadget

- **The protocol belongs to the SDK.** The commands, replies and Bluetooth UUIDs are defined in the SDK's [protocol guide](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/protocol.md#phone-app-over-bluetooth). The app sends the same console lines as the USB console. A feature that needs a new command starts as an SDK change; don't invent replies here.
- **Bluetooth is experimental in the firmware** and off on every board until that board passes the SDK's [Bluetooth checks](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/hardware-validation.md#bluetooth). To test against a real gadget you may need a firmware build with Bluetooth turned on.
- **Keep the mock honest.** When the firmware's replies or validation messages change, update `MockDevice` to match, and the tests with it.
- **One set of fixtures, two ports.** A protocol change updates the samples in `fixtures/replies/` (see its README), then both ports: GadgetKit and `android/core`, with their mocks and tests. Neither port should pass on a reply the other rejects.

## Pull requests

- **Keep each PR to one change**, with a title that says what it does.
- **Say why, then what, then how you know it works.** List the commands you ran and their results. Say what ran on a phone against a real gadget and what only ran against the mock or in tests: a passing build or mock run doesn't show that Bluetooth works. Include the phone, iOS or Android version, board and firmware. Screenshots help for UI changes.
- **Tests:** logic changes in GadgetKit or `android/core` need tests that check observable results (what the store shows, what was sent), not that a helper was called. For a bug fix, add a test that fails before the fix.
- **CI must pass:** it runs GadgetKit's tests on Linux and macOS, builds the iOS app, and tests, builds and lints the Android app.
- **Changelog:** add a line under `## Unreleased` in [CHANGELOG.md](CHANGELOG.md). CI checks for it; label the PR `no-changelog` when nothing user-facing changed (refactors, test-only changes, dependency bumps).
- **Docs:** update the README or this guide when you change how to build, test or use the app.

## Style

- Match the code around you. Swift 6 with strict concurrency; no third-party dependencies. On Android, Kotlin with AndroidX, Compose and kotlinx only (coroutines, serialization), and JUnit with kotlin.test for tests.
- The apps look like the gadget's own screen: take colours from `Palette`, use the pixel font (`PixelText`) for headlines, labels and hints, and the system font for long text, inputs and the console. Dark only. Don't add stock gradients, glass or decorative icons.
- Accessibility: VoiceOver and TalkBack labels on pixel-font text, glyphs and the mascot, Dynamic Type and Android's font size for body text, touch targets of at least 44 points on iOS and 48 dp on Android, and respect Reduce Motion and Remove animations.

## Privacy

Never commit real identifiers: server addresses and URL paths, device IDs, Wi-Fi names, Apple team or key IDs, tokens or passwords. Use obvious fakes in code, tests, mock data and screenshots (`hermes.example.com`, a made-up `hg-` ID). The gadget's endpoint path is effectively a password, so even part of it counts.

## Licensing

Contributions are under the [MIT license](LICENSE). Don't copy code from other projects.

The mascot art is Nous Research's (from Hermes Agent, MIT), and the pixel font comes from the SDK; see [NOTICE](NOTICE). The font, mascot frames and icons are generated from an SDK checkout by `scripts/generate-assets.py` (Swift and Kotlin), `scripts/make-icon.py` (iOS) and `scripts/make-android-icon.py` (Android): regenerate them rather than editing the output, and keep NOTICE up to date when you add material from elsewhere.
