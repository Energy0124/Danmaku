package app.danmaku.library.android

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.system.Os
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.danmaku.domain.LanDanmakuTrack
import app.danmaku.domain.AuthorizedDownloadPolicy
import app.danmaku.domain.DownloadAsset
import app.danmaku.domain.DownloadAssetKind
import app.danmaku.domain.DownloadAuthorization
import app.danmaku.domain.DownloadDrmPolicy
import app.danmaku.domain.DownloadManifest
import app.danmaku.domain.OfflineStoragePolicy
import app.danmaku.domain.LibraryMediaItem
import app.danmaku.domain.LibrarySubtitleTrack
import app.danmaku.domain.PlaybackProgress
import app.danmaku.domain.PlaybackSnapshot
import app.danmaku.domain.PlaybackSource
import app.danmaku.domain.toPlaybackProgress
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class OfflineCacheState {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    RETRYING,
    READY,
    FAILED,
}

@Serializable
data class OfflineCacheEntry(
    val key: String,
    val serverUrl: String,
    val item: LibraryMediaItem,
    val manifest: DownloadManifest,
    val state: OfflineCacheState,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = item.sizeBytes,
    val videoPath: String? = null,
    val danmakuPath: String? = null,
    val subtitlePaths: Map<String, String> = emptyMap(),
    val posterPath: String? = null,
    val warnings: List<String> = emptyList(),
    val errorMessage: String? = null,
    val cachedAtEpochMs: Long? = null,
    val pendingProgress: PlaybackProgress? = null,
)

data class OfflineSubtitlePreparation(
    val track: LibrarySubtitleTrack,
    val source: PlaybackSource.LocalFile,
)

data class OfflinePlaybackPreparation(
    val cacheKey: String,
    val item: LibraryMediaItem,
    val source: PlaybackSource.LocalFile,
    val subtitles: List<OfflineSubtitlePreparation>,
    val danmaku: LanDanmakuTrack,
    val resumePositionMs: Long?,
)

@Serializable
private data class OfflineCacheIndex(
    val version: Int = INDEX_VERSION,
    val entries: List<OfflineCacheEntry> = emptyList(),
)

