package app.danmaku.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaSourceTest {
    @Test
    fun offlineUnavailablePolicyDoesNotAllowDownloads() {
        val policy = AuthorizedDownloadPolicy.offlineUnavailable()

        assertFalse(policy.allowsOfflineStorage)
        assertEquals(DownloadAuthorization.UNKNOWN, policy.authorization)
    }

    @Test
    fun rejectsOfflineStorageWithoutKnownAuthorization() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizedDownloadPolicy(
                offlineStorage = OfflineStoragePolicy.ALLOWED_WITHOUT_EXPIRY,
                authorization = DownloadAuthorization.UNKNOWN,
                drm = DownloadDrmPolicy.DRM_FREE,
            )
        }
    }

    @Test
    fun rejectsOfflineStorageThatRequiresUnsupportedDrm() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizedDownloadPolicy(
                offlineStorage = OfflineStoragePolicy.ALLOWED_WITHOUT_EXPIRY,
                authorization = DownloadAuthorization.USER_AUTHORIZED_ACCOUNT,
                drm = DownloadDrmPolicy.UNSUPPORTED_DRM,
            )
        }
    }

    @Test
    fun rejectsExpiringOfflineStorageWithoutExpiry() {
        assertFailsWith<IllegalArgumentException> {
            AuthorizedDownloadPolicy(
                offlineStorage = OfflineStoragePolicy.ALLOWED_UNTIL_EXPIRY,
                authorization = DownloadAuthorization.USER_AUTHORIZED_ACCOUNT,
                drm = DownloadDrmPolicy.DRM_FREE,
            )
        }
    }

    @Test
    fun createsAuthorizedDownloadManifest() {
        val manifest = DownloadManifest(
            id = "manifest-1",
            sourceId = "ani-rss",
            title = "Example Show - Episode 01",
            assets = listOf(
                DownloadAsset(
                    id = "media",
                    kind = DownloadAssetKind.MEDIA,
                    sourceUri = "ani-rss://subscription/example/episode-1",
                    relativeOutputPath = "Example Show/Episode 01.mkv",
                    mediaType = "video/x-matroska",
                    sizeBytes = 1_024,
                ),
            ),
            policy = AuthorizedDownloadPolicy(
                offlineStorage = OfflineStoragePolicy.ALLOWED_WITHOUT_EXPIRY,
                authorization = DownloadAuthorization.USER_CONFIGURED_EXTERNAL_SERVICE,
                drm = DownloadDrmPolicy.DRM_FREE,
                attribution = "Imported from a user-managed ani-rss output folder",
            ),
            requestedAtEpochMs = 123,
        )

        assertTrue(manifest.policy.allowsOfflineStorage)
        assertEquals("ani-rss", manifest.sourceId)
    }

    @Test
    fun rejectsManifestsWhenOfflineStorageIsNotAllowed() {
        assertFailsWith<IllegalArgumentException> {
            DownloadManifest(
                id = "manifest-1",
                sourceId = "source",
                title = "Episode",
                assets = listOf(validAsset()),
                policy = AuthorizedDownloadPolicy.offlineUnavailable(),
                requestedAtEpochMs = 123,
            )
        }
    }

    @Test
    fun rejectsAbsoluteOutputPaths() {
        assertFailsWith<IllegalArgumentException> {
            validAsset().copy(relativeOutputPath = "/Anime/Episode 01.mkv")
        }
        assertFailsWith<IllegalArgumentException> {
            validAsset().copy(relativeOutputPath = "\\Anime\\Episode 01.mkv")
        }
        assertFailsWith<IllegalArgumentException> {
            validAsset().copy(relativeOutputPath = "C:\\Anime\\Episode 01.mkv")
        }
        assertFailsWith<IllegalArgumentException> {
            validAsset().copy(relativeOutputPath = "Example Show/../Episode 01.mkv")
        }
    }

    private fun validAsset(): DownloadAsset =
        DownloadAsset(
            id = "media",
            kind = DownloadAssetKind.MEDIA,
            sourceUri = "https://example.invalid/video",
            relativeOutputPath = "Example Show/Episode 01.mkv",
            mediaType = "video/mp4",
        )
}
