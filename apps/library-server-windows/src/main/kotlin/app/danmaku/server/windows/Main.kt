package app.danmaku.server.windows

import app.danmaku.domain.LanLibraryServerStatus
import app.danmaku.host.LibraryHostMode
import app.danmaku.host.LibraryHostOperationResult
import app.danmaku.host.LibraryHostOperationStatus
import app.danmaku.host.LibraryHostRuntimeStatus
import app.danmaku.host.LibraryHostService
import app.danmaku.server.FilePlaybackProgressStore
import app.danmaku.server.LocalLibraryDiscoveryAnnouncer
import app.danmaku.server.LocalLibraryServer
import app.danmaku.server.PublishedLibrary
import app.danmaku.server.StaticWebAssets
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

fun main(args: Array<String>) {
    val options = HeadlessServerOptions.parse(args.asList(), System.getenv())
    HeadlessLibraryServer(options).use { host ->
        val start = host.start()
        println(start.message)
        println("Base URLs: ${host.runtimeStatus.baseUrls.joinToString()}")
        println("Web UI URLs: ${host.runtimeStatus.webUiUrls.joinToString().ifBlank { "disabled" }}")
        println("Pairing token: ${host.pairingToken}")
        Runtime.getRuntime().addShutdownHook(Thread { host.close() })
        CountDownLatch(1).await()
    }
}

