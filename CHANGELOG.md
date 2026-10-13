# Changelog

## Unreleased

- The console no longer shows secrets the app or you send: Wi-Fi passwords and the Hermes access token appear as `<hidden>` (the gadget still gets them).
- An Android app (Android 10 or later) with the same screens, demo and debug menu as the iOS app, its logic ported to Kotlin and tested against the same reply samples; it builds and passes its tests, but its Bluetooth link hasn't been tried on a phone yet.

- A demo for trying the app without a gadget: **Try the demo** on the first screen opens pretend gadgets to pair with and explore, also good for screenshots. **Leave the demo** on the first screen or under **Advanced** goes back to Bluetooth, and the app always starts on Bluetooth.
- A simpler home screen: the drawing of the gadget's own screen is gone (from pairing too), the mascot heads the home screen as on the find screen, with the connection shown once under her, and the top bar shows the gadget's Wi-Fi and battery. The status details (server, device ID, board, voltage), the console and **Forget** move to a new **Advanced** screen. A **Gadgets** button goes back from a gadget to the list of gadgets nearby.
- Wi-Fi keeps several networks: the Wi-Fi screen lists the networks the gadget has saved, marks the one it's on and the ones it hasn't joined yet, and lets you join one now or forget it. **Add a network** joins it now or saves it for later, for a phone hotspot or a place you're not at yet. This needs firmware with the SDK's `wifi` console command; on older firmware the screen keeps its one-network form.
- The first version of the app: find a Hermes Gadget over Bluetooth, pair with the code on its screen, and read its status, set up its Wi-Fi, change its settings, or use its console. The screens are styled like the gadget's own display, with its colours, pixel font and mascot. A debug menu (hold a screen's title) switches between real Bluetooth and mock scenarios.
- Tests for the app's logic (`swift test --package-path GadgetKit`, on Linux and macOS), and CI that runs them and builds the iOS app.
