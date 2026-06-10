package app.danmaku.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

class MyAnimeListOAuthService(
    private val credentialStore: ExternalAnimeCredentialStore,
    private val tokenClient: MyAnimeListOAuthTokenClient = MyAnimeListOAuthTokenClient.default(),
    private val randomBytes: (Int) -> ByteArray = ::secureRandomBytes,
) {
    private val pendingSession = AtomicReference<MyAnimeListOAuthSession?>(null)

    fun beginAuthorization(
        redirectUri: String,
        clientSecret: String? = credentialStore.loadMyAnimeListClientSecret(),
    ): URI {
        val settings = credentialStore.loadSettings()
        val clientId = settings.myAnimeListClientId
            ?: error("MyAnimeList client ID is not configured")
        val session = MyAnimeListOAuthSession(
            state = randomToken(24),
            codeVerifier = randomToken(64),
            redirectUri = redirectUri,
            clientId = clientId,
            clientSecret = clientSecret,
        )
        pendingSession.set(session)
        return URI(
            "https://myanimelist.net/v1/oauth2/authorize" +
                "?response_type=code" +
                "&client_id=${clientId.urlEncode()}" +
                "&code_challenge=${session.codeVerifier.urlEncode()}" +
                "&code_challenge_method=plain" +
                "&state=${session.state.urlEncode()}" +
                "&redirect_uri=${redirectUri.urlEncode()}",
        )
    }

    fun completeAuthorization(queryParameters: Map<String, String>): ExternalAnimeProviderSettings {
        queryParameters["error"]?.let { error ->
            error("MyAnimeList authorization failed: $error")
        }
        val code = queryParameters["code"]?.takeIf(String::isNotBlank)
            ?: error("MyAnimeList callback did not include an authorization code")
        val state = queryParameters["state"]?.takeIf(String::isNotBlank)
            ?: error("MyAnimeList callback did not include state")
        val session = pendingSession.getAndSet(null)
            ?: error("No MyAnimeList authorization is pending")
        require(state == session.state) { "MyAnimeList OAuth state did not match" }

        val token = tokenClient.exchangeAuthorizationCode(
            code = code,
            codeVerifier = session.codeVerifier,
            redirectUri = session.redirectUri,
            clientId = session.clientId,
            clientSecret = session.clientSecret,
        )
        return credentialStore.saveMyAnimeListOAuthTokens(
            accessToken = token.accessToken,
            refreshToken = token.refreshToken,
            expiresInSeconds = token.expiresInSeconds,
        )
    }

    private fun randomToken(byteCount: Int): String =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(randomBytes(byteCount))
            .take(128)
}

data class MyAnimeListOAuthSession(
    val state: String,
    val codeVerifier: String,
    val redirectUri: String,
    val clientId: String,
    val clientSecret: String?,
) {
    init {
        require(state.length in 16..128) { "state must be between 16 and 128 characters" }
        require(codeVerifier.length in 43..128) { "codeVerifier must be between 43 and 128 characters" }
        require(redirectUri.startsWith("http://localhost:") || redirectUri.startsWith("http://127.0.0.1:")) {
            "redirectUri must be a local desktop callback"
        }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(clientSecret == null || clientSecret.isNotBlank()) { "clientSecret must not be blank" }
    }
}

data class MyAnimeListOAuthToken(
    val accessToken: String,
    val refreshToken: String?,
    val expiresInSeconds: Long?,
) {
    init {
        require(accessToken.isNotBlank()) { "accessToken must not be blank" }
        require(refreshToken == null || refreshToken.isNotBlank()) { "refreshToken must not be blank" }
        require(expiresInSeconds == null || expiresInSeconds > 0) { "expiresInSeconds must be positive" }
    }
}

fun interface MyAnimeListOAuthTokenClient {
    fun exchangeAuthorizationCode(
        code: String,
        codeVerifier: String,
        redirectUri: String,
        clientId: String,
        clientSecret: String?,
    ): MyAnimeListOAuthToken

    companion object {
        fun default(
            httpPost: OAuthHttpPost = OAuthHttpPost.default(),
            json: Json = Json { ignoreUnknownKeys = true },
        ): MyAnimeListOAuthTokenClient =
            MyAnimeListOAuthTokenClient { code, codeVerifier, redirectUri, clientId, clientSecret ->
                val fields = buildList {
                    add("client_id" to clientId)
                    clientSecret?.let { add("client_secret" to it) }
                    add("code" to code)
                    add("code_verifier" to codeVerifier)
                    add("grant_type" to "authorization_code")
                    add("redirect_uri" to redirectUri)
                }
                val response = httpPost.post(
                    url = URI("https://myanimelist.net/v1/oauth2/token").toURL(),
                    headers = mapOf(
                        "Accept" to "application/json",
                        "Content-Type" to "application/x-www-form-urlencoded",
                    ),
                    body = fields.formEncode(),
                )
                val root = json.parseToJsonElement(response).asObject()
                MyAnimeListOAuthToken(
                    accessToken = root.string("access_token") ?: error("MAL token response omitted access_token"),
                    refreshToken = root.string("refresh_token"),
                    expiresInSeconds = root.long("expires_in"),
                )
            }
    }
}

fun interface OAuthHttpPost {
    fun post(url: URL, headers: Map<String, String>, body: String): String

    companion object {
        fun default(): OAuthHttpPost =
            OAuthHttpPost { url, headers, body ->
                (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 10_000
                    readTimeout = 20_000
                    headers.forEach(::setRequestProperty)
                    outputStream.bufferedWriter().use { it.write(body) }
                }.readOAuthResponse()
            }
    }
}

private fun HttpURLConnection.readOAuthResponse(): String =
    try {
        val status = responseCode
        val responseBody = (if (status >= HttpURLConnection.HTTP_BAD_REQUEST) errorStream else inputStream)
            ?.use { input ->
                input.readNBytes(1_000_001)
                    .also { check(it.size <= 1_000_000) }
                    .toString(Charsets.UTF_8)
            }
            .orEmpty()
        check(status in 200..299) {
            "MyAnimeList OAuth token request failed with HTTP $status: ${responseBody.take(200)}"
        }
        responseBody
    } finally {
        disconnect()
    }

private fun List<Pair<String, String>>.formEncode(): String =
    joinToString("&") { (key, value) -> "${key.urlEncode()}=${value.urlEncode()}" }

private fun String.urlEncode(): String =
    URLEncoder.encode(this, Charsets.UTF_8)

private fun JsonObject.string(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull

private fun JsonObject.long(key: String): Long? =
    (get(key) as? JsonPrimitive)?.longOrNull

private fun kotlinx.serialization.json.JsonElement.asObject(): JsonObject =
    this as? JsonObject ?: JsonObject(emptyMap())

private fun secureRandomBytes(size: Int): ByteArray =
    ByteArray(size).also { SecureRandom().nextBytes(it) }
