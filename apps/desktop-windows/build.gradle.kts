import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val commonMain by getting
        val desktopMain by getting

        commonMain.dependencies {
            implementation(compose.components.resources)
        }

        desktopMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(project(":shared:library-client"))
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.material:material:1.11.0")
            implementation(compose.materialIconsExtended)
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
            implementation("app.cash.sqldelight:sqlite-driver:2.3.2")
            implementation("net.java.dev.jna:jna:5.17.0")
            implementation("net.java.dev.jna:jna-platform:5.17.0")
        }

        val desktopTest by getting
        desktopTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("DesktopLibraryDatabase") {
            packageName.set("app.danmaku.desktop.db")
        }
    }
}

val windowsMpvBridgeDll = rootProject.layout.projectDirectory.file("target/release/player_windows_mpv.dll")
val windowsLibmpvDll = rootProject.layout.projectDirectory.file("runtime/windows/libmpv/libmpv-2.dll")
val windowsRustServerExe = rootProject.layout.projectDirectory.file("target/release/library-server.exe")
val webUiProjectDir = rootProject.layout.projectDirectory.dir("apps/web-ui")
val webUiDistDir = webUiProjectDir.dir("dist")
val windowsDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main/app/desktop-windows/app")
val windowsLibmpvDllPath = windowsLibmpvDll.asFile.absolutePath
val macosMpvBridgeDylib = rootProject.layout.projectDirectory.file("target/release/libplayer_windows_mpv.dylib")
val macosDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main/app/desktop-windows.app/Contents/app")
val hostOs = System.getProperty("os.name").lowercase()
val isWindowsHost = "windows" in hostOs
val isMacosHost = "mac" in hostOs || "darwin" in hostOs

val buildWindowsMpvBridge by tasks.registering(Exec::class) {
    description = "Builds the Windows libmpv JNA bridge DLL used by the desktop player."
    group = "build"
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine("cargo", "build", "--release", "-p", "player-windows-mpv", "--lib")
    inputs.files(
        rootProject.layout.projectDirectory.file("Cargo.toml"),
        rootProject.layout.projectDirectory.dir("native/player-windows-mpv"),
    )
    outputs.file(windowsMpvBridgeDll)
}

val verifyWindowsLibmpvDll by tasks.registering(Exec::class) {
    description = "Verifies that the approved local Windows libmpv DLL is installed before packaging."
    group = "verification"
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        "if (-not (Test-Path -LiteralPath '$windowsLibmpvDllPath' -PathType Leaf)) { " +
            "throw 'Missing approved Windows libmpv DLL at $windowsLibmpvDllPath. " +
            "Run tools/windows/install-libmpv-dependency.ps1 -AcceptLicense first.' }",
    )
}

val bundleWindowsMpvRuntime by tasks.registering(Copy::class) {
    description = "Copies the Windows mpv bridge and approved libmpv DLL into the Compose distributable."
    group = "distribution"
    dependsOn(buildWindowsMpvBridge, verifyWindowsLibmpvDll)
    from(windowsMpvBridgeDll)
    from(windowsLibmpvDll)
    into(windowsDistributableAppDir)
}

val buildWindowsRustServer by tasks.registering(Exec::class) {
    description = "Builds the release Rust library server used by the desktop sidecar."
    group = "build"
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine("cargo", "build", "--release", "-p", "library-server")
    inputs.files(
        rootProject.layout.projectDirectory.file("Cargo.toml"),
        rootProject.layout.projectDirectory.file("Cargo.lock"),
    )
    inputs.dir(rootProject.layout.projectDirectory.dir("native/library-server"))
    inputs.dir(rootProject.layout.projectDirectory.dir("native/rust-core"))
    outputs.file(windowsRustServerExe)
}

val verifyDesktopWebUiDist by tasks.registering(Exec::class) {
    description = "Verifies that the web UI distribution exists before desktop sidecar packaging."
    group = "verification"
    val webIndexPath = webUiDistDir.file("index.html").asFile.absolutePath
    commandLine(
        "powershell",
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-Command",
        "if (-not (Test-Path -LiteralPath '$webIndexPath' -PathType Leaf)) { " +
            "throw 'Missing web UI distribution at $webIndexPath. Run npm run build in apps/web-ui first.' }",
    )
}

val bundleWindowsRustServerRuntime by tasks.registering(Copy::class) {
    description = "Copies the Rust sidecar executable and web UI into the Compose distributable."
    group = "distribution"
    dependsOn(buildWindowsRustServer, verifyDesktopWebUiDist)
    from(windowsRustServerExe)
    from(webUiDistDir) {
        into("web")
    }
    into(windowsDistributableAppDir.map { it.dir("server") })
}

val buildMacosMpvBridge by tasks.registering(Exec::class) {
    description = "Builds the macOS libmpv JNA bridge dylib used by the desktop player."
    group = "build"
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine("cargo", "build", "--release", "-p", "player-windows-mpv", "--lib")
    inputs.files(
        rootProject.layout.projectDirectory.file("Cargo.toml"),
        rootProject.layout.projectDirectory.dir("native/player-windows-mpv"),
    )
    outputs.file(macosMpvBridgeDylib)
}

val bundleMacosMpvRuntime by tasks.registering(Copy::class) {
    description = "Copies the macOS mpv bridge into the Compose distributable."
    group = "distribution"
    dependsOn(buildMacosMpvBridge)
    from(macosMpvBridgeDylib)
    into(macosDistributableAppDir)
}

afterEvaluate {
    tasks.named("createDistributable") {
        when {
            isWindowsHost -> finalizedBy(bundleWindowsMpvRuntime, bundleWindowsRustServerRuntime)
            isMacosHost -> finalizedBy(bundleMacosMpvRuntime)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.danmaku.desktop.MainKt"

        nativeDistributions {
            modules("java.sql")
        }
    }
}
