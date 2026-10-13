# Changelog

## Unreleased

- Wi-Fi keeps several networks: the Wi-Fi screen lists the networks the gadget has saved, marks the one it's on and the ones it hasn't joined yet, and lets you join one now or forget it. **Add a network** joins it now or saves it for later, for a phone hotspot or a place you're not at yet. This needs firmware with the SDK's `wifi` console command; on older firmware the screen keeps its one-network form.
- The first version of the app: find a Hermes Gadget over Bluetooth, pair with the code on its screen, and read its status, set up its Wi-Fi, change its settings, or use its console. The screens are styled like the gadget's own display, with its colours, pixel font and mascot. A debug menu (hold a screen's title) switches between real Bluetooth and mock scenarios.
- Tests for the app's logic (`swift test --package-path GadgetKit`, on Linux and macOS), and CI that runs them and builds the iOS app.
