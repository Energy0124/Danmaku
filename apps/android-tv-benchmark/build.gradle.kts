plugins {
    id("com.android.test")
}

android {
    namespace = "app.danmaku.tv.benchmark"
    compileSdk = 36
    targetProjectPath = ":apps:android-tv"

    defaultConfig {
        minSdk = 23
        targetSdk = 36
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "EMULATOR"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = true
            matchingFallbacks += listOf("benchmark")
        }
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation("androidx.benchmark:benchmark-macro-junit4:1.4.1")
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
