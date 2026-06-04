package app.danmaku.domain

import kotlinx.serialization.Serializable

@Serializable
data class MediaSourceDescriptor(
    val id: String,
    val displayName: String,
    val capabilities: MediaSourceCapabilities,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}

@Serializable
data class MediaSourceCapabilities(
    val canBrowse: Boolean = false,
    val canSearch: Boolean = false,
    val canResolvePlayableVariants: Boolean = false,
    val canReportPlaybackProgress: Boolean = false,
    val downloadPolicy: AuthorizedDownloadPolicy = AuthorizedDownloadPolicy.offlineUnavailable(),
)

interface MediaSourcePlugin {
    val descriptor: MediaSourceDescriptor
}

@Serializable
data class AuthorizedDownloadPolicy(
    val offlineStorage: OfflineStoragePolicy,
    val authorization: DownloadAuthorization,
    val drm: DownloadDrmPolicy,
    val expiresAtEpochMs: Long? = null,
    val attribution: String? = null,
    val termsUrl: String? = null,
    val requiresUserConfirmation: Boolean = true,
) {
    init {
        require(expiresAtEpochMs == null || expiresAtEpochMs >= 0) {
            "expiresAtEpochMs must not be negative"
        }
        require(attribution == null || attribution.isNotBlank()) {
            "attribution must not be blank"
        }
        require(termsUrl == null || termsUrl.isNotBlank()) {
            "termsUrl must not be blank"
        }
        require(
            offlineStorage == OfflineStoragePolicy.NOT_ALLOWED ||
                authorization != DownloadAuthorization.UNKNOWN,
        ) {
            "offline storage requires known authorization"
        }
        require(
            offlineStorage == OfflineStoragePolicy.NOT_ALLOWED ||
                drm != DownloadDrmPolicy.UNSUPPORTED_DRM,
        ) {
            "offline storage cannot require unsupported DRM handling"
        }
        require(
            offlineStorage != OfflineStoragePolicy.ALLOWED_UNTIL_EXPIRY ||
                expiresAtEpochMs != null,
        ) {
            "expiring offline storage requires expiresAtEpochMs"
        }
    }

    val allowsOfflineStorage: Boolean
        get() = offlineStorage != OfflineStoragePolicy.NOT_ALLOWED

    companion object {
        fun offlineUnavailable(): AuthorizedDownloadPolicy =
            AuthorizedDownloadPolicy(
                offlineStorage = OfflineStoragePolicy.NOT_ALLOWED,
                authorization = DownloadAuthorization.UNKNOWN,
                drm = DownloadDrmPolicy.UNKNOWN,
            )
    }
}

@Serializable
enum class OfflineStoragePolicy {
    NOT_ALLOWED,
    ALLOWED_WITHOUT_EXPIRY,
    ALLOWED_UNTIL_EXPIRY,
}

@Serializable
enum class DownloadAuthorization {
    UNKNOWN,
    USER_OWNED_LOCAL_FILE,
    USER_AUTHORIZED_ACCOUNT,
    USER_CONFIGURED_EXTERNAL_SERVICE,
    PUBLIC_OR_LICENSED,
}

@Serializable
enum class DownloadDrmPolicy {
    UNKNOWN,
    DRM_FREE,
    PLATFORM_MANAGED_DRM,
    UNSUPPORTED_DRM,
}

@Serializable
data class DownloadManifest(
    val id: String,
    val sourceId: String,
    val title: String,
    val assets: List<DownloadAsset>,
    val policy: AuthorizedDownloadPolicy,
    val requestedAtEpochMs: Long,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(sourceId.isNotBlank()) { "sourceId must not be blank" }
        require(title.isNotBlank()) { "title must not be blank" }
        require(assets.isNotEmpty()) { "assets must not be empty" }
        require(policy.allowsOfflineStorage) {
            "download manifests require a policy that allows offline storage"
        }
        require(requestedAtEpochMs >= 0) { "requestedAtEpochMs must not be negative" }
    }
}

@Serializable
data class DownloadAsset(
    val id: String,
    val kind: DownloadAssetKind,
    val sourceUri: String,
    val relativeOutputPath: String,
    val mediaType: String,
    val sizeBytes: Long? = null,
    val checksum: DownloadChecksum? = null,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(sourceUri.isNotBlank()) { "sourceUri must not be blank" }
        require(relativeOutputPath.isNotBlank()) { "relativeOutputPath must not be blank" }
        require(!relativeOutputPath.startsWith("/") && !relativeOutputPath.startsWith("\\")) {
            "relativeOutputPath must be relative"
        }
        require(!relativeOutputPath.hasWindowsDriveRoot()) {
            "relativeOutputPath must not include a drive root"
        }
        require(relativeOutputPath.pathSegments().none { it == ".." }) {
            "relativeOutputPath must not traverse parent directories"
        }
        require(mediaType.isNotBlank()) { "mediaType must not be blank" }
        require(sizeBytes == null || sizeBytes >= 0) { "sizeBytes must not be negative" }
    }
}

@Serializable
enum class DownloadAssetKind {
    MEDIA,
    SUBTITLE,
    DANMAKU,
    ARTWORK,
}

@Serializable
data class DownloadChecksum(
    val algorithm: String,
    val value: String,
) {
    init {
        require(algorithm.isNotBlank()) { "algorithm must not be blank" }
        require(value.isNotBlank()) { "value must not be blank" }
    }
}

private fun String.hasWindowsDriveRoot(): Boolean =
    length >= 3 && this[1] == ':' && (this[2] == '\\' || this[2] == '/') && this[0].isLetter()

private fun String.pathSegments(): List<String> =
    split('/', '\\')
