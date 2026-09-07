package app.danmaku.updater.android

import org.junit.Assert.assertThrows
import org.junit.Test

class AppUpdatePackageVerifierTest {
    private val update = AvailableAppUpdate(
        releaseTag = "v0.2.0",
        versionName = "0.2.0",
        versionCode = 2_000,
        releasePageUrl = "https://github.com/Energy0124/Danmaku/releases/tag/v0.2.0",
        assetName = "danmaku-android-mobile.apk",
        apkUrl = "https://github.com/Energy0124/Danmaku/releases/download/v0.2.0/danmaku-android-mobile.apk",
        sha256 = "a".repeat(64),
        sizeBytes = 10,
    )
    private val installed = AppUpdatePackageIdentity("app.danmaku.mobile", 1_000, setOf("certificate"))

    @Test
    fun acceptsMatchingPackageVersionAndCertificate() {
        validateAppUpdatePackageIdentity(
            installed,
            AppUpdatePackageIdentity("app.danmaku.mobile", 2_000, setOf("certificate")),
            update,
        )
    }

    @Test
    fun rejectsWrongPackageVersionOrCertificate() {
        assertThrows(IllegalArgumentException::class.java) {
            validateAppUpdatePackageIdentity(
                installed,
                AppUpdatePackageIdentity("app.danmaku.tv", 2_000, setOf("certificate")),
                update,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateAppUpdatePackageIdentity(
                installed,
                AppUpdatePackageIdentity("app.danmaku.mobile", 2_001, setOf("certificate")),
                update,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateAppUpdatePackageIdentity(
                installed,
                AppUpdatePackageIdentity("app.danmaku.mobile", 2_000, setOf("other")),
                update,
            )
        }
    }
}
