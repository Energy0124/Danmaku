package app.danmaku.library.android

import app.danmaku.domain.ExternalAnimeId
import app.danmaku.domain.ExternalAnimeListEntry
import app.danmaku.domain.ExternalAnimeMapping
import app.danmaku.domain.ExternalAnimeProvider
import app.danmaku.domain.ExternalAnimeTrackingUpdate
import java.net.HttpURLConnection
import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class ProviderAccountState {
    CONNECTED,
    DISCONNECTED,
    NEEDS_RECONNECT,
    UNAVAILABLE,
}

@Serializable
data class ProviderAccountStatus(
    val state: ProviderAccountState,
    val userId: String? = null,
    val displayName: String? = null,
    val lastVerifiedAtEpochMs: Long? = null,
    val reasonCode: String? = null,
)

@Serializable
data class ProviderAccountsDocument(
    val myAnimeList: ProviderAccountStatus,
    val bangumi: ProviderAccountStatus,
    val bangumiTokenUrl: String,
)

@Serializable
data class ExternalTrackingSeries(
    val id: String,
    val title: String,
    val localSeriesIds: List<String>,
    val localSeriesTitles: List<String>,
    val episodeCount: Int,
    val mappings: List<ExternalAnimeMapping>,
)

@Serializable
data class ExternalTrackingPlanSummary(
    val updateCount: Int,
    val skippedCount: Int,
    val conflictCount: Int,
    val failureCount: Int,
    val myAnimeListUpdateCount: Int,
    val bangumiUpdateCount: Int,
)

@Serializable
data class ExternalTrackingPlanUpdate(
    val localSeriesId: String,
    val localSeriesIds: List<String>,
    val seriesTitle: String,
    val episodeCount: Int,
    val mapping: ExternalAnimeMapping,
    val update: ExternalAnimeTrackingUpdate,
)

@Serializable
data class ExternalTrackingPlanSkip(
    val localSeriesId: String,
    val localSeriesIds: List<String>,
    val seriesTitle: String? = null,
    val provider: ExternalAnimeProvider,
    val reason: String,
)

@Serializable
data class ExternalTrackingPlanConflict(
    val localSeriesId: String,
    val localSeriesIds: List<String>,
    val seriesTitle: String,
    val episodeCount: Int,
    val mapping: ExternalAnimeMapping,
    val localUpdate: ExternalAnimeTrackingUpdate,
    val externalEntry: ExternalAnimeListEntry,
    val reason: String,
)

@Serializable
data class ExternalTrackingMappingConflict(
    val localSeriesId: String,
    val localSeriesIds: List<String>,
    val seriesTitle: String,
    val provider: ExternalAnimeProvider,
    val animeIds: List<ExternalAnimeId>,
    val reason: String,
)

@Serializable
data class ExternalAnimeSyncFailure(
    val animeId: ExternalAnimeId,
    val message: String,
    val failedAtEpochMs: Long,
    val attemptCount: Int,
    val retryAfterEpochMs: Long,
)

@Serializable
data class ExternalTrackingPlan(
    val summary: ExternalTrackingPlanSummary,
    val updates: List<ExternalTrackingPlanUpdate>,
    val skipped: List<ExternalTrackingPlanSkip>,
    val conflicts: List<ExternalTrackingPlanConflict>,
    val mappingConflicts: List<ExternalTrackingMappingConflict>,
    val failures: List<ExternalAnimeSyncFailure>,
)

@Serializable
data class ExternalTrackingDocument(
    val generatedAtEpochMs: Long,
    val series: List<ExternalTrackingSeries>,
    val mappings: List<ExternalAnimeMapping>,
    val listEntries: List<ExternalAnimeListEntry>,
    val plan: ExternalTrackingPlan,
)

@Serializable
data class ExternalTrackingOperationError(
    val animeId: ExternalAnimeId,
    val message: String,
)

@Serializable
data class ExternalTrackingOperationResponse(
    val document: ExternalTrackingDocument,
    val successCount: Int,
    val conflictCount: Int,
    val missingCount: Int,
    val errors: List<ExternalTrackingOperationError>,
)

class LanExternalTrackingException(
    val statusCode: Int,
    message: String,
) : RuntimeException(message)

class LanExternalTrackingClient(
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val connectTimeoutMillis: Int = 5_000,
    private val readTimeoutMillis: Int = 30_000,
) {
    fun fetchAccounts(baseUrl: String, pairingToken: String): ProviderAccountsDocument =
        request(baseUrl, pairingToken, "/api/providers/accounts", "GET")

    fun fetchTracking(baseUrl: String, pairingToken: String): ExternalTrackingDocument =
        request(baseUrl, pairingToken, "/api/providers/tracking", "GET")

    fun refreshReadback(baseUrl: String, pairingToken: String): ExternalTrackingOperationResponse =
        request(baseUrl, pairingToken, "/api/providers/tracking/readback", "POST")

    fun sync(
        baseUrl: String,
        pairingToken: String,
        expectedUpdates: List<ExternalAnimeTrackingUpdate>,
    ): ExternalTrackingOperationResponse =
        request(
            baseUrl,
            pairingToken,
            "/api/providers/tracking/sync",
            "POST",
            json.encodeToString(ExternalTrackingSyncRequest(expectedUpdates)),
        )

    private inline fun <reified T> request(
        baseUrl: String,
        pairingToken: String,
        path: String,
        method: String,
        body: String? = null,
    ): T {
        val connection = (URI("${baseUrl.trim().trimEnd('/')}$path").toURL()
            .openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = connectTimeoutMillis
            readTimeout = readTimeoutMillis
            setRequestProperty("Authorization", "Bearer $pairingToken")
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter().use { it.write(body) }
            val status = connection.responseCode
            val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()
                ?.use { it.readText() }
                .orEmpty()
            if (status !in 200..299) {
                throw LanExternalTrackingException(
                    status,
                    responseBody.trim().ifBlank { "Library server returned HTTP $status" },
                )
            }
            json.decodeFromString(responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

@Serializable
private data class ExternalTrackingSyncRequest(
    val expectedUpdates: List<ExternalAnimeTrackingUpdate>,
)
