package app.danmaku.updater.android

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class AppUpdateKind {
    MOBILE,
    TV,
}

data class AppUpdateConfiguration(
    val manifestUrl: String,
    val appKind: AppUpdateKind,
    val applicationId: String,
    val currentVersionCode: Long,
    val currentVersionName: String,
)

data class AvailableAppUpdate(
    val releaseTag: String,
    val versionName: String,
    val versionCode: Long,
    val releasePageUrl: String,
    val assetName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

enum class AppUpdateFailureStage {
    CHECK,
    DOWNLOAD,
    VERIFY,
}

sealed interface AppUpdateState {
    data object Disabled : AppUpdateState
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class Current(val versionName: String) : AppUpdateState
    data class Available(val update: AvailableAppUpdate) : AppUpdateState
    data class Downloading(
        val update: AvailableAppUpdate,
        val downloadedBytes: Long,
    ) : AppUpdateState
    data class Ready(
        val update: AvailableAppUpdate,
        val apkPath: String,
    ) : AppUpdateState
    data class Failed(
        val stage: AppUpdateFailureStage,
        val update: AvailableAppUpdate? = null,
    ) : AppUpdateState
}

@Serializable
internal data class AppUpdateManifest(
    val schemaVersion: Int,
    val release: AppUpdateRelease,
    val apps: List<AppUpdateTarget>,
)

@Serializable
internal data class AppUpdateRelease(
    val tag: String,
    val versionName: String,
    val versionCode: Long,
    val pageUrl: String,
)

@Serializable
internal data class AppUpdateTarget(
    val kind: AppUpdateManifestKind,
    val applicationId: String,
    val assetName: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
)

@Serializable
internal enum class AppUpdateManifestKind {
    @SerialName("mobile")
    MOBILE,

    @SerialName("tv")
    TV,
}