class AndroidOfflineCacheRepository(
    context: Context,
    private val json: Json = DEFAULT_JSON,
) {
    private val appContext = context.applicationContext
    private val root = cacheRoot(appContext)
    private val indexFile = File(root, "index.json")
    private val workManager = WorkManager.getInstance(appContext)

    init {
        root.mkdirs()
    }

    fun entries(): List<OfflineCacheEntry> = readIndex().entries

    fun availableBytes(): Long = root.usableSpace

    fun entry(serverUrl: String, mediaId: String): OfflineCacheEntry? =
        entries().firstOrNull { it.key == cacheKey(serverUrl, mediaId) }

    fun entry(key: String): OfflineCacheEntry? = entries().firstOrNull { it.key == key }

    fun enqueue(serverUrl: String, items: List<LibraryMediaItem>): List<OfflineCacheEntry> {
        val normalizedServerUrl = serverUrl.trim().trimEnd('/')
        require(normalizedServerUrl.isNotBlank()) { "serverUrl must not be blank" }
        val selected = items.distinctBy(LibraryMediaItem::id)
        val existingReadyIds = entries()
            .filter { it.serverUrl == normalizedServerUrl && it.state == OfflineCacheState.READY }
            .mapTo(mutableSetOf()) { it.item.id }
        val requiredBytes = selected.filterNot { it.id in existingReadyIds }.sumOf { it.sizeBytes }
        require(requiredBytes <= (availableBytes() - SPACE_RESERVE_BYTES).coerceAtLeast(0)) {
            "Not enough free storage for this download"
        }
        val updated = mutateIndex { index ->
            val byKey = index.entries.associateByTo(linkedMapOf(), OfflineCacheEntry::key)
            selected.forEach { item ->
                val key = cacheKey(normalizedServerUrl, item.id)
                val current = byKey[key]
                if (current?.state != OfflineCacheState.READY || !isPlayable(current)) {
                    byKey[key] = OfflineCacheEntry(
                        key = key,
                        serverUrl = normalizedServerUrl,
                        item = item,
                        manifest = offlineManifest(normalizedServerUrl, item, key),
                        state = OfflineCacheState.QUEUED,
                        downloadedBytes = current?.downloadedBytes ?: 0,
                    )
                }
            }
            index.copy(entries = byKey.values.toList())
        }
        selected.forEach { item ->
            val key = cacheKey(normalizedServerUrl, item.id)
            updated.entries.firstOrNull { it.key == key }
                ?.takeIf { it.state == OfflineCacheState.QUEUED }
                ?.let { schedule(it.key) }
        }
        return updated.entries.filter { entry -> selected.any { it.id == entry.item.id } }
    }

    fun pause(key: String) {
        workManager.cancelUniqueWork(workName(key))
        updateEntry(key) { it.copy(state = OfflineCacheState.PAUSED, errorMessage = null) }
    }

    fun resume(key: String) {
        updateEntry(key) { it.copy(state = OfflineCacheState.QUEUED, errorMessage = null) }
        schedule(key)
    }

    fun retry(key: String) = resume(key)

    fun delete(key: String) {
        workManager.cancelUniqueWork(workName(key))
        File(root, key).deleteRecursively()
        mutateIndex { index -> index.copy(entries = index.entries.filterNot { it.key == key }) }
    }

    fun clear() {
        entries().forEach { workManager.cancelUniqueWork(workName(it.key)) }
        root.listFiles()?.filter { it != indexFile }?.forEach(File::deleteRecursively)
        writeIndex(OfflineCacheIndex())
    }

    fun playable(serverUrl: String, mediaId: String): OfflinePlaybackPreparation? =
        entry(serverUrl, mediaId)?.let(::playable)

    fun playable(key: String): OfflinePlaybackPreparation? =
        entries().firstOrNull { it.key == key }?.let(::playable)

    fun savePendingProgress(key: String, progress: PlaybackProgress) {
        updateEntry(key) { current ->
            val existing = current.pendingProgress
            if (existing == null || progress.updatedAtEpochMs >= existing.updatedAtEpochMs) {
                current.copy(pendingProgress = progress)
            } else {
                current
            }
        }
    }

    fun savePendingProgress(key: String, snapshot: PlaybackSnapshot, updatedAtEpochMs: Long) {
        val mediaId = entry(key)?.item?.id ?: return
        snapshot.toPlaybackProgress(mediaId, updatedAtEpochMs)?.let { savePendingProgress(key, it) }
    }

    fun clearPendingProgress(key: String) {
        updateEntry(key) { it.copy(pendingProgress = null) }
    }

    fun syncPendingProgress(
        serverUrl: String,
        pairingToken: String,
        remoteProgress: List<PlaybackProgress>,
    ): List<PlaybackProgress> {
        val normalized = serverUrl.trim().trimEnd('/')
        val remoteById = remoteProgress.associateBy(PlaybackProgress::mediaId).toMutableMap()
        entries()
            .filter { it.serverUrl == normalized && it.pendingProgress != null }
            .forEach { entry ->
                val pending = entry.pendingProgress ?: return@forEach
                val remote = remoteById[pending.mediaId]
                if (remote != null && remote.updatedAtEpochMs >= pending.updatedAtEpochMs) {
                    clearPendingProgress(entry.key)
                } else {
                    runCatching {
                        LanLibraryClient().saveProgress(normalized, pairingToken, pending)
                    }.onSuccess {
                        remoteById[pending.mediaId] = pending
                        clearPendingProgress(entry.key)
                    }
                }
            }
        return remoteById.values.sortedByDescending(PlaybackProgress::updatedAtEpochMs)
    }

    internal fun updateEntry(key: String, transform: (OfflineCacheEntry) -> OfflineCacheEntry) {
        mutateIndex { index ->
            index.copy(entries = index.entries.map { if (it.key == key) transform(it) else it })
        }
    }

    internal fun entryByKey(key: String): OfflineCacheEntry? =
        entries().firstOrNull { it.key == key }

    internal fun directory(key: String): File = File(root, key).also(File::mkdirs)

    private fun playable(entry: OfflineCacheEntry): OfflinePlaybackPreparation? {
        if (entry.state != OfflineCacheState.READY || !isPlayable(entry)) return null
        val video = File(root, entry.videoPath ?: return null)
        val danmakuFile = File(root, entry.danmakuPath ?: return null)
        val danmaku = runCatching {
            json.decodeFromString<LanDanmakuTrack>(danmakuFile.readText())
        }.getOrNull() ?: return null
        return OfflinePlaybackPreparation(
            cacheKey = entry.key,
            item = entry.item,
            source = PlaybackSource.LocalFile(video.toURI().toString()),
            subtitles = entry.item.subtitles.mapNotNull { track ->
                val path = entry.subtitlePaths[track.id] ?: return@mapNotNull null
                File(root, path).takeIf(File::isFile)?.let {
                    OfflineSubtitlePreparation(track, PlaybackSource.LocalFile(it.toURI().toString()))
                }
            },
            danmaku = danmaku,
            resumePositionMs = entry.pendingProgress?.positionMs,
        )
    }

    private fun isPlayable(entry: OfflineCacheEntry): Boolean =
        entry.videoPath?.let { File(root, it).isFile } == true &&
            entry.danmakuPath?.let { File(root, it).isFile } == true

    private fun schedule(key: String) {
        val request = OneTimeWorkRequestBuilder<OfflineDownloadWorker>()
            .setInputData(Data.Builder().putString(WORK_KEY, key).build())
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(WORK_TAG)
            .build()
        workManager.enqueueUniqueWork(workName(key), ExistingWorkPolicy.REPLACE, request)
    }

    private fun readIndex(): OfflineCacheIndex = synchronized(INDEX_LOCK) {
        if (!indexFile.isFile) return@synchronized OfflineCacheIndex()
        runCatching { json.decodeFromString<OfflineCacheIndex>(indexFile.readText()) }
            .getOrNull()
            ?.takeIf { it.version == INDEX_VERSION }
            ?: OfflineCacheIndex()
    }

    private fun mutateIndex(transform: (OfflineCacheIndex) -> OfflineCacheIndex): OfflineCacheIndex =
        synchronized(INDEX_LOCK) {
            val current = if (indexFile.isFile) {
                runCatching { json.decodeFromString<OfflineCacheIndex>(indexFile.readText()) }
                    .getOrDefault(OfflineCacheIndex())
            } else {
                OfflineCacheIndex()
            }
            transform(current).also(::writeIndexLocked)
        }

    private fun writeIndex(index: OfflineCacheIndex) = synchronized(INDEX_LOCK) {
        writeIndexLocked(index)
    }

    private fun writeIndexLocked(index: OfflineCacheIndex) {
        root.mkdirs()
        val temporary = File(root, "index.json.tmp")
        temporary.writeText(json.encodeToString(index))
        try {
            Os.rename(temporary.absolutePath, indexFile.absolutePath)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Unable to commit offline cache index", error)
        }
    }

    companion object {
        private val INDEX_LOCK = Any()

        fun cacheKey(serverUrl: String, mediaId: String): String {
            val input = "${serverUrl.trim().trimEnd('/').lowercase()}\n$mediaId"
            return MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray())
                .take(20)
                .joinToString("") { "%02x".format(it) }
        }

        internal fun workName(key: String) = "danmaku-offline-cache-$key"
    }
}

