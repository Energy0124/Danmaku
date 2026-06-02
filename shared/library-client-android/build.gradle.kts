plugins {
    id("com.android.library")
}

android {
    namespace = "app.danmaku.library.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    implementation(project(":shared:domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
}
