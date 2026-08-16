// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "WeaveCore",
    platforms: [
        .iOS(.v17),
        .macOS(.v14),
    ],
    products: [
        .library(name: "WeaveCore", targets: ["WeaveCore"]),
        .executable(name: "WeaveCoreSelfTest", targets: ["WeaveCoreSelfTest"]),
    ],
    targets: [
        .target(
            name: "WeaveCore",
            linkerSettings: [
                .linkedFramework("CryptoKit"),
                .linkedFramework("Network"),
                .linkedFramework("Security"),
            ]
        ),
        .executableTarget(name: "WeaveCoreSelfTest", dependencies: ["WeaveCore"]),
    ]
)
