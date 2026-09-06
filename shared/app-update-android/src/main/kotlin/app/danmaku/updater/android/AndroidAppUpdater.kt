package app.danmaku.updater.android

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal interface AppUpdateCheckStore {
    fun lastAttemptEpochMs(): Long?
    fun recordAttempt(epochMs: Long)
}

private class SharedPreferencesAppUpdateCheckStore(context: Context) : AppUpdateCheckStore {
    private val preferences = context.getSharedPreferences("app-update", Context.MODE_PRIVATE)

    override fun lastAttemptEpochMs(): Long? =
        if (preferences.contains(LAST_ATTEMPT)) preferences.getLong(LAST_ATTEMPT, 0L) else null

    override fun recordAttempt(epochMs: Long) {
        preferences.edit().putLong(LAST_ATTEMPT, epochMs).apply()
    }

    private companion object {
        const val LAST_ATTEMPT = "last-automatic-check-at"
    }
}

internal class AndroidAppUpdater(
    context: Context,
    private val configuration: AppUpdateConfiguration,
    private val transport: AppUpdateTransport = HttpAppUpdateTransport(),
    private val packageVerifier: AppUpdatePackageVerifier = AndroidAppUpdatePackageVerifier(context),
    private val checkStore: AppUpdateCheckStore = SharedPreferencesAppUpdateCheckStore(context),
    private val nowEpochMs: () -> Long = System::currentTimeMillis,
) {
    private val applicationContext = context.applicationContext
    private val operationMutex = Mutex()
    private val mutableState = MutableStateFlow<AppUpdateState>(
        if (configuration.manifestUrl.isBlank()) AppUpdateState.Disabled else AppUpdateState.Idle,
    )
    val state: StateFlow<AppUpdateState> = mutableState.asStateFlow()

    suspend fun check(manual: Boolean) = operationMutex.withLock {
        if (configuration.manifestUrl.isBlank()) {
            mutableState.value = AppUpdateState.Disabled
            return@withLock
        }
        val now = nowEpochMs()
        if (!manual && !isAutomaticUpdateCheckDue(checkStore.lastAttemptEpochMs(), now)) return@withLock
        checkStore.recordAttempt(now)

        mutableState.value = AppUpdateState.Checking
        runCatching {
            val manifest = transport.getText(configuration.manifestUrl)
            resolveAvailableUpdate(manifest, configuration)
        }.onSuccess { update ->
            mutableState.value = if (update == null) {
                AppUpdateState.Current(configuration.currentVersionName)
            } else {
                AppUpdateState.Available(update)
            }
        }.onFailure {
            mutableState.value = if (manual) {
                AppUpdateState.Failed(AppUpdateFailureStage.CHECK)
            } else {
                AppUpdateState.Idle
            }
        }
    }

    suspend fun download() = operationMutex.withLock {
        val update = when (val current = mutableState.value) {
            is AppUpdateState.Available -> current.update
            is AppUpdateState.Failed -> current.update
            else -> null
        } ?: return@withLock

        val updateDirectory = File(applicationContext.cacheDir, "app-updates")
        val partial = File(updateDirectory, "${update.assetName}.part")
        val completed = File(updateDirectory, update.assetName)
        val downloadResult = runCatching {
            partial.delete()
            val result = transport.download(
                url = update.apkUrl,
                destination = partial,
                expectedSizeBytes = update.sizeBytes,
            ) { downloaded ->
                mutableState.value = AppUpdateState.Downloading(update, downloaded)
            }
            require(result.sizeBytes == update.sizeBytes) { "Downloaded APK size does not match" }
            require(result.sha256.equals(update.sha256, ignoreCase = true)) {
                "Downloaded APK checksum does not match"
            }
        }.onFailure {
            partial.delete()
            mutableState.value = AppUpdateState.Failed(
                stage = AppUpdateFailureStage.DOWNLOAD,
                update = update,
            )
        }
        if (downloadResult.isFailure) return@withLock

        runCatching {
            packageVerifier.verify(partial, update)
            completed.delete()
            require(partial.renameTo(completed)) { "Could not finalize downloaded APK" }
            completed
        }.onSuccess { apk ->
            mutableState.value = AppUpdateState.Ready(update, apk.absolutePath)
        }.onFailure {
            partial.delete()
            mutableState.value = AppUpdateState.Failed(
                stage = AppUpdateFailureStage.VERIFY,
                update = update,
            )
        }
    }

    fun dismiss() {
        mutableState.value = if (configuration.manifestUrl.isBlank()) {
            AppUpdateState.Disabled
        } else {
            AppUpdateState.Idle
        }
    }
}
