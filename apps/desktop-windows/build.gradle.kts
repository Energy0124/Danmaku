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
            implementation(project(":shared:library-server-core"))
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
val windowsDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main/app/desktop-windows/app")
val windowsLibmpvDllPath = windowsLibmpvDll.asFile.absolutePath
val macosMpvBridgeDylib = rootProject.layout.projectDirectory.file("target/release/libplayer_windows_mpv.dylib")
val macosDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main/app/desktop-windows.app/Contents/app")
val hostOs = System.getProperty("os.name").lowercase()
val isWindowsHost = "windows" in hostOs
val isMacosHost = "mac" in hostOs || "darwin" in hostOs
val rustLibraryServerExecutableName = if (isWindowsHost) "library-server.exe" else "library-server"
val rustLibraryServerBinary = rootProject.layout.projectDirectory.file("target/release/$rustLibraryServerExecutableName")

val buildRustLibraryServer by tasks.registering(Exec::class) {
    description = "Builds the Rust library server bundled as the desktop sidecar."
    group = "build"
    workingDir = rootProject.layout.projectDirectory.asFile
    commandLine("cargo", "build", "--release", "-p", "library-server")
    inputs.files(
        rootProject.layout.projectDirectory.file("Cargo.toml"),
        rootProject.layout.projectDirectory.dir("native/library-server"),
    )
    outputs.file(rustLibraryServerBinary)
}


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
    description = "Copies the Windows mpv runtime and Rust server sidecar into the Compose distributable."
    group = "distribution"
    dependsOn(buildWindowsMpvBridge, buildRustLibraryServer, verifyWindowsLibmpvDll)
    from(windowsMpvBridgeDll)
    from(windowsLibmpvDll)
    from(rustLibraryServerBinary)
    into(windowsDistributableAppDir)
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
    description = "Copies the macOS mpv bridge and Rust server sidecar into the Compose distributable."
    group = "distribution"
    dependsOn(buildMacosMpvBridge, buildRustLibraryServer)
    from(macosMpvBridgeDylib)
    from(rustLibraryServerBinary)
    into(macosDistributableAppDir)
}

afterEvaluate {
    tasks.named("createDistributable") {
        when {
            isWindowsHost -> finalizedBy(bundleWindowsMpvRuntime)
            isMacosHost -> finalizedBy(bundleMacosMpvRuntime)
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.danmaku.desktop.MainKt"

        nativeDistributions {
            modules("java.sql", "jdk.httpserver")
        }
    }
}
