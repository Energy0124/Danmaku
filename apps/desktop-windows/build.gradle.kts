plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        desktopMain.dependencies {
            implementation(project(":shared:domain"))
            implementation(compose.desktop.currentOs)
            implementation("org.jetbrains.compose.material:material:1.11.0")
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.danmaku.desktop.MainKt"
    }
}
