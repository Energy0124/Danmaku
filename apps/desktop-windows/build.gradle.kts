plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("app.cash.sqldelight")
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

compose.desktop {
    application {
        mainClass = "app.danmaku.desktop.MainKt"

        nativeDistributions {
            modules("java.sql", "jdk.httpserver")
        }
    }
}
