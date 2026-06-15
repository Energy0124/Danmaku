package app.danmaku.desktop

import java.net.URI
import java.net.URLDecoder
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MyAnimeListOAuthTest {
    @Test
    fun startsAuthorizationAndPersistsExchangedTokens() {
        val temp = createTempDirectory("danmaku-mal-oauth")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val credentialStore = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
                nowEpochMs = { 2_000 },
            )
            credentialStore.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListClientSecret = "mal-secret",
                myAnimeListAccessToken = null,
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = null,
            )
            var capturedCode: String? = null
            var capturedCodeVerifier: String? = null
            var capturedRedirectUri: String? = null
            var capturedClientId: String? = null
            var capturedClientSecret: String? = null
            val service = MyAnimeListOAuthService(
                credentialStore = credentialStore,
                tokenClient = MyAnimeListOAuthTokenClient { code, codeVerifier, redirectUri, clientId, clientSecret ->
                    capturedCode = code
                    capturedCodeVerifier = codeVerifier
                    capturedRedirectUri = redirectUri
                    capturedClientId = clientId
                    capturedClientSecret = clientSecret
                    MyAnimeListOAuthToken(
                        accessToken = "oauth-access",
                        refreshToken = "oauth-refresh",
                        expiresInSeconds = 7_200,
                    )
                },
                randomBytes = { size -> ByteArray(size) { index -> (index + size).toByte() } },
            )

            val redirectUri = "http://127.0.0.1:8686/oauth/myanimelist/callback"
            val authorizationUri = service.beginAuthorization(redirectUri = redirectUri)
            val query = authorizationUri.queryParameters()

            assertEquals("https", authorizationUri.scheme)
            assertEquals("myanimelist.net", authorizationUri.host)
            assertEquals("/v1/oauth2/authorize", authorizationUri.path)
            assertEquals("code", query["response_type"])
            assertEquals("mal-client", query["client_id"])
            assertEquals("plain", query["code_challenge_method"])
            assertEquals(redirectUri, query["redirect_uri"])
            assertTrue(query.getValue("state").length in 16..128)
            assertTrue(query.getValue("code_challenge").length in 43..128)

            val updatedSettings = service.completeAuthorization(
                mapOf("code" to "authorization-code", "state" to query.getValue("state")),
            )
            val tokens = credentialStore.loadMyAnimeListOAuthTokens()

            assertTrue(updatedSettings.hasMyAnimeListAccessToken)
            assertEquals("authorization-code", capturedCode)
            assertEquals(query.getValue("code_challenge"), capturedCodeVerifier)
            assertEquals(redirectUri, capturedRedirectUri)
            assertEquals("mal-client", capturedClientId)
            assertEquals("mal-secret", capturedClientSecret)
            assertNotNull(tokens)
            assertEquals("oauth-access", tokens.accessToken)
            assertEquals("oauth-refresh", tokens.refreshToken)
            assertEquals(7_202_000, tokens.expiresAtEpochMs)
        }
    }

    @Test
    fun rejectsMismatchedAuthorizationState() {
        val temp = createTempDirectory("danmaku-mal-oauth-state")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val credentialStore = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
            )
            credentialStore.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListClientSecret = null,
                myAnimeListAccessToken = null,
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = null,
            )
            val service = MyAnimeListOAuthService(
                credentialStore = credentialStore,
                tokenClient = MyAnimeListOAuthTokenClient { _, _, _, _, _ ->
                    error("token exchange should not run when state mismatches")
                },
                randomBytes = { size -> ByteArray(size) { index -> index.toByte() } },
            )
            service.beginAuthorization("http://localhost:8686/oauth/myanimelist/callback")

            val error = assertFailsWith<MyAnimeListOAuthException> {
                service.completeAuthorization(mapOf("code" to "authorization-code", "state" to "wrong-state"))
            }
            assertEquals("MyAnimeList OAuth state did not match", error.message)
        }
    }

    @Test
    fun reportsCallbackErrorsAsOAuthFailures() {
        val temp = createTempDirectory("danmaku-mal-oauth-callback")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val credentialStore = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = ReversingSecretProtector,
            )
            credentialStore.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListClientSecret = null,
                myAnimeListAccessToken = null,
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = null,
            )
            val service = MyAnimeListOAuthService(
                credentialStore = credentialStore,
                randomBytes = { size -> ByteArray(size) { index -> index.toByte() } },
            )
            service.beginAuthorization("http://localhost:8686/oauth/myanimelist/callback")

            val error = assertFailsWith<MyAnimeListOAuthException> {
                service.completeAuthorization(mapOf("error" to "access_denied"))
            }

            assertEquals("MyAnimeList authorization failed: access_denied", error.message)
        }
    }

    @Test
    fun tokenClientPostsAuthorizationCodeFormAndParsesResponse() {
        var capturedBody = ""
        val client = MyAnimeListOAuthTokenClient.default(
            httpPost = OAuthHttpPost { url, headers, body ->
                assertEquals("https://myanimelist.net/v1/oauth2/token", url.toString())
                assertEquals("application/json", headers["Accept"])
                assertEquals("application/x-www-form-urlencoded", headers["Content-Type"])
                capturedBody = body
                """
                {
                  "access_token": "access-token",
                  "refresh_token": "refresh-token",
                  "expires_in": 2678400
                }
                """.trimIndent()
            },
        )

        val token = client.exchangeAuthorizationCode(
            code = "code value",
            codeVerifier = "verifier",
            redirectUri = "http://127.0.0.1:8686/oauth/myanimelist/callback",
            clientId = "client-id",
            clientSecret = "client-secret",
        )

        assertTrue(capturedBody.contains("client_id=client-id"))
        assertTrue(capturedBody.contains("client_secret=client-secret"))
        assertTrue(capturedBody.contains("code=code+value"))
        assertTrue(capturedBody.contains("code_verifier=verifier"))
        assertTrue(capturedBody.contains("grant_type=authorization_code"))
        assertTrue(capturedBody.contains("redirect_uri=http%3A%2F%2F127.0.0.1%3A8686%2Foauth%2Fmyanimelist%2Fcallback"))
        assertEquals("access-token", token.accessToken)
        assertEquals("refresh-token", token.refreshToken)
        assertEquals(2_678_400, token.expiresInSeconds)
    }

    @Test
    fun tokenClientReportsMissingAccessTokenAsOAuthFailure() {
        val client = MyAnimeListOAuthTokenClient.default(
            httpPost = OAuthHttpPost { _, _, _ -> """{"refresh_token":"refresh-token"}""" },
        )

        val error = assertFailsWith<MyAnimeListOAuthException> {
            client.exchangeAuthorizationCode(
                code = "code",
                codeVerifier = "verifier",
                redirectUri = "http://127.0.0.1:8686/oauth/myanimelist/callback",
                clientId = "client-id",
                clientSecret = null,
            )
        }

        assertEquals("MAL token response omitted access_token", error.message)
    }

    private fun URI.queryParameters(): Map<String, String> =
        rawQuery
            .split('&')
            .associate { parameter ->
                val parts = parameter.split('=', limit = 2)
                URLDecoder.decode(parts[0], Charsets.UTF_8) to
                    URLDecoder.decode(parts.getOrElse(1) { "" }, Charsets.UTF_8)
            }

    private object ReversingSecretProtector : DesktopSecretProtector {
        override fun protect(value: ByteArray): ByteArray =
            value.reversedArray()

        override fun unprotect(value: ByteArray): ByteArray =
            value.reversedArray()
    }
}
