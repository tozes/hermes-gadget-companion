// swift-tools-version: 6.0

import PackageDescription

// The app's logic: Foundation and Observation only, so its tests run on Linux
// as well as macOS (`swift test --package-path GadgetKit`). The app, the
// package one level up, depends on it.
let package = Package(
    name: "GadgetKit",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "GadgetKit", targets: ["GadgetKit"]),
    ],
    targets: [
        .target(name: "GadgetKit"),
        .testTarget(name: "GadgetKitTests", dependencies: ["GadgetKit"]),
    ]
)
