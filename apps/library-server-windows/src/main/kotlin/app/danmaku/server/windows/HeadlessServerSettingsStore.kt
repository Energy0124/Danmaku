package app.danmaku.server.windows

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.SecureRandom

internal class HeadlessServerSettingsStore(
    private val file: Path,
    private val random: SecureRandom = SecureRandom(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    },
) {
    @Synchronized
    fun loadOrCreate(explicitPairingToken: String?): HeadlessServerSettings {
        val loaded = load()
        val token = explicitPairingToken
            ?: loaded?.pairingToken
            ?: random.nextPairingToken()
        val settings = HeadlessServerSettings(
            pairingToken = token,
            libraryRoots = loaded?.libraryRoots.orEmpty(),
            dandanplay = loaded?.dandanplay ?: HeadlessDandanplayProviderSettings(),
            externalAnime = loaded?.externalAnime ?: HeadlessExternalAnimeProviderSettings(),
        )
        writeSnapshot(settings)
        return settings
    }

    private fun load(): HeadlessServerSettings? {
        if (!Files.isRegularFile(file)) return null
        return runCatching {
            val root = json.parseToJsonElement(Files.readString(file)).jsonObject
            val schemaVersion = root["schemaVersion"]?.jsonPrimitive?.intOrNull
                ?: return@runCatching null
            if (schemaVersion != SCHEMA_VERSION) {
                return@runCatching null
            }
            val token = root.stringOrNull("pairingToken")
                ?: return@runCatching null
            HeadlessServerSettings(
                pairingToken = token,
                libraryRoots = root["libraryRoots"]
                    ?.jsonArray
                    ?.mapNotNull { element ->
                        element.jsonPrimitive.contentOrNull
                            ?.takeIf(String::isNotBlank)
                            ?.let(Path::of)
                    }
                    .orEmpty(),
                dandanplay = root["dandanplay"].asObjectOrNull()?.toDandanplaySettings()
                    ?: HeadlessDandanplayProviderSettings(),
                externalAnime = root["externalAnime"].asObjectOrNull()?.toExternalAnimeSettings()
                    ?: HeadlessExternalAnimeProviderSettings(),
            )
        }.getOrNull()
    }

    private fun writeSnapshot(settings: HeadlessServerSettings) {
        val parent = file.parent
        if (parent != null) {
            Files.createDirectories(parent)
        }
        val temp = file.resolveSibling("${file.fileName}.tmp")
        Files.writeString(
            temp,
            json.encodeToString(JsonObject.serializer(), settings.toJsonObject()),
        )
        runCatching {
            Files.move(
                temp,
                file,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }.getOrElse { error ->
            if (error is AtomicMoveNotSupportedException) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING)
            } else {
                throw error
            }
        }
    }

    private fun HeadlessServerSettings.toJsonObject(): JsonObject =
        buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("pairingToken", pairingToken)
            put(
                "libraryRoots",
                buildJsonArray {
                    libraryRoots.forEach { root -> add(root.toString()) }
                },
            )
            put("dandanplay", dandanplay.toJsonObject())
            put("externalAnime", externalAnime.toJsonObject())
        }

    private fun HeadlessDandanplayProviderSettings.toJsonObject(): JsonObject =
        buildJsonObject {
            put("baseUrl", baseUrl)
            appId?.let { put("appId", it) }
            put("hasAppSecret", hasAppSecret)
            put("authenticationMode", authenticationMode.name)
            put("cacheMaxAgeDays", cacheMaxAgeDays)
        }

    private fun HeadlessExternalAnimeProviderSettings.toJsonObject(): JsonObject =
        buildJsonObject {
            myAnimeListClientId?.let { put("myAnimeListClientId", it) }
            put("hasMyAnimeListClientSecret", hasMyAnimeListClientSecret)
            put("hasMyAnimeListAccessToken", hasMyAnimeListAccessToken)
            put("bangumiBaseUrl", bangumiBaseUrl)
            put("bangumiUserAgent", bangumiUserAgent)
            put("hasBangumiAccessToken", hasBangumiAccessToken)
        }

    private fun JsonElement?.asObjectOrNull(): JsonObject? =
        this as? JsonObject

    private fun JsonObject.toDandanplaySettings(): HeadlessDandanplayProviderSettings {
        val appSecret = stringOrNull("appSecret")
        return HeadlessDandanplayProviderSettings(
            baseUrl = stringOrNull("baseUrl") ?: DEFAULT_DANDANPLAY_BASE_URL,
            appId = stringOrNull("appId"),
            appSecret = appSecret,
            hasAppSecret = appSecret != null || booleanOrNull("hasAppSecret") == true,
            authenticationMode = stringOrNull("authenticationMode")
                ?.let(::headlessDandanplayAuthenticationModeOrDefault)
                ?: HeadlessDandanplayAuthenticationMode.SIGNED,
            cacheMaxAgeDays = intOrNull("cacheMaxAgeDays")
                ?.takeIf { it >= MIN_DANDANPLAY_CACHE_MAX_AGE_DAYS }
                ?: DEFAULT_DANDANPLAY_CACHE_MAX_AGE_DAYS,
        )
    }

    private fun JsonObject.toExternalAnimeSettings(): HeadlessExternalAnimeProviderSettings =
        HeadlessExternalAnimeProviderSettings(
            myAnimeListClientId = stringOrNull("myAnimeListClientId"),
            hasMyAnimeListClientSecret = booleanOrNull("hasMyAnimeListClientSecret") ?: false,
            hasMyAnimeListAccessToken = booleanOrNull("hasMyAnimeListAccessToken") ?: false,
            bangumiBaseUrl = stringOrNull("bangumiBaseUrl") ?: DEFAULT_BANGUMI_BASE_URL,
            bangumiUserAgent = stringOrNull("bangumiUserAgent") ?: DEFAULT_BANGUMI_USER_AGENT,
            hasBangumiAccessToken = booleanOrNull("hasBangumiAccessToken") ?: false,
        )

    private fun JsonObject.stringOrNull(name: String): String? =
        runCatching {
            get(name)
                ?.jsonPrimitive
                ?.contentOrNull
                ?.takeIf(String::isNotBlank)
        }.getOrNull()

    private fun JsonObject.intOrNull(name: String): Int? =
        runCatching { get(name)?.jsonPrimitive?.intOrNull }.getOrNull()

    private fun JsonObject.booleanOrNull(name: String): Boolean? =
        runCatching { get(name)?.jsonPrimitive?.booleanOrNull }.getOrNull()

    private fun SecureRandom.nextPairingToken(): String =
        nextInt(1_000_000).toString().padStart(6, '0')

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

