plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()

    sourceSets {
        val jvmMain by getting
        jvmMain.dependencies {
            implementation(project(":shared:domain"))
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
        }

        val jvmTest by getting
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