class OfflineDownloadWorker(
    appContext: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(appContext, parameters) {
    private val repository = AndroidOfflineCacheRepository(appContext)
    private val libraryClient = LanLibraryClient()

    override suspend fun doWork(): Result = DOWNLOAD_MUTEX.withLock {
        val key = inputData.getString(WORK_KEY) ?: return@withLock Result.failure()
        val entry = repository.entryByKey(key) ?: return@withLock Result.success()
        setForeground(foregroundInfo(entry, entry.downloadedBytes))
        repository.updateEntry(key) {
            it.copy(
                state = if (runAttemptCount == 0) OfflineCacheState.DOWNLOADING else OfflineCacheState.RETRYING,
                errorMessage = null,
            )
        }
        try {
            withContext(Dispatchers.IO) { download(entry) }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: PermanentDownloadException) {
            repository.updateEntry(key) {
                it.copy(state = OfflineCacheState.FAILED, errorMessage = error.message)
            }
            Result.failure()
        } catch (error: Throwable) {
            if (runAttemptCount >= MAX_RETRIES - 1) {
                repository.updateEntry(key) {
                    it.copy(state = OfflineCacheState.FAILED, errorMessage = error.userMessage())
                }
                Result.failure()
            } else {
                repository.updateEntry(key) {
                    it.copy(state = OfflineCacheState.RETRYING, errorMessage = error.userMessage())
                }
                Result.retry()
            }
        }
    }

    private suspend fun download(entry: OfflineCacheEntry) {
        val token = AndroidLanLibraryConnectionStore(applicationContext)
            .loadProfiles()
            .firstOrNull { it.normalizedBaseUrl == entry.serverUrl }
            ?.pairingToken
            .orEmpty()
        val directory = repository.directory(entry.key)
        val video = File(directory, "video.${entry.item.relativePath.safeExtension("media")}")
        downloadResumable(
            url = libraryClient.streamUrl(entry.serverUrl, entry.item, token),
            destination = video,
            expectedBytes = entry.item.sizeBytes,
            entry = entry,
        )

        val danmaku = libraryClient.fetchDanmaku(entry.serverUrl, entry.item.id, token)
        val danmakuFile = File(directory, "danmaku.json")
        danmakuFile.writeText(DEFAULT_JSON.encodeToString(danmaku))

        val warnings = mutableListOf<String>()
        val subtitlePaths = entry.item.subtitles.mapIndexedNotNull { index, subtitle ->
            val file = File(directory, "subtitle-$index.${subtitle.relativePath.safeExtension("sub")}")
            runCatching {
                downloadSmall(libraryClient.subtitleUrl(entry.serverUrl, subtitle, token), file)
                subtitle.id to file.relativeTo(cacheRoot(applicationContext)).path
            }.onFailure { warnings += "${subtitle.label}: ${it.userMessage()}" }.getOrNull()
        }.toMap()
        val posterPath = entry.item.posterPath?.let { path ->
            val poster = File(directory, "poster.${path.safeExtension("image")}")
            runCatching {
                downloadSmall("${entry.serverUrl}$path", poster)
                poster.relativeTo(cacheRoot(applicationContext)).path
            }.onFailure { warnings += "Poster: ${it.userMessage()}" }.getOrNull()
        }
        val totalBytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length)
        repository.updateEntry(entry.key) {
            it.copy(
                state = OfflineCacheState.READY,
                downloadedBytes = totalBytes,
                totalBytes = totalBytes,
                videoPath = video.relativeTo(cacheRoot(applicationContext)).path,
                danmakuPath = danmakuFile.relativeTo(cacheRoot(applicationContext)).path,
                subtitlePaths = subtitlePaths,
                posterPath = posterPath,
                warnings = warnings,
                errorMessage = null,
                cachedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    private suspend fun downloadResumable(
        url: String,
        destination: File,
        expectedBytes: Long,
        entry: OfflineCacheEntry,
    ) {
        if (destination.isFile && destination.length() == expectedBytes) return
        val part = File(destination.parentFile, "${destination.name}.part")
        var existingBytes = part.takeIf(File::isFile)?.length() ?: 0L
        if (existingBytes > expectedBytes) {
            part.delete()
            existingBytes = 0
        }
        val connection = open(url).apply {
            if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
        }
        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                throw PermanentDownloadException("Video is no longer available on the PC")
            }
            if (responseCode !in setOf(HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_PARTIAL)) {
                throw connection.failure()
            }
            val append = existingBytes > 0 && responseCode == HttpURLConnection.HTTP_PARTIAL
            if (!append) existingBytes = 0
            FileOutputStream(part, append).use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = existingBytes
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        repository.updateEntry(entry.key) {
                            it.copy(downloadedBytes = downloaded, totalBytes = expectedBytes)
                        }
                        setProgress(Data.Builder().putLong("downloadedBytes", downloaded).build())
                        setForeground(foregroundInfo(entry, downloaded))
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (part.length() != expectedBytes) {
            throw IllegalStateException("Downloaded ${part.length()} of $expectedBytes bytes")
        }
        if (destination.exists()) destination.delete()
        if (!part.renameTo(destination)) throw IllegalStateException("Unable to finalize video cache")
    }

    private fun downloadSmall(url: String, destination: File) {
        val temporary = File(destination.parentFile, "${destination.name}.part")
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw connection.failure()
            FileOutputStream(temporary, false).use { output ->
                connection.inputStream.use { it.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (destination.exists()) destination.delete()
        if (!temporary.renameTo(destination)) throw IllegalStateException("Unable to finalize cached asset")
    }

    private fun open(url: String): HttpURLConnection =
        (URI(url).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 30_000
        }

    private fun foregroundInfo(entry: OfflineCacheEntry, downloadedBytes: Long): ForegroundInfo {
        ensureNotificationChannel()
        val pauseIntent = PendingIntent.getBroadcast(
            applicationContext,
            entry.key.hashCode(),
            Intent(applicationContext, OfflineDownloadActionReceiver::class.java)
                .setAction(ACTION_PAUSE)
                .putExtra(WORK_KEY, entry.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getBroadcast(
            applicationContext,
            entry.key.hashCode() xor 0x5a5a5a5a,
            Intent(applicationContext, OfflineDownloadActionReceiver::class.java)
                .setAction(ACTION_CANCEL)
                .putExtra(WORK_KEY, entry.key),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val progress = if (entry.totalBytes > 0) {
            ((downloadedBytes * 100) / entry.totalBytes).toInt().coerceIn(0, 100)
        } else {
            0
        }
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(entry.item.episodeTitle)
            .setContentText(entry.item.seriesTitle)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, entry.totalBytes <= 0)
            .addAction(0, "Pause", pauseIntent)
            .addAction(0, "Cancel", cancelIntent)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(entry.key.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(entry.key.hashCode(), notification)
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        applicationContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    "Offline downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
    }

    private companion object {
        val DOWNLOAD_MUTEX = Mutex()
        const val MAX_RETRIES = 5
    }
}

class OfflineDownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(WORK_KEY) ?: return
        val repository = AndroidOfflineCacheRepository(context)
        when (intent.action) {
            ACTION_PAUSE -> repository.pause(key)
            ACTION_CANCEL -> repository.delete(key)
        }
    }
}

private class PermanentDownloadException(message: String) : IllegalStateException(message)

private fun HttpURLConnection.failure(): Throwable =
    if (responseCode in 400..499) {
        PermanentDownloadException("Server returned HTTP $responseCode")
    } else {
        IllegalStateException("Server returned HTTP $responseCode")
    }

private fun Throwable.userMessage(): String = message?.lineSequence()?.firstOrNull()?.take(200)
    ?: this::class.simpleName
    ?: "Download failed"

private fun String.safeExtension(fallback: String): String =
    substringAfterLast('.', "")
        .substringBefore('?')
        .filter(Char::isLetterOrDigit)
        .take(8)
        .ifBlank { fallback }
        .lowercase()

private fun cacheRoot(context: Context): File {
    val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
    return File(external ?: context.filesDir, "offline-cache")
}

private fun offlineManifest(
    serverUrl: String,
    item: LibraryMediaItem,
    key: String,
): DownloadManifest {
    val assets = buildList {
        add(
            DownloadAsset(
                id = "video",
                kind = DownloadAssetKind.MEDIA,
                sourceUri = "$serverUrl${item.streamPath}",
                relativeOutputPath = "$key/video.${item.relativePath.safeExtension("media")}",
                mediaType = item.mediaType,
                sizeBytes = item.sizeBytes,
            ),
        )
        add(
            DownloadAsset(
                id = "danmaku",
                kind = DownloadAssetKind.DANMAKU,
                sourceUri = "$serverUrl/api/danmaku/${item.id}",
                relativeOutputPath = "$key/danmaku.json",
                mediaType = "application/json",
            ),
        )
        item.subtitles.forEachIndexed { index, subtitle ->
            add(
                DownloadAsset(
                    id = "subtitle:${subtitle.id}",
                    kind = DownloadAssetKind.SUBTITLE,
                    sourceUri = "$serverUrl${subtitle.streamPath}",
                    relativeOutputPath = "$key/subtitle-$index.${subtitle.relativePath.safeExtension("sub")}",
                    mediaType = subtitle.mediaType,
                ),
            )
        }
        item.posterPath?.let { posterPath ->
            add(
                DownloadAsset(
                    id = "poster",
                    kind = DownloadAssetKind.ARTWORK,
                    sourceUri = "$serverUrl$posterPath",
                    relativeOutputPath = "$key/poster.${posterPath.safeExtension("image")}",
                    mediaType = "image/*",
                ),
            )
        }
    }
    return DownloadManifest(
        id = key,
        sourceId = "lan-library:${serverUrl.lowercase()}",
        title = "${item.seriesTitle} · ${item.episodeTitle}",
        assets = assets,
        policy = AuthorizedDownloadPolicy(
            offlineStorage = OfflineStoragePolicy.ALLOWED_WITHOUT_EXPIRY,
            authorization = DownloadAuthorization.USER_OWNED_LOCAL_FILE,
            drm = DownloadDrmPolicy.DRM_FREE,
            attribution = "User-owned desktop library",
            requiresUserConfirmation = false,
        ),
        requestedAtEpochMs = System.currentTimeMillis(),
    )
}

private const val INDEX_VERSION = 1
private const val WORK_KEY = "offlineCacheKey"
private const val WORK_TAG = "danmaku-offline-cache"
private const val ACTION_PAUSE = "app.danmaku.offline.PAUSE"
private const val ACTION_CANCEL = "app.danmaku.offline.CANCEL"
private const val NOTIFICATION_CHANNEL = "offline-downloads"
private const val SPACE_RESERVE_BYTES = 256L * 1024L * 1024L
private val DEFAULT_JSON = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
