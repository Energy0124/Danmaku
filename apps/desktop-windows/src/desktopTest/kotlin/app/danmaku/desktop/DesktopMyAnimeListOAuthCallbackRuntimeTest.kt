package app.danmaku.desktop

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopMyAnimeListOAuthCallbackRuntimeTest {
    @Test
    fun completesAuthorizationOnDedicatedLoopbackCallback() {
        val temp = createTempDirectory("danmaku-mal-oauth-callback")
        DesktopLibraryCatalogStore(temp.resolve("catalog.db")).use { catalogStore ->
            val credentialStore = ExternalAnimeCredentialStore(
                store = catalogStore,
                secretProtector = CallbackTestSecretProtector,
            )
            credentialStore.saveSettings(
                myAnimeListClientId = "mal-client",
                myAnimeListClientSecret = "mal-secret",
                myAnimeListAccessToken = null,
                bangumiBaseUrl = DEFAULT_BANGUMI_BASE_URL,
                bangumiUserAgent = DEFAULT_BANGUMI_USER_AGENT,
                bangumiAccessToken = null,
            )
            val service = MyAnimeListOAuthService(
                credentialStore = credentialStore,
                tokenClient = MyAnimeListOAuthTokenClient { _, _, _, _, _ ->
                    MyAnimeListOAuthToken(
                        accessToken = "oauth-access",
                        refreshToken = "oauth-refresh",
                        expiresInSeconds = 7_200,
                    )
                },
                randomBytes = { size -> ByteArray(size) { index -> (index + size).toByte() } },
            )
            var updatedSettings: ExternalAnimeProviderSettings? = null
            val diagnostics = mutableListOf<String>()
            DesktopMyAnimeListOAuthCallbackRuntime.start(
                oauthService = service,
                onSettingsUpdated = { updatedSettings = it },
                onDiagnostic = diagnostics::add,
            ).use { runtime ->
                val redirectUri = "${runtime.baseUrl}${MY_ANIME_LIST_OAUTH_CALLBACK_PATH}"
                val authorizationUri = service.beginAuthorization(redirectUri)
                val state = authorizationUri.queryParameter("state")

                val body = URI(
                    "${runtime.baseUrl}${MY_ANIME_LIST_OAUTH_CALLBACK_PATH}" +
                        "?code=authorization-code&state=$state",
                ).toURL().readText()

                assertTrue(body.contains("MyAnimeList connected"))
                assertTrue(assertNotNull(updatedSettings).hasMyAnimeListAccessToken)
                assertEquals(
                    listOf("MyAnimeList OAuth authorization complete"),
                    diagnostics,
                )
                assertEquals("oauth-access", credentialStore.loadMyAnimeListOAuthTokens()?.accessToken)
            }
        }
    }
}

private object CallbackTestSecretProtector : DesktopSecretProtector {
    override fun protect(value: ByteArray): ByteArray =
        value.reversedArray()

    override fun unprotect(value: ByteArray): ByteArray =
        value.reversedArray()
}

private fun URI.queryParameter(name: String): String =
    rawQuery
        .split('&')
        .map { it.split('=', limit = 2) }
        .first { it.first() == name }
        .getOrElse(1) { "" }
