package app.danmaku.updater.android

import java.net.URI
import kotlinx.serialization.json.Json

internal const val APP_UPDATE_SCHEMA_VERSION = 1
internal const val MAX_UPDATE_APK_BYTES = 512L * 1024L * 1024L
internal const val AUTOMATIC_UPDATE_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

internal fun resolveAvailableUpdate(
    manifestJson: String,
    configuration: AppUpdateConfiguration,
    json: Json = Json { ignoreUnknownKeys = true },
): AvailableAppUpdate? {
    require(configuration.applicationId.isNotBlank()) { "applicationId must not be blank" }
    val manifest = json.decodeFromString<AppUpdateManifest>(manifestJson)
    require(manifest.schemaVersion == APP_UPDATE_SCHEMA_VERSION) { "Unsupported update manifest schema" }
    require(manifest.release.versionName.matches(Regex("\\d+\\.\\d+\\.\\d+"))) {
        "Invalid update version"
    }
    require(manifest.release.tag == "v${manifest.release.versionName}") { "Release tag/version mismatch" }
    require(manifest.release.versionCode > 0) { "Invalid update version code" }

    val expectedKind = when (configuration.appKind) {
        AppUpdateKind.MOBILE -> AppUpdateManifestKind.MOBILE
        AppUpdateKind.TV -> AppUpdateManifestKind.TV
    }
    val targets = manifest.apps.filter { it.kind == expectedKind }
    require(targets.size == 1) { "Update manifest must contain exactly one target for this app" }
    val target = targets.single()
    require(target.applicationId == configuration.applicationId) { "Update package does not match this app" }
    require(target.assetName.matches(Regex("[A-Za-z0-9._-]+\\.apk"))) { "Invalid update asset name" }
    require(target.sha256.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid update checksum" }
    require(target.sizeBytes in 1..MAX_UPDATE_APK_BYTES) { "Invalid update size" }

    validateGitHubReleaseUrls(
        manifestUrl = configuration.manifestUrl,
        tag = manifest.release.tag,
        pageUrl = manifest.release.pageUrl,
        assetName = target.assetName,
        apkUrl = target.apkUrl,
    )

    if (manifest.release.versionCode <= configuration.currentVersionCode) return null
    return AvailableAppUpdate(
        releaseTag = manifest.release.tag,
        versionName = manifest.release.versionName,
        versionCode = manifest.release.versionCode,
        releasePageUrl = manifest.release.pageUrl,
        assetName = target.assetName,
        apkUrl = target.apkUrl,
        sha256 = target.sha256.lowercase(),
        sizeBytes = target.sizeBytes,
    )
}

internal fun isAutomaticUpdateCheckDue(lastAttemptEpochMs: Long?, nowEpochMs: Long): Boolean {
    if (lastAttemptEpochMs == null) return true
    val elapsed = nowEpochMs - lastAttemptEpochMs
    return elapsed < 0 || elapsed >= AUTOMATIC_UPDATE_CHECK_INTERVAL_MS
}

private fun validateGitHubReleaseUrls(
    manifestUrl: String,
    tag: String,
    pageUrl: String,
    assetName: String,
    apkUrl: String,
) {
    val manifestUri = URI(manifestUrl)
    require(manifestUri.scheme == "https" && manifestUri.host.equals("github.com", ignoreCase = true)) {
        "Update manifest must be hosted on GitHub over HTTPS"
    }
    val manifestSegments = manifestUri.path.trim('/').split('/')
    require(
        manifestSegments.size == 6 &&
            manifestSegments[2] == "releases" &&
            manifestSegments[3] == "latest" &&
            manifestSegments[4] == "download" &&
            manifestSegments[5] == "android-update.json",
    ) { "Invalid GitHub update manifest URL" }
    val repositoryPath = "/${manifestSegments[0]}/${manifestSegments[1]}"
    require(URI(pageUrl) == URI("https://github.com$repositoryPath/releases/tag/$tag")) {
        "Invalid GitHub release page URL"
    }
    require(URI(apkUrl) == URI("https://github.com$repositoryPath/releases/download/$tag/$assetName")) {
        "Invalid GitHub release asset URL"
    }
}
