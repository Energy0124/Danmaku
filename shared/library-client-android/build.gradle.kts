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
    api(project(":shared:library-client"))
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(
        project(
            path = ":shared:library-server-core",
            configuration = "jvmRuntimeElements",
        ),
    )
}
