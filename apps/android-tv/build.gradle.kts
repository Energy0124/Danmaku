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

val releaseVersionName = providers.gradleProperty("danmaku.releaseVersionName").getOrElse("0.1.0")
val releaseVersionCode = providers.gradleProperty("danmaku.releaseVersionCode").getOrElse("1").toInt()
val updateManifestUrl = providers.gradleProperty("danmaku.updateManifestUrl").getOrElse("")

val defaultServerUrl = providers.gradleProperty("danmaku.tv.defaultServerUrl")
    .orElse(providers.environmentVariable("DANMAKU_TV_DEFAULT_SERVER_URL"))
    .getOrElse("")
val defaultPairingToken = providers.gradleProperty("danmaku.tv.defaultPairingToken")
    .orElse(providers.environmentVariable("DANMAKU_TV_DEFAULT_PAIRING_TOKEN"))
    .getOrElse("")

fun String.toBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "app.danmaku.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.danmaku.tv"
        minSdk = 23
        targetSdk = 36
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "DEFAULT_SERVER_URL", defaultServerUrl.toBuildConfigString())
        buildConfigField("String", "DEFAULT_PAIRING_TOKEN", defaultPairingToken.toBuildConfigString())
        buildConfigField("boolean", "TV_QA_FIXTURES_ENABLED", "false")
        buildConfigField("String", "UPDATE_MANIFEST_URL", updateManifestUrl.toBuildConfigString())
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    buildTypes {
        getByName("debug") {
            buildConfigField("boolean", "TV_QA_FIXTURES_ENABLED", "true")
        }
        getByName("release") {
            buildConfigField("boolean", "TV_QA_FIXTURES_ENABLED", "false")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
            buildConfigField("boolean", "TV_QA_FIXTURES_ENABLED", "true")
        }
    }

    if (hasCiSigning) {
        signingConfigs {
            create("ci") {
                storeFile = file(ciKeystorePath!!)
                storePassword = ciKeystorePassword!!
                keyAlias = ciKeyAlias!!
                keyPassword = ciKeyPassword!!
            }
        }

        buildTypes {
            getByName("debug") {
                signingConfig = signingConfigs.getByName("ci")
            }
            getByName("release") {
                signingConfig = signingConfigs.getByName("ci")
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
    implementation(project(":shared:app-update-android"))
    implementation(project(":shared:library-client-android"))
    implementation(project(":shared:player-android-media3"))

    val composeBom = platform("androidx.compose:compose-bom:2026.05.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.media3:media3-ui:1.8.1")
    implementation("androidx.tv:tv-material:1.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test:core:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
