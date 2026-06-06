import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
    id("app.cash.licensee")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

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

licensee {
    allow("Apache-2.0")
}

val windowsMpvBridgeDll = rootProject.layout.projectDirectory.file("target/release/player_windows_mpv.dll")
val windowsLibmpvDll = rootProject.layout.projectDirectory.file("runtime/windows/libmpv/libmpv-2.dll")
val windowsDistributableAppDir = layout.buildDirectory.dir("compose/binaries/main/app/desktop-windows/app")
val windowsLibmpvDllPath = windowsLibmpvDll.asFile.absolutePath

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

afterEvaluate {
    tasks.named("createDistributable") {
        finalizedBy(bundleWindowsMpvRuntime)
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