internal data class HeadlessServerOptions(
    val dataDirectory: Path,
    val libraryRoots: List<Path> = emptyList(),
    val port: Int = LocalLibraryServer.DEFAULT_PORT,
    val pairingToken: String? = null,
    val webAssetsRoot: Path? = null,
) {
    init {
        require(port in 0..65_535) { "port must be between 0 and 65535" }
        require(pairingToken == null || pairingToken.isNotBlank()) { "pairingToken must not be blank" }
    }

    companion object {
        private const val DATA_DIR_ENV = "DANMAKU_SERVER_DATA_DIR"
        private const val WEB_UI_DIST_ENV = "DANMAKU_WEB_UI_DIST"

        fun parse(
            args: List<String>,
            environment: Map<String, String>,
        ): HeadlessServerOptions {
            var dataDirectory = environment[DATA_DIR_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)
                ?: Path.of("data", "library-server")
            val roots = mutableListOf<Path>()
            var port = LocalLibraryServer.DEFAULT_PORT
            var pairingToken: String? = null
            var webAssetsRoot = environment[WEB_UI_DIST_ENV]
                ?.takeIf(String::isNotBlank)
                ?.let(Path::of)

            var index = 0
            while (index < args.size) {
                val arg = args[index]
                when {
                    arg == "--data-dir" -> {
                        dataDirectory = args.valueAfter(arg, index).let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--data-dir=") -> {
                        dataDirectory = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                            ?: error("--data-dir requires a value")
                    }
                    arg == "--root" -> {
                        roots.add(args.valueAfter(arg, index).let(Path::of))
                        index += 1
                    }
                    arg.startsWith("--root=") -> {
                        roots.add(
                            arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                                ?: error("--root requires a value"),
                        )
                    }
                    arg == "--port" -> {
                        port = args.valueAfter(arg, index).toPort()
                        index += 1
                    }
                    arg.startsWith("--port=") -> {
                        port = arg.substringAfter("=").toPort()
                    }
                    arg == "--pairing-token" -> {
                        pairingToken = args.valueAfter(arg, index)
                        index += 1
                    }
                    arg.startsWith("--pairing-token=") -> {
                        pairingToken = arg.substringAfter("=").takeIf(String::isNotBlank)
                            ?: error("--pairing-token requires a value")
                    }
                    arg == "--web-assets-dir" || arg == "--web-ui-dist" -> {
                        webAssetsRoot = args.valueAfter(arg, index).let(Path::of)
                        index += 1
                    }
                    arg.startsWith("--web-assets-dir=") -> {
                        webAssetsRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                    arg.startsWith("--web-ui-dist=") -> {
                        webAssetsRoot = arg.substringAfter("=").takeIf(String::isNotBlank)?.let(Path::of)
                    }
                }
                index += 1
            }

            return HeadlessServerOptions(
                dataDirectory = dataDirectory,
                libraryRoots = roots,
                port = port,
                pairingToken = pairingToken,
                webAssetsRoot = webAssetsRoot,
            )
        }

        private fun List<String>.valueAfter(option: String, index: Int): String =
            getOrNull(index + 1)?.takeUnless { it.startsWith("--") }?.takeIf(String::isNotBlank)
                ?: error("$option requires a value")

        private fun String.toPort(): Int =
            toIntOrNull()
                ?.takeIf { it in 0..65_535 }
                ?: error("Port must be between 0 and 65535: $this")
    }
}

internal class HeadlessLibraryServer(
    private val options: HeadlessServerOptions,
    private val discoveryAnnouncerFactory: (Int) -> AutoCloseable = { port ->
        LocalLibraryDiscoveryAnnouncer(port).apply { start() }
    },
) : AutoCloseable,
    LibraryHostService {
    private val startedAtEpochMs = System.currentTimeMillis()
    private val lockHandle = DataDirectoryLock.acquire(options.dataDirectory)
    private val progressStore = FilePlaybackProgressStore(options.dataDirectory.resolve("progress.json"))
    private val catalogStore = HeadlessLibraryCatalogStore(options.dataDirectory.resolve("catalog.json"))
    private val cachedLibrary = catalogStore.load()
    private val server = createServer()
    private val closed = AtomicBoolean(false)
    private var discoveryAnnouncer: AutoCloseable? = null

    @Volatile
    private var publishedItemCount: Int = 0

    @Volatile
    private var lastPublishedAtEpochMs: Long? = null

    @Volatile
    private var lastScanStatus: LibraryHostOperationStatus = LibraryHostOperationStatus.IDLE

    val pairingToken: String
        get() = server.pairingToken

    init {
        val storedLibrary = cachedLibrary
        if (storedLibrary == null) {
            server.publish(PublishedLibrary.EMPTY)
        } else {
            publishStoredLibrary(storedLibrary)
        }
    }

    override val runtimeStatus: LibraryHostRuntimeStatus
        get() = LibraryHostRuntimeStatus(
            mode = LibraryHostMode.HEADLESS_SERVER,
            baseUrls = server.networkUrls(),
            webUiUrls = server.webUiUrls(),
            itemCount = publishedItemCount,
            startedAtEpochMs = startedAtEpochMs,
            lastPublishedAtEpochMs = lastPublishedAtEpochMs,
            lastScanStatus = lastScanStatus,
        )

    override fun start(): LibraryHostOperationResult {
        val publishResult = publishCurrentLibrary()
        server.start()
        discoveryAnnouncer = discoveryAnnouncer ?: discoveryAnnouncerFactory(server.localPort)
        return if (publishResult.status == LibraryHostOperationStatus.SUCCEEDED) {
            LibraryHostOperationResult(
                status = LibraryHostOperationStatus.SUCCEEDED,
                message = "Headless Danmaku library server started on port ${server.localPort}; " +
                    "published ${publishedItemCount} items.",
                itemCount = publishedItemCount,
            )
        } else {
            publishResult
        }
    }

    override fun stop(): LibraryHostOperationResult {
        close()
        return LibraryHostOperationResult(
            status = LibraryHostOperationStatus.SUCCEEDED,
            message = "Headless Danmaku library server stopped.",
            itemCount = publishedItemCount,
        )
    }

    override fun rescan(reason: String): LibraryHostOperationResult =
        publishCurrentLibrary(reason)

    override fun publishCurrentLibrary(): LibraryHostOperationResult =
        publishCurrentLibrary(reason = "manual")

    private fun publishCurrentLibrary(reason: String): LibraryHostOperationResult {
        val storedLibrary = cachedLibrary
        if (options.libraryRoots.isEmpty() && storedLibrary != null) {
            lastScanStatus = LibraryHostOperationStatus.SUCCEEDED
            return LibraryHostOperationResult(
                status = LibraryHostOperationStatus.SUCCEEDED,
                message = "Using cached headless catalog with ${publishedItemCount} items; no roots configured ($reason).",
                itemCount = publishedItemCount,
            )
        }

        lastScanStatus = LibraryHostOperationStatus.RUNNING
        return runCatching {
            HeadlessLocalLibraryScanner.scan(options.libraryRoots)
        }.fold(
            onSuccess = { scan ->
                val stored = if (scan.scannedRootCount == 0 && scan.publishedLibrary.catalog.items.isEmpty()) {
                    HeadlessStoredLibrary(
                        publishedLibrary = scan.publishedLibrary,
                        savedAtEpochMs = System.currentTimeMillis(),
                    )
                } else {
                    catalogStore.save(scan.publishedLibrary)
                }
                publishStoredLibrary(stored)
                lastScanStatus = LibraryHostOperationStatus.SUCCEEDED
                LibraryHostOperationResult(
                    status = LibraryHostOperationStatus.SUCCEEDED,
                    message = "Published $publishedItemCount items from ${scan.scannedRootCount} roots ($reason).",
                    itemCount = publishedItemCount,
                )
            },
            onFailure = { error ->
                lastScanStatus = LibraryHostOperationStatus.FAILED
                LibraryHostOperationResult(
                    status = LibraryHostOperationStatus.FAILED,
                    message = "Headless library scan failed: ${error.message}",
                    itemCount = publishedItemCount,
                )
            },
        )
    }

    private fun publishStoredLibrary(stored: HeadlessStoredLibrary) {
        server.publish(stored.publishedLibrary)
        publishedItemCount = stored.publishedLibrary.catalog.items.size
        lastPublishedAtEpochMs = stored.savedAtEpochMs
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            discoveryAnnouncer?.close()
            server.close()
            lockHandle.close()
        }
    }

    private fun createServer(): LocalLibraryServer =
        if (options.pairingToken == null) {
            LocalLibraryServer(
                port = options.port,
                progressStore = progressStore,
                webAssets = options.webAssetsRoot?.let(::StaticWebAssets),
                hostMode = LanLibraryServerStatus.HOST_MODE_HEADLESS_SERVER,
            )
        } else {
            LocalLibraryServer(
                port = options.port,
                pairingToken = options.pairingToken,
                progressStore = progressStore,
                webAssets = options.webAssetsRoot?.let(::StaticWebAssets),
                hostMode = LanLibraryServerStatus.HOST_MODE_HEADLESS_SERVER,
            )
        }
}

private class DataDirectoryLock private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }

    companion object {
        fun acquire(dataDirectory: Path): DataDirectoryLock {
            Files.createDirectories(dataDirectory)
            val lockFile = dataDirectory.resolve(".danmaku-host.lock")
            val channel = FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            )
            val lock = channel.tryLock()
                ?: error("Danmaku data directory is already locked by another host: $dataDirectory")
            return DataDirectoryLock(channel, lock)
        }
    }
}
