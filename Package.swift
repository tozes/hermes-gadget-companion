// swift-tools-version: 6.0

import PackageDescription

let package = Package(
    name: "HermesCompanion",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        // An xtool project should contain exactly one library product,
        // representing the main app.
        .library(
            name: "HermesCompanion",
            targets: ["HermesCompanion"]
        ),
    ],
    dependencies: [
        // The app's logic, in its own package so its tests run on Linux:
        // `swift test --package-path GadgetKit`.
        .package(path: "GadgetKit"),
    ],
    targets: [
        // The iOS app: SwiftUI screens and the Core Bluetooth link.
        .target(
            name: "HermesCompanion",
            dependencies: [.product(name: "GadgetKit", package: "GadgetKit")]
        ),
    ]
)
