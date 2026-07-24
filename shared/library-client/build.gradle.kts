import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
}

kotlin {
    jvm()

    android {
        namespace = "app.danmaku.library"
        compileSdk = 36
        minSdk = 23

        withHostTest {}

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":shared:domain"))
        }

        commonTest.dependencies {
            implementation(kotlin("test"))
        }

        val jvmMain by getting
        jvmMain.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }

        val jvmTest by getting
        jvmTest.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }
    }
}
