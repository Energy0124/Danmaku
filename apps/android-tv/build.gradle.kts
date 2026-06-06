import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

abstract class CopyLegalAssetsTask : DefaultTask() {
    @get:Inject
    abstract val fileSystemOperations: FileSystemOperations

    @get:InputFiles
    abstract val sourceFiles: ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun copyFiles() {
        fileSystemOperations.copy {
            from(sourceFiles)
            into(outputDirectory)
        }
    }
}

val copyLegalAssets = tasks.register<CopyLegalAssetsTask>("copyLegalAssets") {
    sourceFiles.from(rootProject.file("LICENSE"))
    sourceFiles.from(rootProject.file("THIRD_PARTY_NOTICES.md"))
    sourceFiles.from(rootProject.file("third_party/licenses/APACHE-2.0.txt"))
    outputDirectory.set(layout.buildDirectory.dir("generated/legalAssets"))
}

val ciKeystorePath = providers.environmentVariable("DANMAKU_ANDROID_KEYSTORE_PATH").orNull
val ciKeystorePassword = providers.environmentVariable("DANMAKU_ANDROID_KEYSTORE_PASSWORD").orNull
val ciKeyAlias = providers.environmentVariable("DANMAKU_ANDROID_KEY_ALIAS").orNull
val ciKeyPassword = providers.environmentVariable("DANMAKU_ANDROID_KEY_PASSWORD").orNull
val hasCiSigning = !ciKeystorePath.isNullOrBlank() &&
    !ciKeystorePassword.isNullOrBlank() &&
    !ciKeyAlias.isNullOrBlank() &&
    !ciKeyPassword.isNullOrBlank()

android {
    namespace = "app.danmaku.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.danmaku.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    if (hasCiSigning) {
        signingConfigs {
            create("ciDebug") {
                storeFile = file(ciKeystorePath!!)
                storePassword = ciKeystorePassword!!
                keyAlias = ciKeyAlias!!
                keyPassword = ciKeyPassword!!
            }
        }

        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("ciDebug")
            }
        }
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(
            copyLegalAssets,
            CopyLegalAssetsTask::outputDirectory,
        )
    }
}

dependencies {
    implementation(project(":shared:domain"))
    implementation(project(":shared:library-client-android"))
    implementation(project(":shared:player-android-media3"))

    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.media3:media3-ui:1.8.1")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
