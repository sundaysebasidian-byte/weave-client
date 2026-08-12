// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "WeaveMac",
    platforms: [.macOS(.v14)],
    products: [
        .executable(name: "WeaveMac", targets: ["WeaveMac"]),
    ],
    targets: [
        .executableTarget(
            name: "WeaveMac",
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
    ],
)
