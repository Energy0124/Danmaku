// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DanmakuCore", platforms: [.iOS(.v17), .macOS(.v13)],
    products: [.library(name: "DanmakuCore", targets: ["DanmakuCore"])],
    targets: [
        .target(name: "DanmakuCore"),
        .testTarget(name: "DanmakuCoreTests", dependencies: ["DanmakuCore"])
    ]
)
