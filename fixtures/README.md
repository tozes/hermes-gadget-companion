# Fixtures

Sample replies from the gadget's console, shared by both ports of the app's logic: the Swift tests in `GadgetKit/Tests` and the Kotlin tests in `android/core/src/test`. Both suites read these files, so a protocol change updates them here once and both ports are tested against it.

`replies/` holds one reply per file, exactly as the gadget sends it over Bluetooth without the terminating NUL byte: no trailing newline unless the reply has one. Each file is named for what it is (`status-new-gadget.txt`, `error-busy.txt`, `wifi-list.txt`…). The prefix is the reply's tag, so `status-*` files are `@status` replies, and `help.txt`, `diag.txt` and `diag-log.txt` are kept as text.

Every value is made up: the device IDs (`hg-5a1c9e2f7b3d4086`), the server (`hermes.example.com`), the network names ("Lisbon Loft", "Pocket Hotspot") and the codes. Never put a real identifier here. See [CONTRIBUTING.md](../CONTRIBUTING.md#privacy).

The replies follow the SDK's [protocol guide](https://github.com/Adolanium/hermes-gadget-sdk/blob/main/docs/protocol.md#phone-app-over-bluetooth) and the firmware's console (`App::console`). When the firmware's replies change, update the files here, then both test suites.
