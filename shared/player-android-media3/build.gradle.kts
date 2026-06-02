plugins {
    id("com.android.library")
}

android {
    namespace = "app.danmaku.player.android"
    compileSdk = 36

    defaultConfig {
        minSdk = 23
    }
}

dependencies {
    implementation(project(":shared:domain"))
    implementation("androidx.media3:media3-exoplayer:1.8.1")
    implementation("androidx.media3:media3-session:1.8.1")
}
