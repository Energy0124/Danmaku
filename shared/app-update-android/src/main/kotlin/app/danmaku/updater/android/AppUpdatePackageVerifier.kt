package app.danmaku.updater.android

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal interface AppUpdatePackageVerifier {
    fun verify(apk: File, update: AvailableAppUpdate)
}

internal data class AppUpdatePackageIdentity(
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
)

internal fun validateAppUpdatePackageIdentity(
    installed: AppUpdatePackageIdentity,
    downloaded: AppUpdatePackageIdentity,
    update: AvailableAppUpdate,
) {
    require(downloaded.packageName == installed.packageName) { "Downloaded APK package does not match" }
    require(downloaded.versionCode == update.versionCode) { "Downloaded APK version does not match" }
    require(installed.signerDigests.isNotEmpty() && installed.signerDigests == downloaded.signerDigests) {
        "Downloaded APK signing certificate does not match"
    }
}

internal class AndroidAppUpdatePackageVerifier(
    context: Context,
) : AppUpdatePackageVerifier {
    private val packageManager = context.packageManager
    private val installedPackageName = context.packageName

    override fun verify(apk: File, update: AvailableAppUpdate) {
        val archive = packageArchiveInfo(apk)
            ?: throw IllegalArgumentException("Downloaded file is not a valid APK")
        val installed = installedPackageInfo()
        validateAppUpdatePackageIdentity(
            installed = AppUpdatePackageIdentity(
                packageName = installedPackageName,
                versionCode = installed.longVersionCodeCompat(),
                signerDigests = installed.currentSignerDigests(),
            ),
            downloaded = AppUpdatePackageIdentity(
                packageName = archive.packageName,
                versionCode = archive.longVersionCodeCompat(),
                signerDigests = archive.currentSignerDigests(),
            ),
            update = update,
        )
    }

    @Suppress("DEPRECATION")
    private fun packageArchiveInfo(apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageArchiveInfo(
                apk.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }

    @Suppress("DEPRECATION")
    private fun installedPackageInfo(): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getPackageInfo(
                installedPackageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
            )
        } else {
            val flags = if (Build.VERSION.SDK_INT >= 28) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }
            packageManager.getPackageInfo(installedPackageName, flags)
        }
}

@Suppress("DEPRECATION")
private fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= 28) longVersionCode else versionCode.toLong()

@Suppress("DEPRECATION")
private fun PackageInfo.currentSignerDigests(): Set<String> {
    val currentSigners = if (Build.VERSION.SDK_INT >= 28) {
        signingInfo?.apkContentsSigners.orEmpty()
    } else {
        signatures.orEmpty()
    }
    return currentSigners.mapTo(mutableSetOf()) { signature ->
        MessageDigest.getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