internal data class HeadlessServerSettings(
    val pairingToken: String,
    val libraryRoots: List<Path> = emptyList(),
    val dandanplay: HeadlessDandanplayProviderSettings = HeadlessDandanplayProviderSettings(),
    val externalAnime: HeadlessExternalAnimeProviderSettings = HeadlessExternalAnimeProviderSettings(),
) {
    init {
        require(pairingToken.isNotBlank()) { "pairingToken must not be blank" }
        require(libraryRoots.none { it.toString().isBlank() }) { "library roots must not contain blank paths" }
    }
}

internal data class HeadlessDandanplayProviderSettings(
    val baseUrl: String = DEFAULT_DANDANPLAY_BASE_URL,
    val appId: String? = null,
    val appSecret: String? = null,
    val hasAppSecret: Boolean = appSecret != null,
    val authenticationMode: HeadlessDandanplayAuthenticationMode = HeadlessDandanplayAuthenticationMode.SIGNED,
    val cacheMaxAgeDays: Int = DEFAULT_DANDANPLAY_CACHE_MAX_AGE_DAYS,
) {
    init {
        require(baseUrl.isHttpBaseUrl()) { "dandanplay base URL must be HTTP(S)" }
        require(appId == null || appId.isNotBlank()) { "dandanplay appId must not be blank" }
        require(appSecret == null || appSecret.isNotBlank()) { "dandanplay appSecret must not be blank" }
        require(cacheMaxAgeDays >= MIN_DANDANPLAY_CACHE_MAX_AGE_DAYS) {
            "dandanplay cache max age must be at least $MIN_DANDANPLAY_CACHE_MAX_AGE_DAYS day"
        }
    }

    val hasCredentials: Boolean
        get() = appId != null && appSecret != null

    val isFetchEnabled: Boolean
        get() = hasCredentials || baseUrl != DEFAULT_DANDANPLAY_BASE_URL
}

internal enum class HeadlessDandanplayAuthenticationMode {
    SIGNED,
    CREDENTIAL,
}

internal data class HeadlessExternalAnimeProviderSettings(
    val myAnimeListClientId: String? = null,
    val hasMyAnimeListClientSecret: Boolean = false,
    val hasMyAnimeListAccessToken: Boolean = false,
    val bangumiBaseUrl: String = DEFAULT_BANGUMI_BASE_URL,
    val bangumiUserAgent: String = DEFAULT_BANGUMI_USER_AGENT,
    val hasBangumiAccessToken: Boolean = false,
) {
    init {
        require(myAnimeListClientId == null || myAnimeListClientId.isNotBlank()) {
            "myAnimeListClientId must not be blank"
        }
        require(bangumiBaseUrl.isHttpsBaseUrl()) { "Bangumi base URL must be HTTPS" }
        require(bangumiUserAgent.isNotBlank()) { "Bangumi user agent must not be blank" }
    }
}

private fun headlessDandanplayAuthenticationModeOrDefault(value: String): HeadlessDandanplayAuthenticationMode =
    runCatching { HeadlessDandanplayAuthenticationMode.valueOf(value.uppercase()) }
        .getOrDefault(HeadlessDandanplayAuthenticationMode.SIGNED)

private fun String.isHttpBaseUrl(): Boolean =
    runCatching {
        val uri = URI(trim())
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

private fun String.isHttpsBaseUrl(): Boolean =
    runCatching {
        val uri = URI(trim())
        uri.scheme == "https" && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

private const val DEFAULT_DANDANPLAY_BASE_URL = "https://api.dandanplay.net"
private const val DEFAULT_DANDANPLAY_CACHE_MAX_AGE_DAYS = 30
private const val MIN_DANDANPLAY_CACHE_MAX_AGE_DAYS = 1
private const val DEFAULT_BANGUMI_BASE_URL = "https://api.bgm.tv/"
private const val DEFAULT_BANGUMI_USER_AGENT = "Danmaku/0.1 (https://github.com/Energy0124/Danmaku)"
