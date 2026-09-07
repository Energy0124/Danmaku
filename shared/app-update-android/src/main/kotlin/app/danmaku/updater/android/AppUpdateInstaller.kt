package app.danmaku.updater.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

class AppUpdateInstaller(
    private val context: Context,
) {
    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < 26 || context.packageManager.canRequestPackageInstalls()

    fun launchUnknownSourcesSettings(): Boolean {
        val intent = unknownSourcesSettingsIntent() ?: return true
        return launch(intent)
    }

    fun unknownSourcesSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < 26) return null
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        )
    }

    fun launchPackageInstaller(apkPath: String): Boolean {
        val intent = packageInstallerIntent(apkPath) ?: return false
        return launch(intent)
    }

    fun packageInstallerIntent(apkPath: String): Intent? {
        val apk = File(apkPath)
        if (!apk.isFile) return null
        val uri = try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.appupdate.files",
                apk,
            )
        } catch (_: IllegalArgumentException) {
            return null
        }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
