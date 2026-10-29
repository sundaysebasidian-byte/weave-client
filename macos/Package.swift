// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "WeaveMac",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "WeaveMac", targets: ["WeaveMac"]),
    ],
    dependencies: [
        .package(url: "https://github.com/jpsim/Yams.git", exact: "6.2.2"),
    ],
    targets: [
        .target(name: "CWeaveHTTP", linkerSettings: [.linkedLibrary("curl")]),
        .executableTarget(
            name: "WeaveMac",
            dependencies: ["CWeaveHTTP", .product(name: "Yams", package: "Yams")],
            path: "Sources/WeaveMac",
            swiftSettings: [
                .unsafeFlags(["-Xfrontend", "-strict-concurrency=minimal"]),
            ],
            linkerSettings: [
                .linkedFramework("SwiftUI"),
                .linkedFramework("AppKit"),
                .linkedFramework("CryptoKit"),
                .linkedFramework("Network"),
                .linkedFramework("Security"),
                .linkedFramework("CoreImage"),
                .linkedFramework("Vision"),
            ],
        ),
        .testTarget(name: "WeaveMacTests", dependencies: ["WeaveMac"]),
    ],
)
