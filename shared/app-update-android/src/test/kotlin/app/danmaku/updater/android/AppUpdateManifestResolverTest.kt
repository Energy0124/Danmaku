package app.danmaku.updater.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateManifestResolverTest {
    private val configuration = AppUpdateConfiguration(
        manifestUrl = "https://github.com/Energy0124/Danmaku/releases/latest/download/android-update.json",
        appKind = AppUpdateKind.MOBILE,
        applicationId = "app.danmaku.mobile",
        currentVersionCode = 1_000,
        currentVersionName = "0.1.0",
    )

    @Test
    fun resolvesTheMatchingNewerApplication() {
        val update = resolveAvailableUpdate(manifest(versionCode = 2_000), configuration)

        requireNotNull(update)
        assertEquals("v0.2.0", update.releaseTag)
        assertEquals("danmaku-android-mobile.apk", update.assetName)
        assertEquals(2_000, update.versionCode)
        assertEquals("a".repeat(64), update.sha256)
    }

    @Test
    fun treatsEqualOrOlderVersionCodesAsCurrent() {
        assertNull(resolveAvailableUpdate(manifest(versionCode = 1_000), configuration))
        assertNull(resolveAvailableUpdate(manifest(versionCode = 999), configuration))
    }

    @Test
    fun rejectsWrongPackagesAndOffRepositoryAssets() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveAvailableUpdate(
                manifest(versionCode = 2_000).replace("app.danmaku.mobile", "app.example.mobile"),
                configuration,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            resolveAvailableUpdate(
                manifest(versionCode = 2_000).replace(
                    "https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/danmaku-android-mobile.apk",
                    "https://example.com/danmaku-android-mobile.apk",
                ),
                configuration,
            )
        }
    }

    @Test
    fun rejectsMalformedChecksumsAndDuplicateTargets() {
        assertThrows(IllegalArgumentException::class.java) {
            resolveAvailableUpdate(manifest(versionCode = 2_000).replace("a".repeat(64), "bad"), configuration)
        }
        val duplicated = manifest(versionCode = 2_000).replace(
            "\"apps\": [",
            "\"apps\": [{\"kind\":\"mobile\",\"applicationId\":\"app.danmaku.mobile\",\"assetName\":\"duplicate.apk\",\"apkUrl\":\"https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/duplicate.apk\",\"sha256\":\"${"b".repeat(64)}\",\"sizeBytes\":10},",
        )
        assertThrows(IllegalArgumentException::class.java) {
            resolveAvailableUpdate(duplicated, configuration)
        }
    }

    @Test
    fun automaticChecksAreDueDailyAndRecoverFromClockRollback() {
        val now = 2 * AUTOMATIC_UPDATE_CHECK_INTERVAL_MS
        assertTrue(isAutomaticUpdateCheckDue(null, now))
        assertTrue(isAutomaticUpdateCheckDue(now - AUTOMATIC_UPDATE_CHECK_INTERVAL_MS, now))
        assertTrue(isAutomaticUpdateCheckDue(now + 1, now))
        assertEquals(false, isAutomaticUpdateCheckDue(now - 1, now))
    }

    private fun manifest(versionCode: Long): String =
        """
        {
          "schemaVersion": 1,
          "release": {
            "tag": "v0.2.0",
            "versionName": "0.2.0",
            "versionCode": $versionCode,
            "pageUrl": "https://github.com/Energy0124/Danmaku/releases/tag/v0.2.0"
          },
          "apps": [
            {
              "kind": "mobile",
              "applicationId": "app.danmaku.mobile",
              "assetName": "danmaku-android-mobile.apk",
              "apkUrl": "https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/danmaku-android-mobile.apk",
              "sha256": "${"a".repeat(64)}",
              "sizeBytes": 12345
            },
            {
              "kind": "tv",
              "applicationId": "app.danmaku.tv",
              "assetName": "danmaku-android-tv.apk",
              "apkUrl": "https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/danmaku-android-tv.apk",
              "sha256": "${"b".repeat(64)}",
              "sizeBytes": 23456
            }
          ]
        }
        """.trimIndent()
}
