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
    targets: [
        .target(
            name: "HermesCompanion"
        ),
    ]
)
