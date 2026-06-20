package app.danmaku.host

data class LibraryHostConfig(
    val dataDirectory: String,
    val libraryRoots: List<String> = emptyList(),
    val port: Int = DEFAULT_PORT,
    val pairingToken: String? = null,
    val webUiEnabled: Boolean = false,
    val mode: LibraryHostMode = LibraryHostMode.EMBEDDED_DESKTOP,
) {
    init {
        require(dataDirectory.isNotBlank()) { "dataDirectory must not be blank" }
        require(port in 0..65_535) { "port must be between 0 and 65535" }
        require(libraryRoots.none { it.isBlank() }) { "libraryRoots must not contain blank paths" }
        require(pairingToken == null || pairingToken.isNotBlank()) { "pairingToken must not be blank" }
    }

    companion object {
        const val DEFAULT_PORT = 8686
    }
}

enum class LibraryHostMode(
    val wireName: String,
) {
    EMBEDDED_DESKTOP("embedded-desktop"),
    HEADLESS_SERVER("headless-server"),
}

data class LibraryHostRuntimeStatus(
    val mode: LibraryHostMode,
    val baseUrls: List<String>,
    val webUiUrls: List<String> = emptyList(),
    val itemCount: Int = 0,
    val startedAtEpochMs: Long? = null,
    val lastPublishedAtEpochMs: Long? = null,
    val lastScanStatus: LibraryHostOperationStatus = LibraryHostOperationStatus.IDLE,
    val pairingRequired: Boolean = true,
) {
    init {
        require(baseUrls.none { it.isBlank() }) { "baseUrls must not contain blank URLs" }
        require(webUiUrls.none { it.isBlank() }) { "webUiUrls must not contain blank URLs" }
        require(itemCount >= 0) { "itemCount must not be negative" }
        startedAtEpochMs?.let { require(it >= 0) { "startedAtEpochMs must not be negative" } }
        lastPublishedAtEpochMs?.let { require(it >= 0) { "lastPublishedAtEpochMs must not be negative" } }
    }
}

enum class LibraryHostOperationStatus {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
}

data class LibraryHostOperationResult(
    val status: LibraryHostOperationStatus,
    val message: String,
    val itemCount: Int? = null,
) {
    init {
        require(message.isNotBlank()) { "message must not be blank" }
        itemCount?.let { require(it >= 0) { "itemCount must not be negative" } }
    }
}

interface LibraryHostService {
    val runtimeStatus: LibraryHostRuntimeStatus

    fun start(): LibraryHostOperationResult

    fun stop(): LibraryHostOperationResult

    fun rescan(reason: String = "manual"): LibraryHostOperationResult

    fun publishCurrentLibrary(): LibraryHostOperationResult
}
